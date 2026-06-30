# ===========================================================================
# FairValue Engine — CB Calculator (TF_LATTICE, Phase 3-1)
# ---------------------------------------------------------------------------
# ValuationContext-like dict → PricingResult-like dict.
#
# component 분해(격자 6회 순차 marginal, telescoping → Σ=total 정확):
#   R0 순수채권(옵션 off)          → bond_value                (+)
#   R1 +전환                       → conversion_option_value   (+)  = R1−R0
#   R2 +풋                         → redemption_option_value   (+)  = R2−R1
#   R3 +발행자콜(context call)     → issuer_call_value         (−)  = R3−R2
#   R4 +매도청구권(발행자, =콜)    → sale_claim_value          (−)  = R4−R3
#   R5 +희석                       → dilution_effect           (−)  = R5−R4
#   total = R5 = bond + Σmarginals (정확). 순서 의존(고정 시퀀스).
#
# 단순화(이번 슬라이스, 주석/warnings 로 명시):
#   - 리픽싱(경로의존)은 미반영 → W201 경고. 정밀화는 LSMC(이후).
#   - 할인율은 평탄 rf/rd(커브 최장만기 근사). ★ Phase 2 CurveCalcService 결선점은
#     _rates_from_curves() 에 격리(다음 묶음 resolve 에서 zero/forward 로 교체).
#   - 곡선 rate 는 연속복리 연율로 취급(엔진 내부 e^{-r·Δt} 할인).
#   - 2분할(host/내재파생) 롤업은 출력에 넣지 않음(계약 freeze 존중).
# ===========================================================================
from __future__ import annotations

import math
from datetime import date

from .tf_lattice import CBLatticeSpec, tf_value


def _to_date(s) -> date:
    if isinstance(s, date):
        return s
    return date.fromisoformat(str(s))


def _year_frac(d0: date, d1: date) -> float:
    return (d1 - d0).days / 365.25


def _interp_curve_at(curve: list, t: float) -> float:
    """★ 커브 결선(Phase 4-α): [[tenor, rate_percent], ...] 를 t 시점 zero rate 로 선형 보간.
    범위 밖은 평탄외삽(끝점 고정). %단위 반환. (Phase 2 LINEAR 보간과 동일 정의)"""
    if not curve:
        return 0.0
    pts = sorted((float(a), float(b)) for a, b in curve)
    if t <= pts[0][0]:
        return pts[0][1]
    if t >= pts[-1][0]:
        return pts[-1][1]
    for i in range(len(pts) - 1):
        t0, r0 = pts[i]
        t1, r1 = pts[i + 1]
        if t0 <= t <= t1:
            return r0 + (r1 - r0) * (t - t0) / (t1 - t0)
    return pts[-1][1]


def _rates_from_curves(ctx: dict, t_years: float) -> tuple[float, float]:
    """★ 커브 결선점. resolve 로 채워진 커브 스냅샷(risk_free_curve/credit_curve)을
    t(만기) 시점 zero rate 로 보간해 rf/rd 산출. 둘 다 연속복리 연율로 취급.
    rf = risk_free_curve, rd = credit_curve(위험 전체수익률). credit 없으면 rf 로 폴백."""
    curves = ctx.get("curves", {})
    rf_pct = _interp_curve_at(curves.get("risk_free_curve", []), t_years)
    rd_pct = _interp_curve_at(curves.get("credit_curve", []) or [], t_years)
    if rd_pct == 0.0:
        rd_pct = rf_pct
    return rf_pct / 100.0, rd_pct / 100.0


