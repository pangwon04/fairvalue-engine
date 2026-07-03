"""엔진확장-3a: 커브 부트스트래핑(스텝별 forward) 결선 검증.

게이트:
  - 평탄 sanity(BLOCKING): 평탄 커브 → forward=평탄(1e-12), 격자 forward경로 == 평탄 스칼라경로(1e-6).
  - 이관 동일성(BLOCKING): curve_bootstrap.forward_steps == 레거시 _forward_steps 공식(1e-12).
  - 외부 앵커(BLOCKING): 모비어스 RCPS 3기준일 total vs 삼도 ±2%(E3a 후 유지).
  - bootstrap_table 구조: ytm/spot/forward 행·0.5y 격자.
  - 결과 옵션 필드 curve_bootstrap 존재·rate_mode.
"""
import json
import math
from pathlib import Path

import pytest

from app.models.curve_bootstrap import forward_steps, bootstrap_table, interp_curve_at, curve_bootstrap_block
from app.models.tf_lattice import CBLatticeSpec, tf_value_split
from app.models.registry import LIBRARY

FIX = Path(__file__).resolve().parent / "fixtures"
GOLD_REAL = Path(__file__).resolve().parents[2] / "golden-values" / "rcps_real_cases.json"


def _legacy_forward(curve_pct, t_years, steps):
    """레거시 rcps/cps _forward_steps 원본 공식(이관 전) 재현 — 동일성 대조군."""
    if not curve_pct:
        return None
    dt = t_years / steps
    out = []
    for n in range(steps):
        t0 = n * dt
        t1 = (n + 1) * dt
        z0 = interp_curve_at(curve_pct, t0) / 100.0
        z1 = interp_curve_at(curve_pct, t1) / 100.0
        out.append((z1 * t1 - z0 * t0) / dt if dt > 0 else z1)
    return out


# --------------------------------------------------------------------------- #
# 평탄 sanity (BLOCKING)
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize("rate", [3.0, 5.5, 22.0])
def test_flat_curve_forward_equals_flat(rate):
    """평탄 커브 → 모든 스텝 forward == 평탄값(연속, 1e-12)."""
    curve = [[0.25, rate], [10.0, rate]]
    fs = forward_steps(curve, 3.7, 40)
    assert all(abs(f - rate / 100.0) < 1e-12 for f in fs)


def test_flat_plumbing_lattice_matches_scalar():
    """★배관 무결성: 평탄 커브 forward 배열 경로 == 평탄 스칼라 경로(1e-6).
    tf_value_split(rf_steps=forward(평탄)) == tf_value_split(rf_steps=None, 평탄 스칼라)."""
    rf, rd = 0.035, 0.12
    sp = CBLatticeSpec(s0=10000, sigma=0.4, t_years=3.0, steps=100, rf=rf, rd=rd, q=0.0,
                       face=12000, coupon_per_year=300, freq=2,
                       conv_enabled=True, conv_ratio=1.0, conv_start_t=0.0,
                       put_enabled=True, put_price=12500, put_start_t=1.0)
    rf_curve = [[0.25, rf * 100], [10, rf * 100]]
    rd_curve = [[0.25, rd * 100], [10, rd * 100]]
    rfs = forward_steps(rf_curve, 3.0, 100)
    rds = forward_steps(rd_curve, 3.0, 100)
    vb1, ve1 = tf_value_split(sp)                                   # 평탄 스칼라
    vb2, ve2 = tf_value_split(sp, rf_steps=rfs, rd_steps=rds)       # 평탄 forward 배열
    assert abs((vb1 + ve1) - (vb2 + ve2)) < 1e-6


# --------------------------------------------------------------------------- #
# 이관 동일성 (BLOCKING) — boost(1)
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize("fname", [
    "rcps_real_2022_context.json", "rcps_real_2023_context.json", "rcps_real_2024_context.json"])
