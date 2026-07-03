# ===========================================================================
# FairValue Engine — BW Calculator (신주인수권부사채, Phase 4-ε)
# ---------------------------------------------------------------------------
# BW = CB 채권 host(bond_value @rd 재사용) + 신주인수권(warrant) + 희석(dilution, −) + 상환(put).
# component: bond_value(+) + warrant_value(+) + redemption_option_value(+) + dilution_effect(−).
#
# ★ 분리형/비분리형 = "채권과 얽힘 여부"로만 분기 (회계 근거 2.1.4.3). European/American 은
#   warrant.start/end(행사기간)로 별도 결정 — 분리형이든 비분리형이든 window 있으면 American,
#   만기점만이면 European. (docs/KIFRS_분리판정_규칙.md)
#   - separable=true : 신주인수권 = 별도 금융상품 → 채권과 독립. bond(@rd) + 독립 콜 + 상환 + 희석 단순합산.
#       ★ 이 합성 케이스: 분리형도 window 있으면 American 콜로 평가(European 단순화 아님).
#   - separable=false: 복합상품 → 격자에서 채권보유 vs 신주인수권 행사 노드별 비교(얽힘, tf_value 미러).
#
# ★ 희석 = 페이오프 희석반영법(3.4.1.4): 행사 페이오프에 희석계수 df=N/(N+M) 를 노드별 반영
#   (사전희석 아님). N=shares_outstanding, M=new_shares. 미희석(df=1) vs 희석(df) 두 번 평가해
#   warrant_value=미희석분, dilution_effect=(희석−미희석)<0 로 분리 계상 → Σ=total 유지.
#   분리형(콜)·비분리형(격자) 모두 df 를 행사 페이오프 df·(S−K) 에 노드별 적용 → 3.4.1.4 정합.
#
# ★ 외부 실보고서 없음 → self-consistency + 골든(bw_case1) + MC 교차 + CB 정합성(근사).
#   CB 정합성: 비분리형·df=1·strike=0 이면 tf_value 전환 payoff(shares·S)와 동일 → CB 와 1e-6 일치.
#   strike>0 이면 CB 대비 "행사 현금납입(shares·K)" 만큼 낮음 = 납입구조 차이(정량 설명).
# pure Python. 새 의존성 없음. cb/tf_lattice 재사용.
# ===========================================================================
from __future__ import annotations

import math

from .cb_calculator import _rates_from_curves, _solve_ytm, _to_date, _year_frac
from .tf_lattice import CBLatticeSpec, report_steps, tf_value, tf_value_split, tf_value_with_trees, crr_params, _coupon_steps
from .gs_model import gs_value, gs_value_with_trees
from .curve_bootstrap import forward_steps, curve_bootstrap_block
from .sensitivity import sensitivity_grid


def _standalone_call(s0, K, sigma, T, rf, q, steps, shares, df, american, w_start_t, w_end_t, rf_steps=None):
    """분리형 독립 신주인수권 = 자사주 콜. payoff df·(S−K) (페이오프 희석). @rf.
    american 이면 window[w_start,w_end] 내 조기행사, 아니면 만기만. shares 배.
    ★E3a: rf_steps(스텝별 forward) 주입 시 스텝별 할인·드리프트(평탄이면 flat 동일)."""
    dt = T / steps
    u = math.exp(sigma * math.sqrt(dt))
    d = 1.0 / u
    u2 = u * u

    def rf_at(st):
        return rf_steps[st] if rf_steps is not None else rf

    V = [0.0] * (steps + 1)
    s = s0 * (d ** steps)
    for j in range(steps + 1):
        V[j] = max(df * (s - K), 0.0)
        s *= u2
    for step in range(steps - 1, -1, -1):
        rfn = rf_at(step)
        p = (math.exp((rfn - q) * dt) - d) / (u - d)
        disc = math.exp(-rfn * dt)
        t = step * dt
        s = s0 * (d ** step)
        nV = [0.0] * (step + 1)
        for j in range(step + 1):
            cont = disc * (p * V[j + 1] + (1 - p) * V[j])
            if american and (w_start_t - 1e-9) <= t <= (w_end_t + 1e-9):
                cont = max(cont, df * (s - K))
            nV[j] = cont
            s *= u2
        V = nV
    return shares * V[0]


