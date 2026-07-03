# ===========================================================================
# FairValue Engine — EB Calculator (TF_LATTICE, Phase 4-δ)
# ---------------------------------------------------------------------------
# EB(교환사채) = CB 에서 전환(자사주) → 교환(타사주). CB 의 채권 host·TF 격자 재사용.
#
# CB 와 다른 점(이것만):
#   1. 전환 → 교환(타사주): 교환옵션 기초자산 = 제3자(target_asset) 주식.
#      TF 격자의 underlying(s0/sigma/q)에 ★target 파라미터를 주입한다. 채권 leg(face/coupon)은
#      발행사 것, 할인은 bond leg @ rd(발행사 신용) / equity leg @ rf. 교환주식수 = ratio·face/strike.
#      ★ target_asset 파라미터(spot/vol/div)는 fixture 의 rights.exchange.target_market 에
#        명시입력(MANUAL, 비상장 전제). 발행사 자사 market(ctx.market)과 키 분리(혼선 차단).
#        상장주 자동조회(MARKET)는 백로그(시장데이터 파이프 대기, 이번 범위 아님).
#        상관관계(발행사 신용 ↔ 타사 주가) 미반영 표준 단순화.
#   2. 희석 미적용: 타사주 교환은 발행사 신주발행 아님 → dilution_effect = 0.
#   3. 상환권(put): CB 와 동일 처리 → redemption_option_value.
#   component(EB): bond_value(+) + exchange_option_value(+) + redemption_option_value(+).
#     conversion/issuer_call/sale_claim/dilution/preferred = 0.
#
# telescoping: R0(교환 off)=bond_value(=CB 채권), R1(+교환)−R0=exchange, R2(+풋)−R1=redemption.
#
# ★ 외부 실보고서 없음 → self-consistency + 골든(eb_case1) + MC 교차검증(target GBM)
#   + CB 정합성(교환대상=자사주·희석 off → CB 와 일치)으로 보증. cb_case1 방식.
#   exchange_option_value 키는 스키마에 이미 존재(불변). 신규 key_parameters 없음.
# pure Python. 새 의존성 없음. cb_calculator 패턴 재사용.
# ===========================================================================
from __future__ import annotations

from datetime import date

from .cb_calculator import _rates_from_curves, _solve_ytm, _to_date, _year_frac
from .tf_lattice import CBLatticeSpec, report_steps, tf_value, tf_value_split, tf_value_with_trees
from .gs_model import gs_value, gs_value_with_trees
from .curve_bootstrap import forward_steps, curve_bootstrap_block
from .sensitivity import sensitivity_grid


