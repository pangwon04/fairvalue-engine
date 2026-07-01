"""CPS(전환우선주) G1~G5 + MC 교차검증 + RCPS 정합성 + host_type.

★ 외부 실보고서 정답지 없음(RCPS 만 있었음). 검증은:
  - self-consistency(Σ=total·부호규칙) + 골든(cps_case1, 회귀 앵커)
  - lattice-vs-MC 교차검증(전환옵션 조각)
  - RCPS 정합성: CPS=RCPS−상환권 → 같은 입력에서 calculate_cps(dated) == calculate_rcps
  - host_type: perpetual host > dated host(프리미엄 우선주, 영구배당 > 유한배당+par)
cb_case1 과 동일하게 절대값은 self-consistency 로만 보증(외부 미검증).
"""
import copy
import json
import math
from pathlib import Path

import pytest

from app.models.cps_calculator import calculate_cps, PERP_HORIZON_YEARS
from app.models.rcps_calculator import calculate_rcps
from app.models.registry import LIBRARY
from app.models.mc import european_call_mc
from app.models.tf_lattice import CBLatticeSpec, crr_params, european_call_crr, tf_value_split
from app.result import PricingResult

FIXDIR = Path(__file__).resolve().parent / "fixtures"
GOLDEN = Path(__file__).resolve().parents[2] / "golden-values" / "cps_case1.json"

SUM_KEYS = (
    "bond_value", "preferred_share_value", "conversion_option_value",
    "exchange_option_value", "warrant_value", "redemption_option_value",
    "issuer_call_value", "sale_claim_value", "stock_option_value",
    "conditional_option_value", "dilution_effect",
)
NEG_KEYS = ("issuer_call_value", "sale_claim_value", "dilution_effect")


def _dated():
    return json.loads((FIXDIR / "cps_case1_context.json").read_text(encoding="utf-8"))


def _perp():
    return json.loads((FIXDIR / "cps_perpetual_context.json").read_text(encoding="utf-8"))


def _golden():
    return json.loads(GOLDEN.read_text(encoding="utf-8"))["expected"]["per_unit"]


# --- G1: 격자계수 ---------------------------------------------------------
def test_g1_lattice_coefficients():
    """G1: dated Δt=5/60=1/12 -> u=exp(σ√Δt), d=1/u, p 공식 일치."""
    dt = 5.0 / 60
    u, d, p = crr_params(0.35, dt, 0.03, 0.0)
    assert abs(u - math.exp(0.35 * math.sqrt(dt))) < 1e-12
    assert abs(d - 1.0 / u) < 1e-12
    assert abs(p - (math.exp((0.03 - 0.0) * dt) - d) / (u - d)) < 1e-12


# --- G2: 1스텝 roll-back --------------------------------------------------
def test_g2_one_step_rollback():
    """G2: 1스텝 우선주(전환 off) Vb = e^{-rd*dt}*(face+cpn) 손계산 일치."""
    rd = 0.05
    spec = CBLatticeSpec(
        s0=11000.0, sigma=0.35, t_years=1.0, steps=1, rf=0.03, rd=rd, q=0.0,
        face=10000.0, coupon_per_year=800.0, freq=1, conv_enabled=False,
    )
    vb, ve = tf_value_split(spec)
    assert abs(vb - math.exp(-rd * 1.0) * (10000.0 + 800.0)) < 1e-6
    assert ve == 0.0


# --- G3: host 손계산 (dated / perpetual) ----------------------------------
def _pure_pref_bond(face, div, rd, T, steps):
    """전환 off 순수 우선주 채권 = Σ_{k=1..T} div·e^{-rd·k} + face·e^{-rd·T} (결정론적)."""
    spec = CBLatticeSpec(
        s0=11000.0, sigma=0.35, t_years=T, steps=steps, rf=0.03, rd=rd, q=0.0,
        face=face, coupon_per_year=div, freq=1, conv_enabled=False,
    )
    vb, _ = tf_value_split(spec)
    closed = face * math.exp(-rd * T) + sum(div * math.exp(-rd * k) for k in range(1, int(round(T)) + 1))
    return vb, closed


def test_g3_host_dated_hand_calc():
    """G3(dated): host 채권성분(전환 off) = par 상환 + 배당 PV 손계산 일치."""
    vb, closed = _pure_pref_bond(face=10000.0, div=800.0, rd=0.05, T=5.0, steps=60)
    assert abs(vb - closed) / closed < 1e-3, f"vb={vb} closed={closed}"