def _bw_composite(s0, sigma, T, steps, rf, rd, q, face, coupon_per_year, freq,
                  shares, K, df, warr_on, w_start_t, w_end_t, put_on, put_price, put_start_t,
                  rf_steps=None, rd_steps=None):
    """비분리형 복합격자 (tf_value 미러). 노드별 max(채권보유, 신주인수권 행사).
    행사 payoff = shares·df·(S−K) (페이오프 희석, 지분 Ve). df=1,K=0,put off → CB 전환과 동일.
    ★E3a: rf_steps/rd_steps 주입 시 스텝별 forward(평탄이면 flat 동일)."""
    n = steps
    dt = T / n
    u = math.exp(sigma * math.sqrt(dt))
    d = 1.0 / u

    def rf_at(st):
        return rf_steps[st] if rf_steps is not None else rf

    def rd_at(st):
        return rd_steps[st] if rd_steps is not None else rd

    cpn = (coupon_per_year / freq) if freq > 0 else 0.0
    coupon_at = _coupon_steps(CBLatticeSpec(
        s0=s0, sigma=sigma, t_years=T, steps=steps, rf=rf, rd=rd, q=q,
        face=face, coupon_per_year=coupon_per_year, freq=freq), dt)
    u2 = u * u
    Vb = [0.0] * (n + 1)
    Ve = [0.0] * (n + 1)
    s = s0 * (d ** n)
    for j in range(n + 1):
        redemption = face + cpn
        ex = shares * df * (s - K) if (warr_on and w_start_t - 1e-9 <= T <= w_end_t + 1e-9) else -1.0
        if ex >= redemption and ex > 0:
            Ve[j] = ex
            Vb[j] = 0.0
        else:
            Vb[j] = redemption
            Ve[j] = 0.0
        s *= u2
    for step in range(n - 1, -1, -1):
        rfn = rf_at(step)
        rdn = rd_at(step)
        p = (math.exp((rfn - q) * dt) - d) / (u - d)
        disc_e = math.exp(-rfn * dt)
        disc_b = math.exp(-rdn * dt)
        t = step * dt
        s = s0 * (d ** step)
        nVb = [0.0] * (step + 1)
        nVe = [0.0] * (step + 1)
        for j in range(step + 1):
            cb = disc_b * (p * Vb[j + 1] + (1 - p) * Vb[j])
            ce = disc_e * (p * Ve[j + 1] + (1 - p) * Ve[j])
            if step in coupon_at:
                cb += cpn
            vb, ve = cb, ce
            if warr_on and (w_start_t - 1e-9) <= t <= (w_end_t + 1e-9):
                ex = shares * df * (s - K)
                if ex >= vb + ve and ex > 0:
                    vb, ve = 0.0, ex
            if put_on and t >= put_start_t - 1e-9:
                if put_price > vb + ve:
                    vb, ve = put_price, 0.0
            nVb[j] = vb
            nVe[j] = ve
            s *= u2
        Vb, Ve = nVb, nVe
    return Vb[0] + Ve[0]


