"""EB(교환사채) G1~G5 + MC 교차검증 + CB 정합성 + silent-bug 게이트.

★ 외부 실보고서 정답지 없음. 검증은:
  - self-consistency(Σ=total·부호) + 골든(eb_case1, 회귀 앵커)
  - lattice-vs-MC 교차검증(target_asset GBM)
  - ★CB 정합성: EB(교환대상=자사주+희석off) == CB(전환) → EB=CB−자사주제약
  - ★silent-bug: target vol 바꾸면 exchange_option_value만 변하고 bond_value 불변
cb_case1 과 동일하게 절대값은 self-consistency 로만 보증(외부 미검증).
"""
import copy
import json
import math
from pathlib import Path

from app.models.eb_calculator import calculate_eb
from app.models.cb_calculator import calculate_cb
from app.models.registry import LIBRARY
from app.models.mc import european_call_mc
from app.models.tf_lattice import CBLatticeSpec, crr_params, european_call_crr, tf_value
from app.result import PricingResult

FIXDIR = Path(__file__).resolve().parent / "fixtures"
GOLDEN = Path(__file__).resolve().parents[2] / "golden-values" / "eb_case1.json"

SUM_KEYS = (
    "bond_value", "preferred_share_value", "conversion_option_value",
    "exchange_option_value", "warrant_value", "redemption_option_value",
    "issuer_call_value", "sale_claim_value", "stock_option_value",
    "conditional_option_value", "dilution_effect",
)
NEG_KEYS = ("issuer_call_value", "sale_claim_value", "dilution_effect")


def _ctx():
    return json.loads((FIXDIR / "eb_case1_context.json").read_text(encoding="utf-8"))


def _golden():
    return json.loads(GOLDEN.read_text(encoding="utf-8"))["expected"]["per_unit"]


# --- G1: 격자계수(σ=target vol) ------------------------------------------
def test_g1_lattice_coefficients_target_vol():
    """G1: u=exp(σ√Δt), σ=target_asset 변동성(0.40). t=4y/steps=300."""
    dt = 4.0 / 300
    u, d, p = crr_params(0.40, dt, 0.03, 0.01)
    assert abs(u - math.exp(0.40 * math.sqrt(dt))) < 1e-12
    assert abs(d - 1.0 / u) < 1e-12


# --- G2: 1스텝 roll-back --------------------------------------------------
def test_g2_one_step_rollback():
    """G2: 1스텝 순수채권(교환 off) = e^{-rd*dt}*(face+cpn) 손계산 일치."""
    rd = 0.08
    v = tf_value(CBLatticeSpec(
        s0=5500.0, sigma=0.40, t_years=1.0, steps=1, rf=0.03, rd=rd, q=0.01,
        face=10000.0, coupon_per_year=300.0, freq=1, conv_enabled=False,
    ))
    assert abs(v - math.exp(-rd * 1.0) * (10000.0 + 300.0)) < 1e-6


# --- G3: bond host = CB 채권 로직 -----------------------------------------
def test_g3_bond_host_equals_cb_logic():
    """G3: EB bond_value(교환 off, 발행사 채권 @rd)가 CB 순수채권 로직·손계산과 일치."""
    res = calculate_eb(_ctx())
    bond = res["components"]["bond_value"]
    # 손계산: 반년 쿠폰(150) 8회 @rd=8% + face @rd. t=4y, freq=2.
    rd = 0.08
    cpn = 300.0 / 2  # 반기 쿠폰
    closed = 10000.0 * math.exp(-rd * 4.0) + sum(cpn * math.exp(-rd * (k / 2)) for k in range(1, 9))
    assert abs(bond - closed) / closed < 5e-3, f"bond={bond} closed={closed}"


# --- G4: 12키 분해 --------------------------------------------------------
def test_g4_decomposition_and_sum_invariant():
    """G4: bond+exchange+redemption 채움, conversion=0·dilution=0·나머지 0. 부호·Σ=total."""
    res = calculate_eb(_ctx())
    comp = res["components"]
    for k in (*SUM_KEYS, "total_fair_value"):
        assert k in comp
    for k in NEG_KEYS:
        assert (comp.get(k) or 0.0) <= 1e-9
    assert comp["bond_value"] > 0 and comp["exchange_option_value"] > 0
    assert comp["conversion_option_value"] == 0.0   # ★ EB: 전환 아님
    assert comp["dilution_effect"] == 0.0           # ★ EB: 희석 없음
    assert (comp.get("preferred_share_value") or 0.0) == 0.0
    s = sum((comp.get(k) or 0.0) for k in SUM_KEYS)
    assert abs(s - comp["total_fair_value"]) <= 0.01
    pr = PricingResult.model_validate(res)
    assert pr.status == "DONE" and pr.instrument_type == "EB"


