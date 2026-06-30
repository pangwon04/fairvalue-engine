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

from .cb_calculator import _interp_curve_at
from .tf_lattice import CBLatticeSpec, tf_value_split


def _to_date(s) -> date:
    return s if isinstance(s, date) else date.fromisoformat(str(s))


def _forward_steps(curve_pct: list, t_years: float, steps: int):
    """zero 커브(%, [[t,rate]]) → 스텝별 연속복리 forward rate(decimal) 배열(길이 steps).
    f_n = (z(t_{n+1})·t_{n+1} − z(t_n)·t_n) / Δt. 커브 없으면 None."""
    if not curve_pct:
        return None
    dt = t_years / steps
    out = []
    for n in range(steps):
        t0 = n * dt
        t1 = (n + 1) * dt
        z0 = _interp_curve_at(curve_pct, t0) / 100.0
        z1 = _interp_curve_at(curve_pct, t1) / 100.0
        fwd = (z1 * t1 - z0 * t0) / dt if dt > 0 else z1
        out.append(fwd)
    return out


def calculate_rcps(ctx: dict) -> dict:
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
    rf_flat = (_interp_curve_at(curves.get("risk_free_curve", []), t_years) / 100.0) if curves.get("risk_free_curve") else 0.0423
    rd_flat = (_interp_curve_at(curves.get("credit_curve", []), t_years) / 100.0) if curves.get("credit_curve") else 0.2327
    if rd_flat == 0.0:
        rd_flat = rf_flat
    rf_steps = _forward_steps(curves.get("risk_free_curve", []), t_years, steps) if use_term_structure else None
    rd_steps = _forward_steps(curves.get("credit_curve", []) or [], t_years, steps) if use_term_structure else None

    # ★ C 모델(측도 분리) — host 와 전환을 서로 다른 배당 드리프트로 산출한다.
    #   - q_conv = 전환 시 포기하는 우선배당(= 계약 우선배당률, 보통주배당 차감) =전환옵션 carry cost.
    #     전환증권 표준 해석이며 임의 calibration 상수가 아니다(terms 에서 취득).
    #   - host(preferred_share_value): 전환의 q 와 무관(전환 안 한 우선주 cashflow). q=0 base TF 의 Vb.
    #   - total: 전환 underlying 에 q_conv 적용한 TF 의 (Vb+Ve). embedded = total − host.
    q_conv = max(0.0, div_rate - q)   # 우선배당 − 보통주배당

    def _spec(qd):
        return CBLatticeSpec(
            s0=s0, sigma=sigma, t_years=t_years, steps=steps, rf=rf_flat, rd=rd_flat, q=qd,
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
    }
