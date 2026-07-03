# ===========================================================================
# FairValue Engine — 커브 부트스트래핑(엔진확장-3a, 스텝별 선도금리 결선)
# ---------------------------------------------------------------------------
# 격자 할인을 "만기 시점 평탄 근사" → "스텝별 선도금리(term-structure)"로 대체하기 위한
# 공용 모듈. RCPS/CPS 에 있던 _forward_steps 를 이관(동일 로직 → 1e-6 동일성 게이트).
#
# ★ 설계 가정(관행 결정): 입력 커브([[tenor_y, rate_%],...])를 ★연속복리 zero(spot) 커브로
#   해석한다(근사). 이유:
#     - 엔진 내부 할인이 이미 연속복리 e^{-r·Δt} 이고, 소스(KISPRICING 등)가 zero 커브다.
#     - annual→continuous 환산을 하면 평탄 커브에서도 값이 이동(DeepSearch host 앵커 이탈) →
#       환산 없이 입력값을 연속 zero 로 그대로 사용해 외부 앵커를 보존한다.
#     - par-쿠폰 재-stripping(YTM→Spot)은 입력이 이미 zero 인 소스에선 이중스트립이 되어
#       보류한다. bootstrap_par_strip()에 옵션으로 구현만 두되 기본 경로에선 호출하지 않는다.
#   → 최종 판정은 외부 앵커(모비어스 ±2%)·평탄 sanity 게이트가 한다. 억지 calibration 금지.
#
# pure Python(math 만). 새 의존성 없음.
# ===========================================================================
from __future__ import annotations

import math


def interp_curve_at(curve: list, t: float) -> float:
    """[[tenor, rate_percent], ...] 를 t 시점 rate 로 선형보간. 범위 밖 평탄외삽(끝점 고정).
    %단위 반환. cb_calculator._interp_curve_at 와 동일 정의(LINEAR)."""
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


def forward_steps(curve_pct: list, t_years: float, steps: int):
    """zero 커브(%, [[t,rate]]) → 스텝별 연속복리 forward rate(decimal) 배열(길이 steps).
    f_n = (z(t_{n+1})·t_{n+1} − z(t_n)·t_n) / Δt.  (RCPS/CPS _forward_steps 이관 — 동일 로직)
    총할인 ∏exp(−f_n·Δt) = exp(−z(t)·t) = 만기별 zero 할인(정통 term-structure).
    커브 없음(퇴화)이면 None → 호출부는 평탄 스칼라 폴백(기존 동작)."""
    if not curve_pct:
        return None
    dt = t_years / steps
    out = []
    for n in range(steps):
        t0 = n * dt
        t1 = (n + 1) * dt
        z0 = interp_curve_at(curve_pct, t0) / 100.0
        z1 = interp_curve_at(curve_pct, t1) / 100.0
        fwd = (z1 * t1 - z0 * t0) / dt if dt > 0 else z1
        out.append(fwd)
    return out


def bootstrap_table(curve_pct: list, t_years: float, grid: float = 0.5):
    """보고서 Appendix '이자율 산정' 표 데이터. 0.5y 격자로 만기까지.
      - ytm    : 입력 원천값 그대로(환산 금지 — 원천에 없는 수치 생성 = 추적성 저해).
      - spot   : 연속복리 zero(우리 해석). ★zero-해석이라 수치는 ytm 과 동일하되 '연속 zero'로 해석.
      - forward: 구간 [t_{i-1}, t_i] 선도금리(연속). 커브가 기울면 ytm/spot 과 달라진다.
    각 행 %단위. 커브 퇴화 시 None."""
    if not curve_pct:
        return None
    n = max(1, int(math.ceil(t_years / grid - 1e-9)))
    ytm, spot, forward = [], [], []
    prev_t = 0.0
    for i in range(1, n + 1):
        t = min(i * grid, t_years)
        z_pct = interp_curve_at(curve_pct, t)          # 입력 원천값(=우리 해석상 연속 zero %)
        z = z_pct / 100.0
        z0 = interp_curve_at(curve_pct, prev_t) / 100.0
        fwd = (z * t - z0 * prev_t) / (t - prev_t) if t > prev_t else z
        ytm.append([round(t, 4), round(z_pct, 6)])      # 원천값 그대로
        spot.append([round(t, 4), round(z_pct, 6)])     # 연속 zero(해석), 수치는 원천과 동일
        forward.append([round(t, 4), round(fwd * 100, 6)])
        prev_t = t
    return {
        "ytm": ytm,
        "spot": spot,
        "forward": forward,
        "grid": grid,
        "assumption": "입력커브를 연속복리 zero 로 해석(근사, par-strip 보류). ytm행=원천값 그대로, spot행=연속zero 해석(동일수치), forward행=구간 선도금리.",
    }


def curve_bootstrap_block(rf_curve: list, rd_curve: list, t_years: float, grid: float = 0.5):
    """result['curve_bootstrap'] 블록: rf/rd 각각 {ytm, spot, forward}. 둘 다 퇴화면 None."""
    rf_tbl = bootstrap_table(rf_curve, t_years, grid)
    rd_tbl = bootstrap_table(rd_curve, t_years, grid)
    if rf_tbl is None and rd_tbl is None:
        return None
    return {"rf": rf_tbl, "rd": rd_tbl, "rate_mode": "BOOTSTRAPPED_FORWARD"}


# ---------------------------------------------------------------------------
# par-쿠폰 stripping(YTM→Spot) — ★기본 OFF 옵션. 입력이 par-yield 커브라고 가정할 때만
#   사용. 기본 경로(연속 zero 해석)에선 호출하지 않는다(이중스트립 방지·앵커 보존).
#   보류 사유: 소스가 이미 zero 커브. 활성화 시 외부 앵커 로컬 재검증 필수.
# ---------------------------------------------------------------------------
def bootstrap_par_strip(curve_pct: list, t_years: float, grid: float = 0.5, freq: int = 2):
    """par-쿠폰 YTM 커브 → 연속 zero DF 스트리핑(0.5y, 반기쿠폰 가정). 반환: [[t, zero_cont_%],...].
    ★기본 경로 미사용(옵션). 평탄 커브는 zero≈ytm(반기환산)로 수렴."""
    if not curve_pct:
        return None
    period = 1.0 / freq
    n = max(1, int(math.ceil(t_years / period - 1e-9)))
    dfs = []  # (t, DF)
    for i in range(1, n + 1):
        t = i * period
        y = interp_curve_at(curve_pct, t) / 100.0
        c = y * period  # 쿠폰(반기)
        acc = 0.0
        for (tj, dfj) in dfs:
            acc += c * dfj
        df = (1.0 - acc) / (1.0 + c)
        df = max(df, 1e-12)
        dfs.append((t, df))
    return [[round(t, 4), round(-math.log(df) / t * 100.0, 6)] for (t, df) in dfs]