def test_forward_steps_matches_legacy(fname):
    """★이관 동일성: curve_bootstrap.forward_steps == 레거시 공식(1e-12), rf·rd 커브 모두."""
    ctx = json.loads((FIX / fname).read_text(encoding="utf-8"))
    cur = ctx["curves"]
    t, n = 10.0, 120
    for key in ("risk_free_curve", "credit_curve"):
        a = forward_steps(cur[key], t, n)
        b = _legacy_forward(cur[key], t, n)
        assert a is not None and len(a) == n
        assert all(abs(x - y) < 1e-12 for x, y in zip(a, b)), f"{fname}/{key} 이관 불일치"


# --------------------------------------------------------------------------- #
# 외부 앵커 (BLOCKING) — E3a 후에도 ±2% 유지
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize("fname,fid", [
    ("rcps_real_2022_context.json", "rcps_real_2022_issue"),
    ("rcps_real_2023_context.json", "rcps_real_2023_ye"),
    ("rcps_real_2024_context.json", "rcps_real_2024_ye")])
def test_mobius_anchor_within_2pct_after_e3a(fname, fid):
    """★외부 앵커: 모비어스 RCPS total vs 삼도 ±2%(E3a 결선 후 유지)."""
    ctx = json.loads((FIX / fname).read_text(encoding="utf-8"))
    total = LIBRARY.calculate(ctx)["total_fair_value"]
    cases = json.loads(GOLD_REAL.read_text(encoding="utf-8"))["cases"]
    g = next(c for c in cases if c["fixture_id"] == fid)["expected"]["per_unit"]["total_fair_value"]
    err = (total - g) / g
    print(f"\n[앵커 {fid}] total={total:.0f} golden={g} ({err*100:+.2f}%)")
    assert abs(err) < 0.02, f"외부 앵커 ±2% 초과({err*100:+.2f}%) — 규명 필요"


# --------------------------------------------------------------------------- #
# bootstrap_table 구조 + 결과 필드
# --------------------------------------------------------------------------- #
def test_bootstrap_table_structure():
    """ytm/spot/forward 행·0.5y 격자·ytm==spot(zero해석)·forward 존재."""
    curve = [[0.5, 3.0], [1.0, 3.3], [3.0, 3.8], [5.0, 4.0]]
    tbl = bootstrap_table(curve, 3.0)
    for k in ("ytm", "spot", "forward", "grid", "assumption"):
        assert k in tbl
    assert tbl["grid"] == 0.5
    assert len(tbl["ytm"]) == len(tbl["spot"]) == len(tbl["forward"])
    # zero-해석: ytm행 == spot행(수치 동일, 해석만 다름)
    for (ta, ya), (ts, ys) in zip(tbl["ytm"], tbl["spot"]):
        assert abs(ta - ts) < 1e-9 and abs(ya - ys) < 1e-9
    # 상승커브 → 후반 forward > 전반 forward
    assert tbl["forward"][-1][1] > tbl["forward"][0][1]


def test_curve_bootstrap_block_and_result_field():
    """curve_bootstrap_block(rf/rd) + CB 결과 옵션 필드·rate_mode."""
    blk = curve_bootstrap_block([[1, 3.0], [3, 3.5]], [[1, 13.0], [3, 15.0]], 3.0)
    assert blk["rate_mode"] == "BOOTSTRAPPED_FORWARD" and "rf" in blk and "rd" in blk
    ctx = json.loads((FIX / "cb_case1_context.json").read_text(encoding="utf-8"))
    res = LIBRARY.calculate(ctx)
    assert res["curve_bootstrap"] is not None
    assert res["curve_bootstrap"]["rf"] is not None
    assert res["trees"]["tree_meta"]["rate_mode"] in ("BOOTSTRAPPED_FORWARD", "FLAT_FALLBACK")


def test_degenerate_curve_flat_fallback():
    """커브 없음/1점 퇴화 → forward None(평탄 폴백)."""
    assert forward_steps([], 3.0, 10) is None
    assert bootstrap_table([], 3.0) is None
    assert curve_bootstrap_block([], [], 3.0) is None