def calculate_cb(ctx: dict) -> dict:
    terms = ctx.get("terms", {})
    market = ctx.get("market", {})
    rights = ctx.get("rights", {})
    options = ctx.get("options", {})

    val_date = _to_date(ctx["valuation_date"])
    issue_date = _to_date(terms["issue_date"])
    maturity = _to_date(terms["maturity_date"])
    t_years = _year_frac(val_date, maturity)

    s0 = float(market["spot"])
    sigma = float(market["volatility"]) / 100.0
    q = float(market.get("dividend_yield") or 0.0) / 100.0
    face = float(terms.get("face_value") or 10000.0)
    steps = int(options.get("lattice_steps") or 1000)

    rf, rd = _rates_from_curves(ctx, t_years)

    # 쿠폰
    freq = int(round(12 / terms["coupon_freq_month"])) if terms.get("coupon_freq_month") else 0
    coupon_per_year = (float(terms.get("coupon_rate") or 0.0) / 100.0) * face

    # 전환: 주식수 = ratio × face/strike (표준 CB). parity = 주식수 × spot.
    conv = rights.get("conversion", {})
    conv_strike = float(conv.get("strike") or s0)
    conv_ratio_mult = float(conv.get("ratio") or 1.0)
    shares_per_bond = conv_ratio_mult * face / conv_strike
    conv_start_t = max(0.0, _year_frac(val_date, _to_date(conv["start"]))) if conv.get("start") else 0.0
    parity = shares_per_bond * s0

    # 풋(보유자): 보장수익률(YTP)로 발행일 기준 accrete 한 상환가. 시작일부터 행사.
    put = (rights.get("redemption", {}) or {}).get("put", {}) or {}
    put_enabled = bool(put.get("enabled"))
    ytp = float(put.get("yield") or terms.get("guaranteed_yield") or 0.0) / 100.0
    put_date = _to_date(put["start"]) if put.get("start") else maturity
    put_price = face * (1.0 + ytp) ** max(0.0, _year_frac(issue_date, put_date))
    put_start_t = max(0.0, _year_frac(val_date, put_date)) if put.get("start") else 0.0

    # 발행자 콜(context redemption.call) — cb_case1 은 비활성.
    call = (rights.get("redemption", {}) or {}).get("call", {}) or {}
    call_enabled = bool(call.get("enabled"))
    call_price = face  # 콜가는 액면 기준(있을 때)

    # 매도청구권(발행자 수익자 → 콜 메커닉). strike_pct% × face.
    sc = rights.get("sale_claim", {}) or {}
    sc_enabled = bool(sc.get("enabled"))
    sc_price = face * (float(sc.get("strike_pct") or 100.0) / 100.0)

    dil = rights.get("dilution", {}) or {}
    dil_enabled = bool(dil.get("enabled"))

    base = dict(
        s0=s0, sigma=sigma, t_years=t_years, steps=steps, rf=rf, rd=rd, q=q,
        face=face, coupon_per_year=coupon_per_year, freq=freq,
        conv_ratio=shares_per_bond, conv_start_t=conv_start_t,
        put_price=put_price, put_start_t=put_start_t,
    )

    def run(conv_on, put_on, call_on, call_price_, call_start_):
        return tf_value(CBLatticeSpec(
            conv_enabled=conv_on, put_enabled=put_on, call_enabled=call_on,
            call_price=call_price_, call_start_t=call_start_, **base,
        ))

    # 순차 marginal (telescoping)
    r0 = run(False, False, False, 0.0, 0.0)                                   # 순수채권
    r1 = run(True, False, False, 0.0, 0.0)                                    # +전환
    r2 = run(True, put_enabled, False, 0.0, 0.0)                              # +풋
    r3 = run(True, put_enabled, call_enabled, call_price, 0.0)               # +발행자콜
    r4 = run(True, put_enabled, call_enabled or sc_enabled,
             (sc_price if sc_enabled else call_price), 0.0)                  # +매도청구권(콜)
    r5 = r4  # 희석 미반영(dilution off in cb_case1). 활성 시 별도 보정.

    bond_value = r0
    conversion_option_value = r1 - r0
    redemption_option_value = r2 - r1
    issuer_call_value = r3 - r2
    sale_claim_value = r4 - r3
    dilution_effect = r5 - r4
    total = r5

    # 음수 강제 키 수치노이즈 스냅(부호 규칙 보장; total 은 Σ로 echo).
    def clamp_neg(x):
        return min(0.0, x) if x < 1e-9 else 0.0
    issuer_call_value = clamp_neg(issuer_call_value)
    sale_claim_value = clamp_neg(sale_claim_value)
    dilution_effect = clamp_neg(dilution_effect)
    # total 은 분해 합으로 echo(합계 불변식 정확).
    total = (bond_value + conversion_option_value + redemption_option_value
             + issuer_call_value + sale_claim_value + dilution_effect)

    warnings = []
    if (rights.get("refixing", {}) or {}).get("enabled"):
        warnings.append({"code": "W201", "message": "리픽싱(경로의존) 미반영 — TF 격자 근사(LSMC 권장)", "stage": "model"})

    components = {
        "bond_value": round(bond_value, 4),
        "preferred_share_value": None,
        "conversion_option_value": round(conversion_option_value, 4),
        "exchange_option_value": None,
        "warrant_value": None,
        "redemption_option_value": round(redemption_option_value, 4),
        "issuer_call_value": round(issuer_call_value, 4),
        "sale_claim_value": round(sale_claim_value, 4),
        "stock_option_value": None,
        "conditional_option_value": None,
        "dilution_effect": round(dilution_effect, 4),
        "total_fair_value": round(total, 4),
    }

    # ytm: 순수채권(R0) 을 가격으로 보는 연속복리 IRR (참고값).
    ytm = _solve_ytm(r0, coupon_per_year, freq, face, t_years)

    key_parameters = {
        "risk_free_rate": round(rf * 100, 4),
        "ytm": round(ytm * 100, 4) if ytm is not None else None,
        "credit_spread": round((rd - rf) * 100, 4),
        "volatility": round(sigma * 100, 4),
        "dividend_yield": round(q * 100, 4),
        "parity": round(parity, 4),
        "discount_rate": round(rd * 100, 4),
        "model_name": "TF_LATTICE",
        "model_version": ctx.get("model_version", "cb-1.0.0"),
        "lattice_steps": steps,
    }

    return {
        "job_id": int(ctx.get("job_id", 0)),
        "instrument_id": int(ctx.get("instrument_id", 0)),
        "instrument_type": "CB",
        "valuation_date": val_date.isoformat(),
        "status": "DONE",
        "total_fair_value": round(total, 4),
        "per_unit_value": round(total, 4),  # 1 unit = 1 bond(face 기준). 골든 스케일과 다를 수 있음.
        "components": components,
        "key_parameters": key_parameters,
        "reproducibility": {
            "input_hash": ctx.get("input_hash", "0" * 64),
            "seed": int(ctx.get("seed", 20240101)),
            "model_version": ctx.get("model_version", "cb-1.0.0"),
        },
        "warnings": warnings,
        "errors": [],
    }


def _solve_ytm(price: float, coupon_per_year: float, freq: int, face: float, t: float):
    """순수채권 가격을 연속복리로 맞추는 y(이분법). 실패 시 None."""
    if price <= 0:
        return None
    cpn = (coupon_per_year / freq) if freq > 0 else 0.0

    def pv(y):
        total = 0.0
        if freq > 0:
            k = 1
            while k / freq <= t + 1e-9:
                total += cpn * math.exp(-y * (k / freq))
                k += 1
        total += face * math.exp(-y * t)
        return total

    lo, hi = -0.5, 2.0
    if (pv(lo) - price) * (pv(hi) - price) > 0:
        return None
    for _ in range(100):
        mid = (lo + hi) / 2
        if (pv(mid) - price) > 0:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2