def _bw_composite_gs(s0, sigma, T, steps, rf, rd, q, face, coupon_per_year, freq,
                     shares, K, df, warr_on, w_start_t, w_end_t, put_on, put_price, put_start_t,
                     rf_steps=None, rd_steps=None):
    """비분리형 복합격자 — GS(전환확률 가중 할인) 버전.
    노드별 max(채권보유, 신주인수권 행사). 행사 payoff = shares·df·(S−K)(지분, q=1),
    풋(현금, q=0). 할인 y=q·rf+(1−q)·rd. df=1,K=0,put off 이면 GS 전환격자와 동일.
    ★E3a: rf_steps/rd_steps 주입 시 노드별 forward(평탄이면 flat 동일)."""
    n = steps
    dt = T / n
    u = math.exp(sigma * math.sqrt(dt))
    d = 1.0 / u

    def rf_at(st):
        return rf_steps[st] if rf_steps is not None else rf

    def rd_at(st):
        return rd_steps[st] if rd_steps is not None else rd

    cpn = (coupon_per_year / freq) if freq > 0 else 0.0
    coupon_at = _coupon_steps(CBLatticeSpec(
        s0=s0, sigma=sigma, t_years=T, steps=steps, rf=rf, rd=rd, q=q,
        face=face, coupon_per_year=coupon_per_year, freq=freq), dt)
    u2 = u * u
    V = [0.0] * (n + 1)
    Q = [0.0] * (n + 1)
    s = s0 * (d ** n)
    for j in range(n + 1):
        redemption = face + cpn
        ex = shares * df * (s - K) if (warr_on and w_start_t - 1e-9 <= T <= w_end_t + 1e-9) else -1.0
        if ex >= redemption and ex > 0:
            V[j] = ex; Q[j] = 1.0
        else:
            V[j] = redemption; Q[j] = 0.0
        s *= u2
    for step in range(n - 1, -1, -1):
        rfn = rf_at(step)
        rdn = rd_at(step)
        p = (math.exp((rfn - q) * dt) - d) / (u - d)
        t = step * dt
        s = s0 * (d ** step)
        nV = [0.0] * (step + 1)
        nQ = [0.0] * (step + 1)
        for j in range(step + 1):
            q_cont = p * Q[j + 1] + (1 - p) * Q[j]
            y = q_cont * rfn + (1 - q_cont) * rdn
            cont = math.exp(-y * dt) * (p * V[j + 1] + (1 - p) * V[j])
            if step in coupon_at:
                cont += cpn
            val, qh = cont, q_cont
            if warr_on and (w_start_t - 1e-9) <= t <= (w_end_t + 1e-9):
                ex = shares * df * (s - K)
                if ex >= val and ex > 0:
                    val, qh = ex, 1.0
            if put_on and t >= put_start_t - 1e-9:
                if put_price > val:
                    val, qh = put_price, 0.0
            nV[j] = val; nQ[j] = qh
            s *= u2
        V, Q = nV, nQ
    return V[0]


