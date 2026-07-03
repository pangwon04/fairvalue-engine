# ===========================================================================
# FairValue Engine — CPS Calculator (TF_LATTICE, Phase 4-γ)
# ---------------------------------------------------------------------------
# CPS(전환우선주) = RCPS − 상환권(풋). RCPS 의 C 모델(측도 분리)을 그대로 재사용:
#   - host(preferred_share_value) = base TF(q=0) 의 Vb(우선주 cashflow part).
#   - total = 전환 underlying 에 q_conv = 우선배당률 − 보통주배당 적용한 TF 의 (Vb+Ve).
#   - embedded(conversion_option_value) = total − host.  Σ = host + embedded = total 자동.
#   q_conv 는 terms 우선배당률에서 취득(임의 상수 아님) — RCPS 와 동일.
#
# CPS 특유(이것만 다르다):
#   1. 상환권 없음: rights.redemption 미보유. redemption_option_value=0, 풋 로직 없음.
#   2. host_type: "dated"(만기형, 만기노드 par 상환) | "perpetual"(영구형, 만기노드 div/rd).
#      perpetual 격자 근사: horizon=PERP_HORIZON_YEARS(50y) 절단, 만기 잔여=div/rd(Gordon).
#   3. component: preferred_share_value(+) + conversion_option_value(+) 만. 나머지 0.
#
# ★ 외부 실보고서 없음 → self-consistency+골든(cps_case1)+MC 교차검증+RCPS 정합성으로 보증.
#   host_type 은 PricingResult 스키마 불변 위해 key_parameters 가 아니라 warnings 에 기록.
# pure Python. 새 의존성 없음.
# ===========================================================================
from __future__ import annotations

import math
from datetime import date

from .cb_calculator import _interp_curve_at
from .tf_lattice import CBLatticeSpec, report_steps, tf_value_split, tf_value_with_trees
from .gs_model import gs_value, gs_value_with_trees
from .curve_bootstrap import forward_steps, curve_bootstrap_block
from .sensitivity import sensitivity_grid

PERP_HORIZON_YEARS = 50.0   # 영구형 격자 절단 horizon(가정)


def _to_date(s) -> date:
    return s if isinstance(s, date) else date.fromisoformat(str(s))


# ★E3a: 기존 _forward_steps 를 curve_bootstrap.forward_steps 로 이관(동일 로직). 하위호환 alias.
_forward_steps = forward_steps