def calculate_eb(ctx: dict) -> dict:
    # ★ 모델 분기점: model=GS 면 Goldman-Sachs 격자. 아니면 기존 TF(불변).
    if str(ctx.get("model", "")).upper().startswith("GS"):
        return calculate_eb_gs(ctx)
    terms = ctx.get("terms", {})
    market = ctx.get("market", {})
    rights = ctx.get("rights", {})
    options = ctx.get("options", {})

    val_date = _to_date(ctx["valuation_date"])
    issue_date = _to_date(terms["issue_date"])
    maturity = _to_date(terms["maturity_date"])
    t_years = _year_frac(val_date, maturity)

    face = float(terms.get("face_value") or 10000.0)
    steps = int(options.get("lattice_steps") or 500)

    rf, rd = _rates_from_curves(ctx, t_years)   # 발행사 무위험/신용
    curves = ctx.get("curves", {})
    rf_curve = curves.get("risk_free_curve", []) or []
    rd_curve = curves.get("credit_curve", []) or rf_curve
    rf_steps_user = forward_steps(rf_curve, t_years, steps)
    rd_steps_user = forward_steps(rd_curve, t_years, steps)

    # 쿠폰(발행사 채권)
    freq = int(round(12 / terms["coupon_freq_month"])) if terms.get("coupon_freq_month") else 0
    coupon_per_year = (float(terms.get("coupon_rate") or 0.0) / 100.0) * face

    # ★ 교환옵션 기초자산 = target_asset(타사주). 명시입력(rights.exchange.target_market).
    ex = rights.get("exchange", {}) or {}
    tgt = ex.get("target_market") or {}
    # 키 분리: target_market 이 있으면 그것을, 없으면 ctx.market fallback(정합성 테스트용).
    src = tgt if tgt else market
    tgt_spot = float(src["spot"])
    tgt_vol = float(src.get("volatility") or 0.0)
    tgt_vol = tgt_vol / 100.0 if tgt_vol > 3.0 else tgt_vol   # 45(%) 또는 0.45 허용
    tgt_q = float(src.get("dividend_yield") or 0.0)
    tgt_q = tgt_q / 100.0 if tgt_q > 1.0 else tgt_q

    ex_strike = float(ex.get("strike") or tgt_spot)
    ex_ratio = float(ex.get("ratio") or 1.0)
    shares_per_bond = ex_ratio * face / ex_strike     # 교환 주식수(타사주)
    ex_start_t = max(0.0, _year_frac(val_date, _to_date(ex["start"]))) if ex.get("start") else 0.0
    parity = shares_per_bond * tgt_spot

    # 풋(보유자): CB 와 동일. 보장수익률로 발행일 기준 accrete.
    put = (rights.get("redemption", {}) or {}).get("put", {}) or {}
    put_enabled = bool(put.get("enabled"))
    ytp = float(put.get("yield") or terms.get("guaranteed_yield") or 0.0) / 100.0
    put_date = _to_date(put["start"]) if put.get("start") else maturity
    put_price = face * (1.0 + ytp) ** max(0.0, _year_frac(issue_date, put_date))
    put_start_t = max(0.0, _year_frac(val_date, put_date)) if put.get("start") else 0.0

    # base 격자 스펙: underlying = target_asset(타사주), 채권 = 발행사.
    base = dict(
        s0=tgt_spot, sigma=tgt_vol, t_years=t_years, steps=steps, rf=rf, rd=rd, q=tgt_q,
        face=face, coupon_per_year=coupon_per_year, freq=freq,
        conv_ratio=shares_per_bond, conv_start_t=ex_start_t,
        put_price=put_price, put_start_t=put_start_t,
    )

    def run(ex_on, put_on):
        vb, ve = tf_value_split(CBLatticeSpec(
            conv_enabled=ex_on, put_enabled=put_on, call_enabled=False,
            call_price=0.0, call_start_t=0.0, **base,
        ), rf_steps=rf_steps_user, rd_steps=rd_steps_user)
        return vb + ve

    # telescoping (희석·발행자콜·매도청구권 없음 — EB)
    r0 = run(False, False)                 # 순수채권(발행사) = bond host (CB 와 동일 로직)
    r1 = run(True, False)                  # +교환(타사주)
    r2 = run(True, put_enabled)            # +풋

    # 보고서용 트리(최종 실행 = 교환 on + 풋, 보고서용 steps). 12키 값 불변.
    _rsteps = report_steps(t_years, steps)
    rf_steps_rep = forward_steps(rf_curve, t_years, _rsteps)
    rd_steps_rep = forward_steps(rd_curve, t_years, _rsteps)
    _base_r = dict(base); _base_r["steps"] = _rsteps
    _rb, _re, trees = tf_value_with_trees(CBLatticeSpec(
        conv_enabled=True, put_enabled=put_enabled, call_enabled=False,
        call_price=0.0, call_start_t=0.0, **_base_r,
    ), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    trees["tree_meta"]["rate_mode"] = "BOOTSTRAPPED_FORWARD" if rf_steps_rep else "FLAT_FALLBACK"

    def _total_at(sig, s0_):
        br = dict(_base_r); br["s0"] = s0_; br["sigma"] = sig
        vb, ve = tf_value_split(CBLatticeSpec(
            conv_enabled=True, put_enabled=put_enabled, call_enabled=False,
            call_price=0.0, call_start_t=0.0, **br,
        ), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
        return vb + ve
    sensitivity = sensitivity_grid(_total_at, tgt_vol, tgt_spot, "TF_LATTICE", _rsteps)
    curve_bootstrap = curve_bootstrap_block(rf_curve, rd_curve, t_years)

    bond_value = r0
    exchange_option_value = r1 - r0
    redemption_option_value = r2 - r1
    total = bond_value + exchange_option_value + redemption_option_value

    components = {
        "bond_value": round(bond_value, 4),
        "preferred_share_value": None,
        "conversion_option_value": 0.0,       # EB: 전환 아님(교환)
        "exchange_option_value": round(exchange_option_value, 4),
        "warrant_value": None,
        "redemption_option_value": round(redemption_option_value, 4),
        "issuer_call_value": 0.0,
        "sale_claim_value": 0.0,
        "stock_option_value": None,
        "conditional_option_value": None,
        "dilution_effect": 0.0,               # EB: 타사주 → 희석 없음
        "total_fair_value": round(total, 4),
    }

    ytm = _solve_ytm(r0, coupon_per_year, freq, face, t_years)
    key_parameters = {
        "risk_free_rate": round(rf * 100, 4),
        "ytm": round(ytm * 100, 4) if ytm is not None else None,
        "credit_spread": round((rd - rf) * 100, 4),
        "volatility": round(tgt_vol * 100, 4),     # target_asset 변동성
        "dividend_yield": round(tgt_q * 100, 4),
        "parity": round(parity, 4),
        "discount_rate": round(rd * 100, 4),
        "model_name": "TF_LATTICE",
        "model_version": ctx.get("model_version", "eb-1.0.0"),
        "lattice_steps": steps,
        "u": trees["tree_meta"]["u"],
        "d": trees["tree_meta"]["d"],
    }
    src_kind = "target_market(명시입력)" if tgt else "ctx.market(fallback)"
    return {
        "job_id": int(ctx.get("job_id", 0)),
        "instrument_id": int(ctx.get("instrument_id", 0)),
        "instrument_type": "EB",
        "valuation_date": val_date.isoformat(),
        "status": "DONE",
        "total_fair_value": round(total, 4),
        "per_unit_value": round(total, 4),
        "components": components,
        "key_parameters": key_parameters,
        "reproducibility": {
            "input_hash": ctx.get("input_hash", "0" * 64),
            "seed": int(ctx.get("seed", 20240101)),
            "model_version": ctx.get("model_version", "eb-1.0.0"),
        },
        "warnings": [{"code": "W203", "message": f"EB 교환옵션 underlying={src_kind}(타사주, 비상장 명시입력 전제). 상관관계 미반영. 외부 실보고서 미검증(self-consistency/골든/MC/CB정합성으로 보증)", "stage": "model"}],
        "errors": [],
        "trees": trees,
        "curve_bootstrap": curve_bootstrap,
        "sensitivity": sensitivity,
    }


# ===========================================================================
# EB — Goldman-Sachs 격자 분기 (엔진확장-2)
#   telescoping: R0 순수채권=bond_value, R1(+교환)−R0=exchange, R2(+풋)−R1=redemption.
#   할인만 GS(전환확률 가중). underlying=target_asset(타사주), 채권=발행사. TF 경로 불변.
# ===========================================================================
def calculate_eb_gs(ctx: dict) -> dict:
    terms = ctx.get("terms", {})
    market = ctx.get("market", {})
    rights = ctx.get("rights", {})
    options = ctx.get("options", {})

    val_date = _to_date(ctx["valuation_date"])
    issue_date = _to_date(terms["issue_date"])
    maturity = _to_date(terms["maturity_date"])
    t_years = _year_frac(val_date, maturity)

    face = float(terms.get("face_value") or 10000.0)
    steps = int(options.get("lattice_steps") or 500)

    rf, rd = _rates_from_curves(ctx, t_years)
    curves = ctx.get("curves", {})
    rf_curve = curves.get("risk_free_curve", []) or []
    rd_curve = curves.get("credit_curve", []) or rf_curve
    rf_steps_user = forward_steps(rf_curve, t_years, steps)
    rd_steps_user = forward_steps(rd_curve, t_years, steps)

    freq = int(round(12 / terms["coupon_freq_month"])) if terms.get("coupon_freq_month") else 0
    coupon_per_year = (float(terms.get("coupon_rate") or 0.0) / 100.0) * face

    ex = rights.get("exchange", {}) or {}
    tgt = ex.get("target_market") or {}
    src = tgt if tgt else market
    tgt_spot = float(src["spot"])
    tgt_vol = float(src.get("volatility") or 0.0)
    tgt_vol = tgt_vol / 100.0 if tgt_vol > 3.0 else tgt_vol
    tgt_q = float(src.get("dividend_yield") or 0.0)
    tgt_q = tgt_q / 100.0 if tgt_q > 1.0 else tgt_q

    ex_strike = float(ex.get("strike") or tgt_spot)
    ex_ratio = float(ex.get("ratio") or 1.0)
    shares_per_bond = ex_ratio * face / ex_strike
    ex_start_t = max(0.0, _year_frac(val_date, _to_date(ex["start"]))) if ex.get("start") else 0.0
    parity = shares_per_bond * tgt_spot

    put = (rights.get("redemption", {}) or {}).get("put", {}) or {}
    put_enabled = bool(put.get("enabled"))
    ytp = float(put.get("yield") or terms.get("guaranteed_yield") or 0.0) / 100.0
    put_date = _to_date(put["start"]) if put.get("start") else maturity
    put_price = face * (1.0 + ytp) ** max(0.0, _year_frac(issue_date, put_date))
    put_start_t = max(0.0, _year_frac(val_date, put_date)) if put.get("start") else 0.0

    base = dict(
        s0=tgt_spot, sigma=tgt_vol, t_years=t_years, steps=steps, rf=rf, rd=rd, q=tgt_q,
        face=face, coupon_per_year=coupon_per_year, freq=freq,
        conv_ratio=shares_per_bond, conv_start_t=ex_start_t,
        put_price=put_price, put_start_t=put_start_t,
    )

    def run(ex_on, put_on):
        return gs_value(CBLatticeSpec(
            conv_enabled=ex_on, put_enabled=put_on, call_enabled=False,
            call_price=0.0, call_start_t=0.0, **base,
        ), rf_steps=rf_steps_user, rd_steps=rd_steps_user)

    r0 = run(False, False)
    r1 = run(True, False)
    r2 = run(True, put_enabled)

    _rsteps = report_steps(t_years, steps)
    rf_steps_rep = forward_steps(rf_curve, t_years, _rsteps)
    rd_steps_rep = forward_steps(rd_curve, t_years, _rsteps)
    _base_r = dict(base); _base_r["steps"] = _rsteps
    _rb, _re, trees = gs_value_with_trees(CBLatticeSpec(
        conv_enabled=True, put_enabled=put_enabled, call_enabled=False,
        call_price=0.0, call_start_t=0.0, **_base_r,
    ), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    trees["tree_meta"]["rate_mode"] = "BOOTSTRAPPED_FORWARD" if rf_steps_rep else "FLAT_FALLBACK"

    def _total_at(sig, s0_):
        br = dict(_base_r); br["s0"] = s0_; br["sigma"] = sig
        return gs_value(CBLatticeSpec(
            conv_enabled=True, put_enabled=put_enabled, call_enabled=False,
            call_price=0.0, call_start_t=0.0, **br,
        ), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    sensitivity = sensitivity_grid(_total_at, tgt_vol, tgt_spot, "GS", _rsteps)
    curve_bootstrap = curve_bootstrap_block(rf_curve, rd_curve, t_years)

    bond_value = r0
    exchange_option_value = r1 - r0
    redemption_option_value = r2 - r1
    total = bond_value + exchange_option_value + redemption_option_value

    components = {
        "bond_value": round(bond_value, 4),
        "preferred_share_value": None,
        "conversion_option_value": 0.0,
        "exchange_option_value": round(exchange_option_value, 4),
        "warrant_value": None,
        "redemption_option_value": round(redemption_option_value, 4),
        "issuer_call_value": 0.0,
        "sale_claim_value": 0.0,
        "stock_option_value": None,
        "conditional_option_value": None,
        "dilution_effect": 0.0,
        "total_fair_value": round(total, 4),
    }
    ytm = _solve_ytm(r0, coupon_per_year, freq, face, t_years)
    key_parameters = {
        "risk_free_rate": round(rf * 100, 4),
        "ytm": round(ytm * 100, 4) if ytm is not None else None,
        "credit_spread": round((rd - rf) * 100, 4),
        "volatility": round(tgt_vol * 100, 4),
        "dividend_yield": round(tgt_q * 100, 4),
        "parity": round(parity, 4),
        "discount_rate": round(rd * 100, 4),
        "model_name": "GS",
        "model_version": ctx.get("model_version", "eb-gs-1.0.0"),
        "lattice_steps": steps,
        "u": trees["tree_meta"]["u"],
        "d": trees["tree_meta"]["d"],
    }
    src_kind = "target_market(명시입력)" if tgt else "ctx.market(fallback)"
    return {
        "job_id": int(ctx.get("job_id", 0)),
        "instrument_id": int(ctx.get("instrument_id", 0)),
        "instrument_type": "EB",
        "valuation_date": val_date.isoformat(),
        "status": "DONE",
        "total_fair_value": round(total, 4),
        "per_unit_value": round(total, 4),
        "components": components,
        "key_parameters": key_parameters,
        "reproducibility": {
            "input_hash": ctx.get("input_hash", "0" * 64),
            "seed": int(ctx.get("seed", 20240101)),
            "model_version": ctx.get("model_version", "eb-gs-1.0.0"),
        },
        "warnings": [{"code": "W210", "message": f"GS(전환확률 가중 할인) 모형 — EB 교환옵션 underlying={src_kind}. TF 와 값 상이(정상)", "stage": "model"}],
        "errors": [],
        "trees": trees,
        "curve_bootstrap": curve_bootstrap,
        "sensitivity": sensitivity,
    }
