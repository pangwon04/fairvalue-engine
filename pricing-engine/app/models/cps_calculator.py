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
from .tf_lattice import CBLatticeSpec, tf_value_split

PERP_HORIZON_YEARS = 50.0   # 영구형 격자 절단 horizon(가정)


def _to_date(s) -> date:
    return s if isinstance(s, date) else date.fromisoformat(str(s))


def _forward_steps(curve_pct: list, t_years: float, steps: int):
    """zero 커브(%, [[t,rate]]) → 스텝별 연속복리 forward rate 배열. RCPS 와 동일."""
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


def calculate_cps(ctx: dict) -> dict:
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

    rf_flat = (_interp_curve_at(curves.get("risk_free_curve", []), t_years) / 100.0) if curves.get("risk_free_curve") else 0.03
    rd_flat = (_interp_curve_at(curves.get("credit_curve", []), t_years) / 100.0) if curves.get("credit_curve") else 0.07
    if rd_flat == 0.0:
        rd_flat = rf_flat

    div_amt = div_rate * issue_price
    if host_type == "perpetual":
        redemption_face = (div_amt / rd_flat) if rd_flat > 0 else issue_price
    else:
        redemption_face = issue_price

    rf_steps = _forward_steps(curves.get("risk_free_curve", []), t_years, steps) if use_term_structure else None
    rd_steps = _forward_steps(curves.get("credit_curve", []) or [], t_years, steps) if use_term_structure else None

    conv = ctx.get("rights", {}).get("conversion", {})
    conv_price = float(conv.get("strike") or issue_price)
    conv_ratio = float(conv.get("ratio") or 1.0) * (issue_price / conv_price)

    q_conv = max(0.0, div_rate - q)

    def _spec(qd):
        return CBLatticeSpec(
            s0=s0, sigma=sigma, t_years=t_years, steps=steps, rf=rf_flat, rd=rd_flat, q=qd,
            face=redemption_face,
            coupon_per_year=div_amt, freq=1,
            conv_enabled=True, conv_ratio=conv_ratio, conv_start_t=0.0,
            put_enabled=False, call_enabled=False,
        )

    host_vb, _ = tf_value_split(_spec(0.0), rf_steps=rf_steps, rd_steps=rd_steps)
    tvb, tve = tf_value_split(_spec(q_conv), rf_steps=rf_steps, rd_steps=rd_steps)
    total = tvb + tve

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
    }