# --- G5: 골든 self-consistency -------------------------------------------
def test_g5_golden_self_consistency():
    """G5: eb_case1 골든 total/bond/exchange/redemption 회귀 앵커 1% 이내(외부 미검증)."""
    res = calculate_eb(_ctx())
    c = res["components"]
    g = _golden()
    for key, val in (("total_fair_value", res["total_fair_value"]),
                     ("bond_value", c["bond_value"]),
                     ("exchange_option_value", c["exchange_option_value"]),
                     ("redemption_option_value", c["redemption_option_value"])):
        assert abs(val - g[key]) / g[key] < 0.01, f"{key}: {val} vs {g[key]}"


# --- 교차검증: 교환옵션(target GBM) 격자 vs MC ---------------------------
def test_crosscheck_lattice_vs_mc_target():
    """교환옵션 조각(target European call CRR) vs MC 1~2% 수렴(target GBM)."""
    crr = european_call_crr(5500.0, 5000.0, 0.40, 4.0, 0.03, 0.01, 600)
    mc = european_call_mc(5500.0, 5000.0, 0.40, 4.0, 0.03, 0.01, paths=200_000, seed=20240101)
    assert abs(crr - mc) / mc < 0.02, f"CRR={crr} MC={mc}"


# --- ★ silent-bug: target vol → exchange만, bond 불변 --------------------
def test_silent_bug_target_vol_moves_only_exchange():
    """target vol 40→60: exchange_option_value 변하고 bond_value 불변(교환옵션이 target 파라미터 사용)."""
    base = calculate_eb(_ctx())["components"]
    hi = copy.deepcopy(_ctx())
    hi["rights"]["exchange"]["target_market"]["volatility"] = 60.0
    hic = calculate_eb(hi)["components"]
    assert abs(base["bond_value"] - hic["bond_value"]) < 1e-6, "bond_value 는 target vol 무관"
    assert hic["exchange_option_value"] > base["exchange_option_value"] + 1.0, "exchange 는 target vol 증가 시 상승"


# --- ★ CB 정합성: EB(자사주+희석off) ≡ CB(전환) --------------------------
def _shared_ctx():
    terms = {"issue_date": "2023-06-26", "maturity_date": "2028-06-26",
             "face_value": 10000, "coupon_rate": 3.0, "coupon_freq_month": 6}
    mkt = {"spot": 3260, "volatility": 45.0, "dividend_yield": 0.0}
    curves = {"risk_free_curve": [[0.25, 3.0], [1, 3.0], [3, 3.0], [5, 3.0]],
              "credit_curve": [[0.25, 8.0], [1, 8.0], [3, 8.0], [5, 8.0]]}
    put = {"put": {"enabled": True, "yield": 5.0, "start": "2026-06-26"}}
    cb = {"instrument_type": "CB", "valuation_date": "2024-06-26", "instrument_id": 1, "job_id": 1,
          "model": "TF_LATTICE", "model_version": "cb-1.0.0", "seed": 1,
          "terms": terms, "market": mkt, "curves": curves, "options": {"lattice_steps": 300},
          "rights": {"conversion": {"strike": 3260, "ratio": 1, "start": "2024-09-26"},
                     "redemption": put, "dilution": {"enabled": False}}}
    eb = {"instrument_type": "EB", "valuation_date": "2024-06-26", "instrument_id": 2, "job_id": 1,
          "model": "TF_LATTICE", "model_version": "eb-1.0.0", "seed": 1,
          "terms": terms, "market": mkt, "curves": curves, "options": {"lattice_steps": 300},
          "rights": {"exchange": {"target_asset_id": 0, "ratio": 1, "strike": 3260, "start": "2024-09-26",
                                  "target_market": mkt},
                     "redemption": put}}
    return cb, eb


def test_cb_consistency_eb_equals_cb_minus_own_stock():
    """★ EB(교환대상=자사주 파라미터, 희석 off) == CB(전환): 기초자산 같으면 교환≡전환.
    EB=CB−자사주제약 자기검증(이미 검증된 CB 에 EB 를 묶어 신뢰 확보)."""
    cb_ctx, eb_ctx = _shared_ctx()
    cb = calculate_cb(cb_ctx)
    eb = calculate_eb(eb_ctx)
    cc, ec = cb["components"], eb["components"]
    assert abs(cb["total_fair_value"] - eb["total_fair_value"]) < 1e-4, "total 일치"
    assert abs(cc["bond_value"] - ec["bond_value"]) < 1e-4, "bond 일치"
    assert abs(cc["conversion_option_value"] - ec["exchange_option_value"]) < 1e-4, "전환≡교환"
    assert abs(cc["redemption_option_value"] - ec["redemption_option_value"]) < 1e-4, "상환 일치"


# --- registry 라우팅 -----------------------------------------------------
def test_registry_routing_eb():
    """ModelLibrary 가 instrument_type=EB 를 calculate_eb 로 라우팅."""
    res = LIBRARY.calculate(_ctx())
    assert res["instrument_type"] == "EB"