def calculate_bw(ctx: dict) -> dict:
    # ★ 모델 분기점: model=GS 면 Goldman-Sachs 격자. 아니면 기존 TF(불변).
    if str(ctx.get("model", "")).upper().startswith("GS"):
        return calculate_bw_gs(ctx)
    terms = ctx.get("terms", {})
    market = ctx.get("market", {})
    rights = ctx.get("rights", {})
    options = ctx.get("options", {})

    val_date = _to_date(ctx["valuation_date"])
    issue_date = _to_date(terms["issue_date"])
    maturity = _to_date(terms["maturity_date"])
    t_years = _year_frac(val_date, maturity)

    s0 = float(market["spot"])
    sigma = float(market["volatility"])
    sigma = sigma / 100.0  # CB 관례: 퍼센트 입력
    q = float(market.get("dividend_yield") or 0.0)
    q = q / 100.0  # CB 관례: 퍼센트 입력
    face = float(terms.get("face_value") or 10000.0)
    steps = int(options.get("lattice_steps") or 300)

    rf, rd = _rates_from_curves(ctx, t_years)
    curves = ctx.get("curves", {})
    rf_curve = curves.get("risk_free_curve", []) or []
    rd_curve = curves.get("credit_curve", []) or rf_curve
    rf_steps_user = forward_steps(rf_curve, t_years, steps)
    rd_steps_user = forward_steps(rd_curve, t_years, steps)
    freq = int(round(12 / terms["coupon_freq_month"])) if terms.get("coupon_freq_month") else 0
    coupon_per_year = (float(terms.get("coupon_rate") or 0.0) / 100.0) * face

    # 신주인수권
    w = rights.get("warrant", {}) or {}
    K = float(w["strike"]) if w.get("strike") is not None else s0
    quantity = float(w.get("quantity") or 1.0)     # 사채 1단위당 신주인수권 수(주)
    separable = bool(w.get("separable"))
    w_start_t = max(0.0, _year_frac(val_date, _to_date(w["start"]))) if w.get("start") else t_years
    w_end_t = _year_frac(val_date, _to_date(w["end"])) if w.get("end") else t_years
    american = w_start_t < t_years - 1e-9          # window 있으면 American, 만기점만이면 European
    parity = quantity * s0

    # 희석계수 df = N/(N+M) (페이오프 희석반영법)
    dil = rights.get("dilution", {}) or {}
    dil_on = bool(dil.get("enabled"))
    N = float(market.get("shares_outstanding") or 0.0)
    M = float(dil.get("new_shares") or 0.0)
    df = (N / (N + M)) if (dil_on and N > 0 and M > 0) else 1.0

    # 상환(put): CB 와 동일 accrete
    put = (rights.get("redemption", {}) or {}).get("put", {}) or {}
    put_on = bool(put.get("enabled"))
    ytp = float(put.get("yield") or terms.get("guaranteed_yield") or 0.0) / 100.0
    put_date = _to_date(put["start"]) if put.get("start") else maturity
    put_price = face * (1.0 + ytp) ** max(0.0, _year_frac(issue_date, put_date))
    put_start_t = max(0.0, _year_frac(val_date, put_date)) if put.get("start") else 0.0

    # 채권 host (전환/풋 off) = CB 순수채권 @rd. ★E3a: tf_value_split(forward)로 결선.
    bond_spec = CBLatticeSpec(
        s0=s0, sigma=sigma, t_years=t_years, steps=steps, rf=rf, rd=rd, q=q,
        face=face, coupon_per_year=coupon_per_year, freq=freq, conv_enabled=False, put_enabled=False)
    _bvb, _bve = tf_value_split(bond_spec, rf_steps=rf_steps_user, rd_steps=rd_steps_user)
    bond_value = _bvb + _bve

    if separable:
        # 분리형: 채권·워런트 독립. 상환은 채권에만.
        _pvb, _pve = tf_value_split(CBLatticeSpec(
            s0=s0, sigma=sigma, t_years=t_years, steps=steps, rf=rf, rd=rd, q=q,
            face=face, coupon_per_year=coupon_per_year, freq=freq, conv_enabled=False,
            put_enabled=put_on, put_price=put_price, put_start_t=put_start_t),
            rf_steps=rf_steps_user, rd_steps=rd_steps_user)
        bond_put = _pvb + _pve
        redemption_option_value = bond_put - bond_value
        w_undil = _standalone_call(s0, K, sigma, t_years, rf, q, steps, quantity, 1.0, american, w_start_t, w_end_t, rf_steps=rf_steps_user)
        w_dil = _standalone_call(s0, K, sigma, t_years, rf, q, steps, quantity, df, american, w_start_t, w_end_t, rf_steps=rf_steps_user)
        warrant_value = w_undil
        dilution_effect = w_dil - w_undil
        total = bond_value + warrant_value + dilution_effect + redemption_option_value
    else:
        # 비분리형: 복합격자(얽힘). telescoping 으로 12키 분해.
        def comp(df_, warr, put_):
            return _bw_composite(s0, sigma, t_years, steps, rf, rd, q, face, coupon_per_year, freq,
                                 quantity, K, df_, warr, w_start_t, w_end_t, put_, put_price, put_start_t,
                                 rf_steps=rf_steps_user, rd_steps=rd_steps_user)
        base_bond = comp(1.0, False, False)          # = bond_value (전환/풋 off)
        w_undil_c = comp(1.0, True, False)           # +워런트(미희석)
        w_dil_c = comp(df, True, False)              # 희석 반영
        full = comp(df, True, put_on)                # +상환
        bond_value = base_bond
        warrant_value = w_undil_c - base_bond
        dilution_effect = w_dil_c - w_undil_c
        redemption_option_value = full - w_dil_c
        total = full

    if dilution_effect > 0:                          # 부호 규칙(희석≤0) 수치노이즈 스냅
        dilution_effect = 0.0

    # 보고서용 트리 — 기초자산(자사주)·가치 시각화. TF 격자 표현(행사가·희석·분리형은 12키 값에 반영).
    _rsteps = report_steps(t_years, steps)
    rf_steps_rep = forward_steps(rf_curve, t_years, _rsteps)
    rd_steps_rep = forward_steps(rd_curve, t_years, _rsteps)

    def _rep_spec(sig, s0_):
        return CBLatticeSpec(
            s0=s0_, sigma=sig, t_years=t_years, steps=_rsteps, rf=rf, rd=rd, q=q,
            face=face, coupon_per_year=coupon_per_year, freq=freq,
            conv_enabled=True, conv_ratio=quantity, conv_start_t=(w_start_t if american else t_years),
            put_enabled=put_on, put_price=put_price, put_start_t=put_start_t)

    _rb, _re, trees = tf_value_with_trees(_rep_spec(sigma, s0), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    trees["tree_meta"]["rate_mode"] = "BOOTSTRAPPED_FORWARD" if rf_steps_rep else "FLAT_FALLBACK"

    # 민감도 — 트리 표현 spec(base 셀 == 트리 루트 1e-6). 행사가·희석·분리형은 12키에만 반영.
    def _total_at(sig, s0_):
        vb, ve = tf_value_split(_rep_spec(sig, s0_), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
        return vb + ve
    sensitivity = sensitivity_grid(_total_at, sigma, s0, "TF_LATTICE", _rsteps)
    curve_bootstrap = curve_bootstrap_block(rf_curve, rd_curve, t_years)

    components = {
        "bond_value": round(bond_value, 4),
        "preferred_share_value": None,
        "conversion_option_value": 0.0,
        "exchange_option_value": None,
        "warrant_value": round(warrant_value, 4),
        "redemption_option_value": round(redemption_option_value, 4),
        "issuer_call_value": 0.0,
        "sale_claim_value": 0.0,
        "stock_option_value": None,
        "conditional_option_value": None,
        "dilution_effect": round(dilution_effect, 4),
        "total_fair_value": round(total, 4),
    }
    ytm = _solve_ytm(bond_value, coupon_per_year, freq, face, t_years)
    key_parameters = {
        "risk_free_rate": round(rf * 100, 4),
        "ytm": round(ytm * 100, 4) if ytm is not None else None,
        "credit_spread": round((rd - rf) * 100, 4),
        "volatility": round(sigma * 100, 4),
        "dividend_yield": round(q * 100, 4),
        "parity": round(parity, 4),
        "discount_rate": round(rd * 100, 4),
        "model_name": "TF_LATTICE",
        "model_version": ctx.get("model_version", "bw-1.0.0"),
        "lattice_steps": steps,
        "u": trees["tree_meta"]["u"],
        "d": trees["tree_meta"]["d"],
    }
    style = "American" if american else "European"
    mode = "분리형(독립합산)" if separable else "비분리형(복합격자)"
    return {
        "job_id": int(ctx.get("job_id", 0)),
        "instrument_id": int(ctx.get("instrument_id", 0)),
        "instrument_type": "BW",
        "valuation_date": val_date.isoformat(),
        "status": "DONE",
        "total_fair_value": round(total, 4),
        "per_unit_value": round(total, 4),
        "components": components,
        "key_parameters": key_parameters,
        "reproducibility": {
            "input_hash": ctx.get("input_hash", "0" * 64),
            "seed": int(ctx.get("seed", 20240101)),
            "model_version": ctx.get("model_version", "bw-1.0.0"),
        },
        "warnings": [{"code": "W204", "message": f"BW {mode}·{style} 행사·df={df:.4f}(페이오프 희석 3.4.1.4). 외부 실보고서 미검증(self-consistency/골든/MC/CB정합성으로 보증)", "stage": "model"}],
        "errors": [],
        "trees": trees,
        "curve_bootstrap": curve_bootstrap,
        "sensitivity": sensitivity,
    }


# ===========================================================================
# BW — Goldman-Sachs 격자 분기 (엔진확장-2)
#   비분리형: _bw_composite_gs(전환확률 가중 할인)로 telescoping.
#   분리형: 채권(gs_value @전환없음=rd) + 독립 신주인수권(_standalone_call @rf 지분).
#     분리형은 채권↔워런트 상호작용이 없어 GS≡TF(전환확률 가중 대상 아님) — 정상.
#   할인만 GS(비분리형에서 TF 와 값 상이). TF 경로 불변.
# ===========================================================================
def calculate_bw_gs(ctx: dict) -> dict:
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
    steps = int(options.get("lattice_steps") or 300)

    rf, rd = _rates_from_curves(ctx, t_years)
    curves = ctx.get("curves", {})
    rf_curve = curves.get("risk_free_curve", []) or []
    rd_curve = curves.get("credit_curve", []) or rf_curve
    rf_steps_user = forward_steps(rf_curve, t_years, steps)
    rd_steps_user = forward_steps(rd_curve, t_years, steps)
    freq = int(round(12 / terms["coupon_freq_month"])) if terms.get("coupon_freq_month") else 0
    coupon_per_year = (float(terms.get("coupon_rate") or 0.0) / 100.0) * face

    w = rights.get("warrant", {}) or {}
    K = float(w["strike"]) if w.get("strike") is not None else s0
    quantity = float(w.get("quantity") or 1.0)
    separable = bool(w.get("separable"))
    w_start_t = max(0.0, _year_frac(val_date, _to_date(w["start"]))) if w.get("start") else t_years
    w_end_t = _year_frac(val_date, _to_date(w["end"])) if w.get("end") else t_years
    american = w_start_t < t_years - 1e-9
    parity = quantity * s0

    dil = rights.get("dilution", {}) or {}
    dil_on = bool(dil.get("enabled"))
    N = float(market.get("shares_outstanding") or 0.0)
    M = float(dil.get("new_shares") or 0.0)
    df = (N / (N + M)) if (dil_on and N > 0 and M > 0) else 1.0

    put = (rights.get("redemption", {}) or {}).get("put", {}) or {}
    put_on = bool(put.get("enabled"))
    ytp = float(put.get("yield") or terms.get("guaranteed_yield") or 0.0) / 100.0
    put_date = _to_date(put["start"]) if put.get("start") else maturity
    put_price = face * (1.0 + ytp) ** max(0.0, _year_frac(issue_date, put_date))
    put_start_t = max(0.0, _year_frac(val_date, put_date)) if put.get("start") else 0.0

    # 채권 host (전환/풋 off) = GS 순수채권(q≡0 → rd 할인). ★E3a forward 결선.
    bond_value = gs_value(CBLatticeSpec(
        s0=s0, sigma=sigma, t_years=t_years, steps=steps, rf=rf, rd=rd, q=q,
        face=face, coupon_per_year=coupon_per_year, freq=freq, conv_enabled=False, put_enabled=False),
        rf_steps=rf_steps_user, rd_steps=rd_steps_user)

    if separable:
        bond_put = gs_value(CBLatticeSpec(
            s0=s0, sigma=sigma, t_years=t_years, steps=steps, rf=rf, rd=rd, q=q,
            face=face, coupon_per_year=coupon_per_year, freq=freq, conv_enabled=False,
            put_enabled=put_on, put_price=put_price, put_start_t=put_start_t),
            rf_steps=rf_steps_user, rd_steps=rd_steps_user)
        redemption_option_value = bond_put - bond_value
        w_undil = _standalone_call(s0, K, sigma, t_years, rf, q, steps, quantity, 1.0, american, w_start_t, w_end_t, rf_steps=rf_steps_user)
        w_dil = _standalone_call(s0, K, sigma, t_years, rf, q, steps, quantity, df, american, w_start_t, w_end_t, rf_steps=rf_steps_user)
        warrant_value = w_undil
        dilution_effect = w_dil - w_undil
        total = bond_value + warrant_value + dilution_effect + redemption_option_value
    else:
        def comp(df_, warr, put_):
            return _bw_composite_gs(s0, sigma, t_years, steps, rf, rd, q, face, coupon_per_year, freq,
                                    quantity, K, df_, warr, w_start_t, w_end_t, put_, put_price, put_start_t,
                                    rf_steps=rf_steps_user, rd_steps=rd_steps_user)
        base_bond = comp(1.0, False, False)
        w_undil_c = comp(1.0, True, False)
        w_dil_c = comp(df, True, False)
        full = comp(df, True, put_on)
        bond_value = base_bond
        warrant_value = w_undil_c - base_bond
        dilution_effect = w_dil_c - w_undil_c
        redemption_option_value = full - w_dil_c
        total = full

    if dilution_effect > 0:
        dilution_effect = 0.0

    _rsteps = report_steps(t_years, steps)
    rf_steps_rep = forward_steps(rf_curve, t_years, _rsteps)
    rd_steps_rep = forward_steps(rd_curve, t_years, _rsteps)

    def _rep_spec(sig, s0_):
        return CBLatticeSpec(
            s0=s0_, sigma=sig, t_years=t_years, steps=_rsteps, rf=rf, rd=rd, q=q,
            face=face, coupon_per_year=coupon_per_year, freq=freq,
            conv_enabled=True, conv_ratio=quantity, conv_start_t=(w_start_t if american else t_years),
            put_enabled=put_on, put_price=put_price, put_start_t=put_start_t)

    _rb, _re, trees = gs_value_with_trees(_rep_spec(sigma, s0), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    trees["tree_meta"]["rate_mode"] = "BOOTSTRAPPED_FORWARD" if rf_steps_rep else "FLAT_FALLBACK"

    def _total_at(sig, s0_):
        return gs_value(_rep_spec(sig, s0_), rf_steps=rf_steps_rep, rd_steps=rd_steps_rep)
    sensitivity = sensitivity_grid(_total_at, sigma, s0, "GS", _rsteps)
    curve_bootstrap = curve_bootstrap_block(rf_curve, rd_curve, t_years)

    components = {
        "bond_value": round(bond_value, 4),
        "preferred_share_value": None,
        "conversion_option_value": 0.0,
        "exchange_option_value": None,
        "warrant_value": round(warrant_value, 4),
        "redemption_option_value": round(redemption_option_value, 4),
        "issuer_call_value": 0.0,
        "sale_claim_value": 0.0,
        "stock_option_value": None,
        "conditional_option_value": None,
        "dilution_effect": round(dilution_effect, 4),
        "total_fair_value": round(total, 4),
    }
    ytm = _solve_ytm(bond_value, coupon_per_year, freq, face, t_years)
    key_parameters = {
        "risk_free_rate": round(rf * 100, 4),
        "ytm": round(ytm * 100, 4) if ytm is not None else None,
        "credit_spread": round((rd - rf) * 100, 4),
        "volatility": round(sigma * 100, 4),
        "dividend_yield": round(q * 100, 4),
        "parity": round(parity, 4),
        "discount_rate": round(rd * 100, 4),
        "model_name": "GS",
        "model_version": ctx.get("model_version", "bw-gs-1.0.0"),
        "lattice_steps": steps,
        "u": trees["tree_meta"]["u"],
        "d": trees["tree_meta"]["d"],
    }
    style = "American" if american else "European"
    mode = "분리형(독립합산)" if separable else "비분리형(복합격자)"
    return {
        "job_id": int(ctx.get("job_id", 0)),
        "instrument_id": int(ctx.get("instrument_id", 0)),
        "instrument_type": "BW",
        "valuation_date": val_date.isoformat(),
        "status": "DONE",
        "total_fair_value": round(total, 4),
        "per_unit_value": round(total, 4),
        "components": components,
        "key_parameters": key_parameters,
        "reproducibility": {
            "input_hash": ctx.get("input_hash", "0" * 64),
            "seed": int(ctx.get("seed", 20240101)),
            "model_version": ctx.get("model_version", "bw-gs-1.0.0"),
        },
        "warnings": [{"code": "W210", "message": f"GS(전환확률 가중 할인) 모형 — BW {mode}·{style}·df={df:.4f}. TF 와 값 상이(비분리형)", "stage": "model"}],
        "errors": [],
        "trees": trees,
        "curve_bootstrap": curve_bootstrap,
        "sensitivity": sensitivity,
    }