def test_g3_host_perpetual_hand_calc():
    """G3(perpetual): host 채권성분(전환 off) = div/rd 잔여 + 50년 배당 PV 손계산 일치.
    격자 근사(horizon 50, 만기노드 div/rd)가 절단 Gordon 과 일치."""
    rd = 0.05
    face = 800.0 / rd  # div/rd = 16000
    vb, closed = _pure_pref_bond(face=face, div=800.0, rd=rd, T=PERP_HORIZON_YEARS, steps=200)
    assert abs(vb - closed) / closed < 1e-3, f"vb={vb} closed={closed}"


# --- G4: 12키 분해 + Σ=total ---------------------------------------------
@pytest.mark.parametrize("ctx_fn", [_dated, _perp])
def test_g4_decomposition_and_sum_invariant(ctx_fn):
    """G4: preferred+conversion 채움, 나머지 0, 부호규칙, Σ=total(0.01). PricingResult 검증."""
    res = calculate_cps(ctx_fn())
    comp = res["components"]
    for k in (*SUM_KEYS, "total_fair_value"):
        assert k in comp
    for k in NEG_KEYS:
        assert (comp.get(k) or 0.0) <= 1e-9
    # CPS 는 preferred + conversion 만 양수, redemption/기타 0
    assert comp["preferred_share_value"] > 0 and comp["conversion_option_value"] > 0
    assert comp["redemption_option_value"] == 0.0
    assert (comp.get("bond_value") or 0.0) == 0.0
    s = sum((comp.get(k) or 0.0) for k in SUM_KEYS)
    assert abs(s - comp["total_fair_value"]) <= 0.01
    pr = PricingResult.model_validate(res)
    assert pr.status == "DONE" and pr.instrument_type == "CPS"


# --- G5: 골든 self-consistency (회귀 앵커) --------------------------------
def test_g5_golden_self_consistency():
    """G5: cps_case1(dated) 골든 total/host/embedded 회귀 앵커 1% 이내(외부 미검증)."""
    res = calculate_cps(_dated())
    c = res["components"]
    g = _golden()
    total, host, emb = res["total_fair_value"], c["preferred_share_value"], c["conversion_option_value"]
    assert abs(total - g["total_fair_value"]) / g["total_fair_value"] < 0.01
    assert abs(host - g["host"]) / g["host"] < 0.01
    assert abs(emb - g["embedded_derivative"]) / g["embedded_derivative"] < 0.01
    assert abs((host + emb) - total) <= 0.01


# --- 교차검증: 전환옵션 격자 vs MC ---------------------------------------
def test_crosscheck_lattice_vs_mc():
    """전환옵션 조각(European call CRR) vs MC 1~2% 수렴."""
    crr = european_call_crr(11000.0, 10000.0, 0.35, 5.0, 0.03, 0.08, 600)
    mc = european_call_mc(11000.0, 10000.0, 0.35, 5.0, 0.03, 0.08, paths=200_000, seed=20240101)
    assert abs(crr - mc) / mc < 0.02, f"CRR={crr} MC={mc}"


# --- ★ RCPS 정합성: CPS=RCPS−상환권 --------------------------------------
def test_rcps_consistency_cps_equals_rcps_minus_redemption():
    """같은 dated 입력에서 CPS 와 RCPS(상환권 자유행사 미반영 C 모델)가 host·전환에서 일치.
    CPS=RCPS−상환권 자기검증(우리 RCPS C 모델이 일반적임을 확인)."""
    cps = calculate_cps(_dated())
    rctx = copy.deepcopy(_dated())
    rctx["instrument_type"] = "RCPS"
    rcps = calculate_rcps(rctx)
    cc, rc = cps["components"], rcps["components"]
    assert abs(cps["total_fair_value"] - rcps["total_fair_value"]) < 1e-6
    assert abs(cc["preferred_share_value"] - rc["preferred_share_value"]) < 1e-6
    assert abs(cc["conversion_option_value"] - rc["conversion_option_value"]) < 1e-6


# --- ★ host_type: perpetual > dated --------------------------------------
def test_host_type_perpetual_gt_dated():
    """프리미엄 우선주(배당 8% > rd 5%): 영구형 host > 만기형 host(영구배당 > 유한배당+par)."""
    dated_host = calculate_cps(_dated())["components"]["preferred_share_value"]
    perp_host = calculate_cps(_perp())["components"]["preferred_share_value"]
    print(f"\n[host_type] perpetual host={perp_host:.0f} > dated host={dated_host:.0f}")
    assert perp_host > dated_host


# --- registry 라우팅 -----------------------------------------------------
def test_registry_routing_cps():
    """ModelLibrary 가 instrument_type=CPS 를 calculate_cps 로 라우팅."""
    res = LIBRARY.calculate(_dated())
    assert res["instrument_type"] == "CPS"
