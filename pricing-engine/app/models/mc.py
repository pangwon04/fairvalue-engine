# ===========================================================================
# FairValue Engine — GBM Monte Carlo (교차검증용, Phase 3-1)
# ---------------------------------------------------------------------------
# 격자 분해 조각(전환옵션)을 독립 입증하기 위한 간단한 GBM 시뮬레이션.
# 통제된 부분문제(European 행사)에서 격자(European 모드)와 1~2% 내 수렴 확인.
# 표준 라이브러리 random 만 사용(시드 고정 → 재현성).
# ===========================================================================
from __future__ import annotations

import math
import random


def european_call_mc(
    s0: float, strike: float, sigma: float, t: float, rf: float, q: float,
    paths: int = 200_000, seed: int = 20240101,
) -> float:
    """European call PV = e^{-rf·t}·E[max(S_T−K,0)], GBM. 위험중립 드리프트 (rf−q)."""
    rng = random.Random(seed)
    drift = (rf - q - 0.5 * sigma * sigma) * t
    vol = sigma * math.sqrt(t)
    disc = math.exp(-rf * t)
    acc = 0.0
    # antithetic variates 로 분산 감소
    half = paths // 2
    for _ in range(half):
        z = rng.gauss(0.0, 1.0)
        for zz in (z, -z):
            st = s0 * math.exp(drift + vol * zz)
            payoff = st - strike
            if payoff > 0:
                acc += payoff
    return disc * acc / (half * 2)
