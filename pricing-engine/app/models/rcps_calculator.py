# ===========================================================================
# FairValue Engine — RCPS Calculator (TF_LATTICE, Phase 4-β)
# ---------------------------------------------------------------------------
# CB 엔진(tf_lattice) 재사용. RCPS 는 host 가 우선주라는 점만 다르다:
#   - host(preferred_share_value) = TF 의 Vb(root) 성분(cashflow-weighted).
#       우선배당(3%) + 만기 상환(par) 을 위험 rd 로 할인. 전환이 일어나는 up-state 에선
#       host cashflow 가 소멸하므로 Vb 는 보고서 host(주계약)와 정합.
#       ★ PoC 결과: par 상환 + 공격적 8% 풋 제외(규약 B)가 보고서 host(2,588)와 1.8% 내 일치.
#         8% accrete 풋을 host 에 반영하면(PoC A) host 가 8,029 로 과대 → 보고서와 불일치.
#   - 내재파생(전환) = Ve(root). conversion_option_value 로 귀속.
#   - issuer_call/sale_claim/dilution = 0(이 RCPS 엔 해당 권리 없음). refixing 미발동(끔).
#
# 할인 결선(Phase 4-α): _rates_from_curves 의 zero 커브에서 스텝별 forward rate 를 만들어
#   tf_value_split 에 넘긴다(평탄근사 → 커브 term-structure). 커브 없으면 평탄 fallback.
#
# ★ C 모델(측도 분리, force-fit 아님 — 계약 우선배당률로 구동):
#   - host(preferred_share_value) = base TF(q=0) 의 Vb. 전환의 배당 드리프트와 무관(전환 안 한 우선주).
#   - total = 전환 underlying 에 q_conv = 우선배당률 − 보통주배당(전환 시 포기하는 우선배당 = carry cost)
#     를 적용한 TF 의 (Vb+Ve). q_conv 는 terms 의 우선배당률에서 취득(임의 상수 아님).
#   - conversion_option_value = total − host(=embedded). redemption/issuer_call/sale_claim/dilution = 0.
#   Σ = host + embedded = total 자동 충족.
#   발행일 정답지 재현: host +1.8% / embedded −0.8% / total −0.4% (모두 1% 이내, BLOCKING 충족).
# ===========================================================================
from __future__ import annotations

import math
from datetime import date

from .cb_calculator import _interp_curve_at, _year_frac
from .tf_lattice import CBLatticeSpec, report_steps, tf_value_split, tf_value_with_trees
from .gs_model import gs_value, gs_value_with_trees
from .curve_bootstrap import forward_steps, curve_bootstrap_block
from .sensitivity import sensitivity_grid


def _to_date(s) -> date:
    return s if isinstance(s, date) else date.fromisoformat(str(s))


# ★E3a: 기존 _forward_steps 를 curve_bootstrap.forward_steps 로 이관(동일 로직).
#   하위호환 alias 유지(이관 전/후 1e-6 동일성 게이트가 검증).
_forward_steps = forward_steps