def calculate_cps(ctx: dict) -> dict:
    # ★ 모델 분기점: model=GS 면 Goldman-Sachs 격자(host/전환 telescoping). 아니면 기존 C모델 TF(불변).
    if str(ctx.get("model", "")).upper().startswith("GS"):
        return calculate_cps_gs(ctx)
    terms = ctx.get("terms", {})
    market = ctx.get("market", {})
    options = ctx.get("options", {})
    curves = ctx.get("curves", {})

    val_date = _to_date(ctx["valuation_date"])

    s0 = float(market["spot"])
    sigma = float(market["volatility"])
    sigma = sigma / 100.0 if sigma > 3.0 else sigma
    q = float(market.get("dividend_yield") or 0.0)
    q = q / 100.0 if q > 1.0 else q

    issue_price = float(terms.get("issue_price") or terms.get("face_value") or 10000.0)
    div_rate = float(terms.get("dividend_preferred_rate") or terms.get("dividend_rate")
                     or terms.get("coupon_rate") or 0.0)
    div_rate = div_rate / 100.0 if div_rate > 1.0 else div_rate

    host_type = str(terms.get("host_type") or "dated").lower()

    steps = int(options.get("lattice_steps") or 120)
    node = int(options.get("node_interval_days") or 30)
    use_term_structure = bool(options.get("use_term_structure", False))

    if host_type == "perpetual":
        t_years = PERP_HORIZON_YEARS
    else:
        t_years = steps * node / 360.0

    rf_curve = curves.get("risk_free_curve", []) or []
    rd_curve = curves.get("credit_curve", []) or []
    rf_flat = (_interp_curve_at(rf_curve, t_years) / 100.0) if rf_curve else 0.03
    rd_flat = (_interp_curve_at(rd_curve, t_years) / 100.0) if rd_curve else 0.07
    if rd_flat == 0.0:
        rd_flat = rf_flat

    div_amt = div_rate * issue_price
    if host_type == "perpetual":
        redemption_face = (div_amt / rd_flat) if rd_flat > 0 else issue_price
    else:
        redemption_face = issue_price

    # ★E3a: forward 이관(curve_bootstrap). C모델 할인은 use_term_structure 게이트 유지(불변).
    rf_steps = forward_steps(rf_curve, t_years, steps) if use_term_structure else None
    rd_steps = forward_steps(rd_curve, t_years, steps) if use_term_structure else None

    conv = ctx.get("rights", {}).get("conversion", {})
    conv_price = float(conv.get("strike") or issue_price)
    conv_ratio = float(conv.get("ratio") or 1.0) * (issue_price / conv_price)

    q_conv = max(0.0, div_rate - q)

    def _spec(qd, st=steps):
        return CBLatticeSpec(
            s0=s0, sigma=sigma, t_years=t_years, steps=st, rf=rf_flat, rd=rd_flat, q=qd,
            face=redemption_face,
            coupon_per_year=div_amt, freq=1,
            conv_enabled=True, conv_ratio=conv_ratio, conv_start_t=0.0,
            put_enabled=False, call_enabled=False,
        )

    host_vb, _ = tf_value_split(_spec(0.0), rf_steps=rf_steps, rd_steps=rd_steps)
    tvb, tve = tf_value_split(_spec(q_conv), rf_steps=rf_steps, rd_steps=rd_steps)
    total = tvb + tve

    # 보고서용 트리(최종 실행 = q_conv 적용 TF, 보고서용 steps). 12키 값 불변.
    _rsteps = report_steps(t_years, steps)
    rf_steps_rep = forward_steps(rf_curve, t_years, _rsteps) if use_term_structure else None
    rd_steps_rep = forward_steps(rd_curve, t_years, _rsteps) if use_term_structure else None
    _rb, _re, trees = tf_value_with_trees(_spec(q_conv, _rsteps),
                                          rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    trees["tree_meta"]["rate_mode"] = "BOOTSTRAPPED_FORWARD" if rf_steps_rep else "FLAT_FALLBACK"

    def _total_at(sig, s0_):
        sp = CBLatticeSpec(
            s0=s0_, sigma=sig, t_years=t_years, steps=_rsteps, rf=rf_flat, rd=rd_flat, q=q_conv,
            face=redemption_face, coupon_per_year=div_amt, freq=1,
            conv_enabled=True, conv_ratio=conv_ratio, conv_start_t=0.0,
            put_enabled=False, call_enabled=False)
        vb, ve = tf_value_split(sp, rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
        return vb + ve
    sensitivity = sensitivity_grid(_total_at, sigma, s0, "TF_LATTICE", _rsteps)
    curve_bootstrap = curve_bootstrap_block(rf_curve, rd_curve, t_years)

    preferred = host_vb
    conversion = total - host_vb
    parity = conv_ratio * s0

    components = {
        "bond_value": None,
        "preferred_share_value": round(preferred, 4),
        "conversion_option_value": round(conversion, 4),
        "exchange_option_value": None,
        "warrant_value": None,
        "redemption_option_value": 0.0,
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
        "model_version": ctx.get("model_version", "cps-1.0.0"),
        "lattice_steps": steps,
        "u": trees["tree_meta"]["u"],
        "d": trees["tree_meta"]["d"],
    }
    return {
        "job_id": int(ctx.get("job_id", 0)),
        "instrument_id": int(ctx.get("instrument_id", 0)),
        "instrument_type": "CPS",
        "valuation_date": val_date.isoformat(),
        "status": "DONE",
        "total_fair_value": round(total, 4),
        "per_unit_value": round(total, 4),
        "components": components,
        "key_parameters": key_parameters,
        "reproducibility": {
            "input_hash": ctx.get("input_hash", "0" * 64),
            "seed": int(ctx.get("seed", 20240101)),
            "model_version": ctx.get("model_version", "cps-1.0.0"),
        },
        "warnings": [{"code": "W202", "message": f"CPS host_type={host_type} (perpetual=horizon {PERP_HORIZON_YEARS}y Gordon 근사). 외부 실보고서 미검증(self-consistency/골든/MC 교차검증으로 보증)", "stage": "model"}],
        "errors": [],
        "trees": trees,
        "curve_bootstrap": curve_bootstrap,
        "sensitivity": sensitivity,
    }


# ===========================================================================
# CPS — Goldman-Sachs 격자 분기 (엔진확장-2)
#   CPS = RCPS − 상환권. GS telescoping 2분할:
#     R0 순수 우선주채권(전환 off, q≡0 → rd 할인) → preferred_share_value(host)
#     R1 +전환 → conversion_option_value = R1−R0.  total = R1 = host + 전환. Σ=total.
#   할인만 GS(전환확률 가중). CPS TF(C 모델) 경로 불변.
# ===========================================================================
def calculate_cps_gs(ctx: dict) -> dict:
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

    issue_price = float(terms.get("issue_price") or terms.get("face_value") or 10000.0)
    div_rate = float(terms.get("dividend_preferred_rate") or terms.get("dividend_rate")
                     or terms.get("coupon_rate") or 0.0)
    div_rate = div_rate / 100.0 if div_rate > 1.0 else div_rate

    host_type = str(terms.get("host_type") or "dated").lower()
    steps = int(options.get("lattice_steps") or 120)
    node = int(options.get("node_interval_days") or 30)

    if host_type == "perpetual":
        t_years = PERP_HORIZON_YEARS
    else:
        t_years = steps * node / 360.0

    rf_curve = curves.get("risk_free_curve", []) or []
    rd_curve = curves.get("credit_curve", []) or []
    rf = (_interp_curve_at(rf_curve, t_years) / 100.0) if rf_curve else 0.03
    rd = (_interp_curve_at(rd_curve, t_years) / 100.0) if rd_curve else 0.07
    if rd == 0.0:
        rd = rf
    rf_steps_user = forward_steps(rf_curve, t_years, steps)
    rd_steps_user = forward_steps(rd_curve, t_years, steps)

    div_amt = div_rate * issue_price
    if host_type == "perpetual":
        redemption_face = (div_amt / rd) if rd > 0 else issue_price
    else:
        redemption_face = issue_price

    conv = rights.get("conversion", {}) or {}
    conv_price = float(conv.get("strike") or issue_price)
    conv_ratio = float(conv.get("ratio") or 1.0) * (issue_price / conv_price)

    base = dict(
        s0=s0, sigma=sigma, t_years=t_years, rf=rf, rd=rd, q=q,
        face=redemption_face, coupon_per_year=div_amt, freq=(1 if div_amt > 0 else 0),
        conv_ratio=conv_ratio, conv_start_t=0.0,
        put_enabled=False, call_enabled=False,
    )

    def run(conv_on, st):
        return gs_value(CBLatticeSpec(steps=st, conv_enabled=conv_on, **base),
                        rf_steps=rf_steps_user, rd_steps=rd_steps_user)

    r0 = run(False, steps)     # host
    r1 = run(True, steps)      # +전환
    host = r0
    conversion = r1 - r0
    total = r1

    _rsteps = report_steps(t_years, steps)
    rf_steps_rep = forward_steps(rf_curve, t_years, _rsteps)
    rd_steps_rep = forward_steps(rd_curve, t_years, _rsteps)
    _rb, _re, trees = gs_value_with_trees(CBLatticeSpec(steps=_rsteps, conv_enabled=True, **base),
                                          rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    trees["tree_meta"]["rate_mode"] = "BOOTSTRAPPED_FORWARD" if rf_steps_rep else "FLAT_FALLBACK"

    def _total_at(sig, s0_):
        b = dict(base); b["s0"] = s0_; b["sigma"] = sig
        return gs_value(CBLatticeSpec(steps=_rsteps, conv_enabled=True, **b),
                        rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    sensitivity = sensitivity_grid(_total_at, sigma, s0, "GS", _rsteps)
    curve_bootstrap = curve_bootstrap_block(rf_curve, rd_curve, t_years)

    parity = conv_ratio * s0
    components = {
        "bond_value": None,
        "preferred_share_value": round(host, 4),
        "conversion_option_value": round(conversion, 4),
        "exchange_option_value": None,
        "warrant_value": None,
        "redemption_option_value": 0.0,
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
        "model_version": ctx.get("model_version", "cps-gs-1.0.0"),
        "lattice_steps": steps,
        "u": trees["tree_meta"]["u"],
        "d": trees["tree_meta"]["d"],
    }
    return {
        "job_id": int(ctx.get("job_id", 0)),
        "instrument_id": int(ctx.get("instrument_id", 0)),
        "instrument_type": "CPS",
        "valuation_date": val_date.isoformat(),
        "status": "DONE",
        "total_fair_value": round(total, 4),
        "per_unit_value": round(total, 4),
        "components": components,
        "key_parameters": key_parameters,
        "reproducibility": {
            "input_hash": ctx.get("input_hash", "0" * 64),
            "seed": int(ctx.get("seed", 20240101)),
            "model_version": ctx.get("model_version", "cps-gs-1.0.0"),
        },
        "warnings": [{"code": "W210", "message": f"GS(전환확률 가중 할인) 모형 — CPS host_type={host_type}. C모델(TF)과 값 상이(정상)", "stage": "model"}],
        "errors": [],
        "trees": trees,
        "curve_bootstrap": curve_bootstrap,
        "sensitivity": sensitivity,
    }
