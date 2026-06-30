# ===========================================================================
# FairValue Engine — Tsiveriotis-Fernandes 격자 (Phase 3-1, CB)
# ---------------------------------------------------------------------------
# CRR 이항격자 + TF 이중할인 backward induction.
#   - 지분요소(Ve): 전환 시 받게 될 주식가치. 무위험 rf 로 할인.
#   - 부채요소(Vb): 현금(채권 원리금/풋). 위험 rd 로 할인.
#   - 각 노드에서 전환(보유자) / 풋(보유자) / 콜(발행자) 최적행사.
#   - 할인은 연속복리(e^{-r·Δt}/스텝). r 은 연속 연율.
#
# ★ 커브 결선점(다음 묶음 resolve): rf/rd 는 지금은 평탄 스칼라 입력.
#   CurveRates 인터페이스(아래)로 분리해 두었으며, Phase 2 CurveCalcService 의
#   zero/forward 로 교체하면 같은 backward induction 이 그대로 동작한다.
#
# pure Python(math 만). 새 의존성 없음.
# ===========================================================================
from __future__ import annotations

import math
from dataclasses import dataclass, field


def crr_params(sigma: float, dt: float, rf: float, q: float) -> tuple[float, float, float]:
    """CRR 계수. u=e^{σ√Δt}, d=1/u, p=(e^{(rf−q)Δt}−d)/(u−d)."""
    u = math.exp(sigma * math.sqrt(dt))
    d = 1.0 / u
    p = (math.exp((rf - q) * dt) - d) / (u - d)
    return u, d, p


def european_call_crr(s0: float, strike: float, sigma: float, t: float, rf: float, q: float, steps: int) -> float:
    """교차검증용 European call CRR 격자(조기행사 없음). MC 와 수렴 비교에 사용."""
    dt = t / steps
    u, d, p = crr_params(sigma, dt, rf, q)
    disc = math.exp(-rf * dt)
    u2 = u * u
    vals = [0.0] * (steps + 1)
    s = s0 * (d ** steps)
    for j in range(steps + 1):
        vals[j] = max(s - strike, 0.0)
        s *= u2
    for step in range(steps - 1, -1, -1):
        for j in range(step + 1):
            vals[j] = disc * (p * vals[j + 1] + (1 - p) * vals[j])
    return vals[0]


@dataclass
class CBLatticeSpec:
    """CB 격자 입력. 옵션 플래그를 토글해 component 분해에 재사용한다."""
    s0: float
    sigma: float
    t_years: float
    steps: int
    rf: float                 # 무위험 연속 연율
    rd: float                 # 위험(신용반영) 연속 연율
    q: float = 0.0
    face: float = 10000.0
    coupon_per_year: float = 0.0   # 연간 쿠폰액(금액)
    freq: int = 0                  # 연 지급 횟수(0=무이표)
    conv_enabled: bool = False
    conv_ratio: float = 1.0
    conv_start_t: float = 0.0
    put_enabled: bool = False
    put_price: float = 0.0
    put_start_t: float = 0.0
    call_enabled: bool = False
    call_price: float = 0.0
    call_start_t: float = 0.0


def _coupon_steps(spec: CBLatticeSpec, dt: float) -> set[int]:
    out: set[int] = set()
    if spec.coupon_per_year > 0 and spec.freq > 0:
        k = 1
        while k / spec.freq <= spec.t_years + 1e-9:
            step = round((k / spec.freq) / dt)
            if 1 <= step <= spec.steps:
                out.add(step)
            k += 1
    return out


def tf_value(spec: CBLatticeSpec) -> float:
    """TF 격자 루트 가치(Vb+Ve). 옵션 플래그에 따라 부분가치 계산에 재사용."""
    n = spec.steps
    dt = spec.t_years / n
    u, d, p = crr_params(spec.sigma, dt, spec.rf, spec.q)
    disc_e = math.exp(-spec.rf * dt)
    disc_b = math.exp(-spec.rd * dt)
    cpn = (spec.coupon_per_year / spec.freq) if spec.freq > 0 else 0.0
    coupon_at = _coupon_steps(spec, dt)

    # 노드 주가는 S0·u^j·d^(m-j) = S0·u^(2j-m). j 증가 시 ×u^2 (O(1) 갱신).
    u2 = u * u
    # 만기 payoff
    Vb = [0.0] * (n + 1)
    Ve = [0.0] * (n + 1)
    s = spec.s0 * (d ** n)  # j=0
    for j in range(n + 1):
        redemption = spec.face + cpn  # 만기 쿠폰 포함
        conv = spec.conv_ratio * s if spec.conv_enabled else -1.0
        if conv >= redemption:
            Ve[j] = conv
            Vb[j] = 0.0
        else:
            Vb[j] = redemption
            Ve[j] = 0.0
        s *= u2

    for step in range(n - 1, -1, -1):
        nVb = [0.0] * (step + 1)
        nVe = [0.0] * (step + 1)
        t = step * dt
        s = spec.s0 * (d ** step)  # j=0
        for j in range(step + 1):
            cb = disc_b * (p * Vb[j + 1] + (1 - p) * Vb[j])
            ce = disc_e * (p * Ve[j + 1] + (1 - p) * Ve[j])
            if step in coupon_at:
                cb += cpn  # 쿠폰은 현금 → 부채요소
            val_b, val_e = cb, ce

            # 전환(보유자, 지분): parity = ratio·S
            if spec.conv_enabled and t >= spec.conv_start_t - 1e-12:
                conv = spec.conv_ratio * s
                if conv >= val_b + val_e:
                    val_b, val_e = 0.0, conv

            # 풋(보유자, 현금): put_price 로 상환
            if spec.put_enabled and t >= spec.put_start_t - 1e-12:
                if spec.put_price > val_b + val_e:
                    val_b, val_e = spec.put_price, 0.0

            # 발행자 콜(발행자가 가치 최소화): 보유자는 max(call_price, 전환) 강제
            if spec.call_enabled and t >= spec.call_start_t - 1e-12:
                cur = val_b + val_e
                if cur > spec.call_price:
                    conv = spec.conv_ratio * s if spec.conv_enabled else 0.0
                    if conv >= spec.call_price:
                        val_b, val_e = 0.0, conv
                    else:
                        val_b, val_e = spec.call_price, 0.0

            nVb[j] = val_b
            nVe[j] = val_e
            s *= u2
        Vb, Ve = nVb, nVe

    return Vb[0] + Ve[0]