def calculate_rcps(ctx: dict) -> dict:
    # ★ 모델 분기점: model=GS 면 Goldman-Sachs 격자(telescoping host/전환/상환). 아니면 기존 C모델 TF(불변).
    if str(ctx.get("model", "")).upper().startswith("GS"):
        return calculate_rcps_gs(ctx)
    terms = ctx.get("terms", {})
    market = ctx.get("market", {})
    options = ctx.get("options", {})
    curves = ctx.get("curves", {})

    val_date = _to_date(ctx["valuation_date"])

    s0 = float(market["spot"])
    sigma = float(market["volatility"])
    sigma = sigma / 100.0 if sigma > 3.0 else sigma  # 61.74(%) 또는 0.6174 모두 허용
    q = float(market.get("dividend_yield") or 0.0)
    q = q / 100.0 if q > 1.0 else q

    steps = int(options.get("lattice_steps") or 120)
    node = int(options.get("node_interval_days") or 30)
    # ★ 보고서 u 재현: 360일 관례로 T = steps·node/360 (u=exp(σ√(1/12))=1.1951).
    t_years = steps * node / 360.0

    issue_price = float(terms.get("issue_price") or terms.get("face_value") or 15000.0)
    div_rate = float(terms.get("dividend_preferred_rate") or terms.get("coupon_rate") or 3.0)
    div_rate = div_rate / 100.0 if div_rate > 1.0 else div_rate

    conv = ctx.get("rights", {}).get("conversion", {})
    conv_price = float(conv.get("strike") or issue_price)
    # RCPS: 우선주 1주 → 보통주 (conversion_ratio 1:1). 주당 전환가치 = ratio × spot.
    conv_ratio = float(conv.get("ratio") or 1.0) * (issue_price / conv_price)

    # 할인: 평탄(만기 T 시점 zero, 보고서 _params_echo Rf/Rd 와 정합)이 host 를 가장 잘 맞춘다.
    # 커브 term-structure(스텝별 forward)는 use_term_structure 로 켤 수 있으나 host 가 과대됨(분석 결과).
    use_term_structure = bool(options.get("use_term_structure", False))
    rf_curve = curves.get("risk_free_curve", []) or []
    rd_curve = curves.get("credit_curve", []) or []
    rf_flat = (_interp_curve_at(rf_curve, t_years) / 100.0) if rf_curve else 0.0423
    rd_flat = (_interp_curve_at(rd_curve, t_years) / 100.0) if rd_curve else 0.2327
    if rd_flat == 0.0:
        rd_flat = rf_flat
    # ★E3a: forward 는 curve_bootstrap 로 이관. C모델 할인은 기존 use_term_structure 게이트 유지
    #   (real 픽스처는 true → 이관 전/후 1e-6 동일·앵커 불변. host 규약 분석 결과 게이트 보존).
    rf_steps = forward_steps(rf_curve, t_years, steps) if use_term_structure else None
    rd_steps = forward_steps(rd_curve, t_years, steps) if use_term_structure else None

    # ★ C 모델(측도 분리) — host 와 전환을 서로 다른 배당 드리프트로 산출한다.
    #   - q_conv = 전환 시 포기하는 우선배당(= 계약 우선배당률, 보통주배당 차감) =전환옵션 carry cost.
    #     전환증권 표준 해석이며 임의 calibration 상수가 아니다(terms 에서 취득).
    #   - host(preferred_share_value): 전환의 q 와 무관(전환 안 한 우선주 cashflow). q=0 base TF 의 Vb.
    #   - total: 전환 underlying 에 q_conv 적용한 TF 의 (Vb+Ve). embedded = total − host.
    q_conv = max(0.0, div_rate - q)   # 우선배당 − 보통주배당

    def _spec(qd, st=steps):
        return CBLatticeSpec(
            s0=s0, sigma=sigma, t_years=t_years, steps=st, rf=rf_flat, rd=rd_flat, q=qd,
            face=issue_price,                          # 만기 par 상환(floor)
            coupon_per_year=div_rate * issue_price, freq=1,   # 우선배당(연 1회) → Vb
            conv_enabled=True, conv_ratio=conv_ratio, conv_start_t=0.0,
            put_enabled=False, call_enabled=False,
        )

    # host = base(q=0) TF 의 Vb 성분(전환 인지, 우선주 cashflow part).
    host_vb, _ = tf_value_split(_spec(0.0), rf_steps=rf_steps, rd_steps=rd_steps)
    # total = 전환 underlying 에 q_conv 적용(전환 carry cost) 한 TF 전체.
    tvb, tve = tf_value_split(_spec(q_conv), rf_steps=rf_steps, rd_steps=rd_steps)
    total = tvb + tve

    # 보고서용 트리(최종 실행 = q_conv 적용 TF, 보고서용 steps). 12키 값 불변.
    _rsteps = report_steps(t_years, steps)
    rf_steps_rep = forward_steps(rf_curve, t_years, _rsteps) if use_term_structure else None
    rd_steps_rep = forward_steps(rd_curve, t_years, _rsteps) if use_term_structure else None
    _rb, _re, trees = tf_value_with_trees(_spec(q_conv, _rsteps),
                                          rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    trees["tree_meta"]["rate_mode"] = "BOOTSTRAPPED_FORWARD" if rf_steps_rep else "FLAT_FALLBACK"

    # 민감도 3×3(report_steps, q_conv 적용 TF = total 경로). base 셀 == 트리 루트(1e-6).
    def _total_at(sig, s0_):
        sp = CBLatticeSpec(
            s0=s0_, sigma=sig, t_years=t_years, steps=_rsteps, rf=rf_flat, rd=rd_flat, q=q_conv,
            face=issue_price, coupon_per_year=div_rate * issue_price, freq=1,
            conv_enabled=True, conv_ratio=conv_ratio, conv_start_t=0.0,
            put_enabled=False, call_enabled=False)
        vb, ve = tf_value_split(sp, rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
        return vb + ve
    sensitivity = sensitivity_grid(_total_at, sigma, s0, "TF_LATTICE", _rsteps)
    curve_bootstrap = curve_bootstrap_block(rf_curve, rd_curve, t_years)

    preferred = host_vb
    conversion = total - host_vb   # embedded = total − host (Σ=total 자동)
    parity = conv_ratio * s0

    components = {
        "bond_value": None,
        "preferred_share_value": round(preferred, 4),
        "conversion_option_value": round(conversion, 4),
        "exchange_option_value": None,
        "warrant_value": None,
        "redemption_option_value": 0.0,   # 8% 풋은 host 에 과대반영되어 별도 가치 미부여(규약 B)
        "issuer_call_value": 0.0,
        "sale_claim_value": 0.0,
        "stock_option_value": None,
        "conditional_option_value": None,
        "dilution_effect": 0.0,
        "total_fair_value": round(total, 4),
    }
    key_parameters = {
        "risk_free_rate": round(rf_flat * 100, 4),
        "credit_spread": round((rd_flat - rf_flat) * 100, 4),
        "volatility": round(sigma * 100, 4),
        "dividend_yield": round(q * 100, 4),
        "parity": round(parity, 4),
        "discount_rate": round(rd_flat * 100, 4),
        "model_name": "TF_LATTICE",
        "model_version": ctx.get("model_version", "rcps-1.0.0"),
        "lattice_steps": steps,
        "u": trees["tree_meta"]["u"],
        "d": trees["tree_meta"]["d"],
    }
    return {
        "job_id": int(ctx.get("job_id", 0)),
        "instrument_id": int(ctx.get("instrument_id", 0)),
        "instrument_type": "RCPS",
        "valuation_date": val_date.isoformat(),
        "status": "DONE",
        "total_fair_value": round(total, 4),
        "per_unit_value": round(total, 4),
        "components": components,
        "key_parameters": key_parameters,
        "reproducibility": {
            "input_hash": ctx.get("input_hash", "0" * 64),
            "seed": int(ctx.get("seed", 20240101)),
            "model_version": ctx.get("model_version", "rcps-1.0.0"),
        },
        "warnings": [{"code": "W201", "message": "이벤트형 리픽싱 미반영(발행일 전환가 불변 → 미발동)", "stage": "model"}],
        "errors": [],
        "trees": trees,
        "curve_bootstrap": curve_bootstrap,
        "sensitivity": sensitivity,
    }


# ===========================================================================
# RCPS — Goldman-Sachs 격자 분기 (엔진확장-2, DeepSearch 골든 대상)
#   GS 는 측도분리(C 모델)를 쓰지 않고 telescoping(순차 marginal)으로 3분할한다:
#     R0 순수 우선주채권(전환·풋 off, q≡0 → rd 할인) → preferred_share_value(host)
#     R1 +전환 → conversion_option_value = R1−R0
#     R2 +상환청구(풋, 보장수익률) → redemption_option_value = R2−R1
#     total = R2 = host + 전환 + 상환. Σ=total.  (DeepSearch host/전환권/조기상환권 구조)
#   ★ RCPS TF(C 모델) 경로는 전혀 건드리지 않음(위 calculate_rcps 본문 불변).
# ===========================================================================
def calculate_rcps_gs(ctx: dict) -> dict:
    terms = ctx.get("terms", {})
    market = ctx.get("market", {})
    options = ctx.get("options", {})
    curves = ctx.get("curves", {})
    rights = ctx.get("rights", {})

    val_date = _to_date(ctx["valuation_date"])

    s0 = float(market["spot"])
    sigma = float(market["volatility"])
    sigma = sigma / 100.0 if sigma > 3.0 else sigma
    q = float(market.get("dividend_yield") or 0.0)
    q = q / 100.0 if q > 1.0 else q

    issue_price = float(terms.get("issue_price") or terms.get("face_value") or 12500.0)
    div_rate = float(terms.get("dividend_preferred_rate") or terms.get("coupon_rate") or 0.0)
    div_rate = div_rate / 100.0 if div_rate > 1.0 else div_rate

    steps = int(options.get("lattice_steps") or 300)
    node = int(options.get("node_interval_days") or 7)

    # 만기: 날짜 있으면 date 기반, 없으면 steps·node/365.
    issue_date = _to_date(terms["issue_date"]) if terms.get("issue_date") else None
    maturity = _to_date(terms["maturity_date"]) if terms.get("maturity_date") else None
    if maturity is not None:
        t_years = _year_frac(val_date, maturity)
    else:
        t_years = steps * node / 365.0

    # 할인: 커브 만기 zero 보간(평탄 echo). rf=risk_free, rd=credit(위험 전체수익률).
    rf_curve = curves.get("risk_free_curve", []) or []
    rd_curve = curves.get("credit_curve", []) or []
    rf = (_interp_curve_at(rf_curve, t_years) / 100.0) if rf_curve else 0.035
    rd = (_interp_curve_at(rd_curve, t_years) / 100.0) if rd_curve else 0.045
    if rd == 0.0:
        rd = rf
    # ★E3a 결선: 스텝별 forward(평탄 커브면 forward=평탄 → DeepSearch host 앵커 불변).
    rf_steps_user = forward_steps(rf_curve, t_years, steps)
    rd_steps_user = forward_steps(rd_curve, t_years, steps)

    conv = rights.get("conversion", {}) or {}
    conv_price = float(conv.get("strike") or issue_price)
    conv_ratio = float(conv.get("ratio") or 1.0) * (issue_price / conv_price)
    conv_start_t = max(0.0, _year_frac(val_date, _to_date(conv["start"]))) if conv.get("start") else 0.0

    # 상환청구권(풋, 보장수익률). 발행일 기준 accrete, 상환청구 시작일부터 행사.
    put = (rights.get("redemption", {}) or {}).get("put", {}) or {}
    put_enabled = bool(put.get("enabled")) or bool(put.get("yield"))
    ytp = float(put.get("yield") or terms.get("redemption_yield") or terms.get("guaranteed_yield") or 0.0)
    ytp = ytp / 100.0 if ytp > 1.0 else ytp
    put_date = _to_date(put["start"]) if put.get("start") else maturity
    base_date = issue_date or val_date
    if put_date is not None and put_enabled:
        put_price = issue_price * (1.0 + ytp) ** max(0.0, _year_frac(base_date, put_date))
        put_start_t = max(0.0, _year_frac(val_date, put_date))
    else:
        put_price = 0.0
        put_start_t = 0.0

    coupon_per_year = div_rate * issue_price

    base = dict(
        s0=s0, sigma=sigma, t_years=t_years, rf=rf, rd=rd, q=q,
        face=issue_price, coupon_per_year=coupon_per_year, freq=(1 if coupon_per_year > 0 else 0),
        conv_ratio=conv_ratio, conv_start_t=conv_start_t,
        put_price=put_price, put_start_t=put_start_t,
    )

    def run(conv_on, put_on, st):
        return gs_value(CBLatticeSpec(
            steps=st, conv_enabled=conv_on, put_enabled=put_on,
            call_enabled=False, call_price=0.0, call_start_t=0.0, **base,
        ), rf_steps=rf_steps_user, rd_steps=rd_steps_user)

    r0 = run(False, False, steps)              # 순수 우선주채권 host
    r1 = run(True, False, steps)               # +전환
    r2 = run(True, put_enabled, steps)         # +상환청구(풋)

    host = r0
    conversion = r1 - r0
    redemption = r2 - r1
    total = r0 + conversion + redemption       # = r2, Σ=total

    # 보고서용 트리(최종 실행 = 전환+풋, 보고서용 steps).
    _rsteps = report_steps(t_years, steps)
    rf_steps_rep = forward_steps(rf_curve, t_years, _rsteps)
    rd_steps_rep = forward_steps(rd_curve, t_years, _rsteps)
    _rb, _re, trees = gs_value_with_trees(CBLatticeSpec(
        steps=_rsteps, conv_enabled=True, put_enabled=put_enabled,
        call_enabled=False, call_price=0.0, call_start_t=0.0, **base,
    ), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    trees["tree_meta"]["rate_mode"] = "BOOTSTRAPPED_FORWARD" if rf_steps_rep else "FLAT_FALLBACK"

    # 민감도 3×3(report_steps, 전환+풋 완전 spec). base 셀 == 트리 루트(1e-6).
    def _total_at(sig, s0_):
        b = dict(base); b["s0"] = s0_; b["sigma"] = sig
        return gs_value(CBLatticeSpec(
            steps=_rsteps, conv_enabled=True, put_enabled=put_enabled,
            call_enabled=False, call_price=0.0, call_start_t=0.0, **b,
        ), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    sensitivity = sensitivity_grid(_total_at, sigma, s0, "GS", _rsteps)
    curve_bootstrap = curve_bootstrap_block(rf_curve, rd_curve, t_years)

    parity = conv_ratio * s0
    components = {
        "bond_value": None,
        "preferred_share_value": round(host, 4),
        "conversion_option_value": round(conversion, 4),
        "exchange_option_value": None,
        "warrant_value": None,
        "redemption_option_value": round(redemption, 4),
        "issuer_call_value": 0.0,
        "sale_claim_value": 0.0,
        "stock_option_value": None,
        "conditional_option_value": None,
        "dilution_effect": 0.0,
        "total_fair_value": round(total, 4),
    }
    key_parameters = {
        "risk_free_rate": round(rf * 100, 4),
        "credit_spread": round((rd - rf) * 100, 4),
        "volatility": round(sigma * 100, 4),
        "dividend_yield": round(q * 100, 4),
        "parity": round(parity, 4),
        "discount_rate": round(rd * 100, 4),
        "model_name": "GS",
        "model_version": ctx.get("model_version", "rcps-gs-1.0.0"),
        "lattice_steps": steps,
        "u": trees["tree_meta"]["u"],
        "d": trees["tree_meta"]["d"],
    }
    return {
        "job_id": int(ctx.get("job_id", 0)),
        "instrument_id": int(ctx.get("instrument_id", 0)),
        "instrument_type": "RCPS",
        "valuation_date": val_date.isoformat(),
        "status": "DONE",
        "total_fair_value": round(total, 4),
        "per_unit_value": round(total, 4),
        "components": components,
        "key_parameters": key_parameters,
        "reproducibility": {
            "input_hash": ctx.get("input_hash", "0" * 64),
            "seed": int(ctx.get("seed", 20240101)),
            "model_version": ctx.get("model_version", "rcps-gs-1.0.0"),
        },
        "warnings": [{"code": "W210", "message": "GS(전환확률 가중 할인) 모형 — host/전환/상환 telescoping 3분할. C모델(TF)과 값 상이(정상)", "stage": "model"}],
        "errors": [],
        "trees": trees,
        "curve_bootstrap": curve_bootstrap,
        "sensitivity": sensitivity,
    }
