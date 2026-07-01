"""BW(신주인수권부사채) G1~G5 + MC + 분리/비분리 게이트 + 희석 게이트 + CB 정합성.

★ 외부 실보고서 없음. self-consistency + 골든(bw_case1) + MC(자사주 GBM) +
  분리≠비분리 + 희석(new_shares↑→dilution↓) + CB 정합성(비분리·K=0·df=1 ≡ CB).
회계근거: 2.1.4.3(분리형=별도 금융상품), 3.4.1.4(페이오프 희석반영). docs/KIFRS_분리판정_규칙.md.
"""
import copy
import json
import math
from pathlib import Path

from app.models.bw_calculator import calculate_bw, _bw_composite, _standalone_call
from app.models.cb_calculator import calculate_cb
from app.models.registry import LIBRARY
from app.models.mc import european_call_mc
from app.models.tf_lattice import CBLatticeSpec, crr_params, european_call_crr, tf_value
from app.result import PricingResult

FIXDIR = Path(__file__).resolve().parent / "fixtures"
GOLDEN = Path(__file__).resolve().parents[2] / "golden-values" / "bw_case1.json"

SUM_KEYS = (
    "bond_value", "preferred_share_value", "conversion_option_value",
    "exchange_option_value", "warrant_value", "redemption_option_value",
    "issuer_call_value", "sale_claim_value", "stock_option_value",
    "conditional_option_value", "dilution_effect",
)


def _sep():
    return json.loads((FIXDIR / "bw_case1_context.json").read_text(encoding="utf-8"))


def _nonsep():
    return json.loads((FIXDIR / "bw_nonseparable_case.json").read_text(encoding="utf-8"))


def _golden():
    return json.loads(GOLDEN.read_text(encoding="utf-8"))["expected"]["per_unit"]


# --- G1/G2 ---------------------------------------------------------------
def test_g1_lattice_coefficients():
    dt = 4.0 / 300
    u, d, p = crr_params(0.40, dt, 0.03, 0.01)
    assert abs(u - math.exp(0.40 * math.sqrt(dt))) < 1e-12
    assert abs(d - 1.0 / u) < 1e-12


def test_g2_one_step_rollback():
    """G2: 1스텝 순수채권(워런트 off) = e^{-rd*dt}*(face+cpn)."""
    rd = 0.08
    v = tf_value(CBLatticeSpec(
        s0=11000.0, sigma=0.40, t_years=1.0, steps=1, rf=0.03, rd=rd, q=0.01,
        face=10000.0, coupon_per_year=300.0, freq=1, conv_enabled=False))
    assert abs(v - math.exp(-rd * 1.0) * (10000.0 + 300.0)) < 1e-6


# --- G3: bond host = CB 채권 ----------------------------------------------
def test_g3_bond_host_equals_cb_logic():
    bond = calculate_bw(_sep())["components"]["bond_value"]
    rd = 0.08
    cpn = 300.0 / 2
    closed = 10000.0 * math.exp(-rd * 4.0) + sum(cpn * math.exp(-rd * (k / 2)) for k in range(1, 9))
    assert abs(bond - closed) / closed < 5e-3, f"bond={bond} closed={closed}"


# --- G4: 분해 -------------------------------------------------------------
def test_g4_decomposition_and_sum_invariant():
    for ctx in (_sep(), _nonsep()):
        res = calculate_bw(ctx)
        comp = res["components"]
        for k in (*SUM_KEYS, "total_fair_value"):
            assert k in comp
        assert comp["bond_value"] > 0 and comp["warrant_value"] > 0
        assert comp["redemption_option_value"] >= 0
        assert comp["dilution_effect"] <= 1e-9        # ★ 희석 음수(또는 0)
        assert comp["conversion_option_value"] == 0.0
        assert comp["exchange_option_value"] in (None, 0.0)
        s = sum((comp.get(k) or 0.0) for k in SUM_KEYS)
        assert abs(s - comp["total_fair_value"]) <= 0.01
        pr = PricingResult.model_validate(res)
        assert pr.status == "DONE" and pr.instrument_type == "BW"


# --- G5: 골든 self-consistency -------------------------------------------
def test_g5_golden_self_consistency():
    res = calculate_bw(_sep())
    c = res["components"]
    g = _golden()
    for key, val in (("total_fair_value", res["total_fair_value"]),
                     ("bond_value", c["bond_value"]),
                     ("warrant_value", c["warrant_value"]),
                     ("redemption_option_value", c["redemption_option_value"]),
                     ("dilution_effect", c["dilution_effect"])):
        assert abs(val - g[key]) <= max(0.02, abs(g[key]) * 0.01), f"{key}: {val} vs {g[key]}"


# --- 교차검증: warrant 콜 vs MC ------------------------------------------
def test_crosscheck_lattice_vs_mc():
    crr = european_call_crr(11000.0, 10000.0, 0.40, 4.0, 0.03, 0.01, 600)
    mc = european_call_mc(11000.0, 10000.0, 0.40, 4.0, 0.03, 0.01, paths=200_000, seed=20240101)
    assert abs(crr - mc) / mc < 0.02, f"CRR={crr} MC={mc}"


# --- ★ 분리/비분리 게이트: 결과가 달라야 함 ------------------------------
def test_separable_vs_nonseparable_differ():
    """separable=true/false 가 같은 입력에서 warrant_value·total 다름(얽힘 여부 반영, silent bug 방지)."""
    sep = calculate_bw(_sep())
    non = calculate_bw(_nonsep())
    assert abs(sep["components"]["warrant_value"] - non["components"]["warrant_value"]) > 1.0
    assert abs(sep["total_fair_value"] - non["total_fair_value"]) > 1.0


# --- ★ 희석 게이트: new_shares↑ → dilution 더 음수, off→0 -----------------
def test_dilution_gate_monotone():
    def dil(ns, enabled=True):
        c = copy.deepcopy(_sep())
        c["rights"]["dilution"] = {"enabled": enabled, "new_shares": ns}
        return calculate_bw(c)["components"]["dilution_effect"]
    d0 = dil(0, enabled=False)
    d2 = dil(2_000_000)
    d5 = dil(5_000_000)
    assert d0 == 0.0                     # 희석 off → 0
    assert d2 < 0 and d5 < 0             # 음수
    assert d5 < d2                       # new_shares↑ → 더 음수(희석 큼)


# --- ★ CB 정합성: 비분리·K=0·df=1 ≡ CB(전환) -----------------------------
def _cb_and_bw0():
    st = {"issue_date": "2023-06-26", "maturity_date": "2028-06-26", "face_value": 10000,
          "coupon_rate": 3.0, "coupon_freq_month": 6}
    mkt = {"spot": 11000, "volatility": 40.0, "dividend_yield": 1.0, "shares_outstanding": 10000000}
    cv = {"risk_free_curve": [[0.25, 3.0], [1, 3.0], [3, 3.0], [5, 3.0]],
          "credit_curve": [[0.25, 8.0], [1, 8.0], [3, 8.0], [5, 8.0]]}
    cb = {"instrument_type": "CB", "valuation_date": "2024-06-26", "instrument_id": 1, "job_id": 1,
          "model": "TF_LATTICE", "model_version": "cb", "seed": 1, "terms": st, "market": mkt,
          "curves": cv, "options": {"lattice_steps": 300},
          "rights": {"conversion": {"strike": 10000, "ratio": 1, "start": "2024-09-26"},
                     "dilution": {"enabled": False}}}
    bw0 = {"instrument_type": "BW", "valuation_date": "2024-06-26", "instrument_id": 2, "job_id": 1,
           "model": "TF_LATTICE", "model_version": "bw", "seed": 1, "terms": st, "market": mkt,
           "curves": cv, "options": {"lattice_steps": 300},
           "rights": {"warrant": {"strike": 0, "quantity": 1, "separable": False,
                                  "start": "2024-09-26", "end": "2028-06-26"},
                      "dilution": {"enabled": False}}}
    return cb, bw0


def test_cb_consistency_nonsep_k0_equals_cb():
    """★ BW(비분리, strike=0, df=1) 의 복합격자 = CB 전환(사채상계, 현금납입 0)와 동일.
    tf_value 미러라 total·warrant 가 CB total·conversion 과 1e-2 일치."""
    cb_ctx, bw0 = _cb_and_bw0()
    cb = calculate_cb(cb_ctx)
    bw = calculate_bw(bw0)
    assert abs(bw["total_fair_value"] - cb["total_fair_value"]) < 1e-2
    assert abs(bw["components"]["warrant_value"] - cb["components"]["conversion_option_value"]) < 1e-2


def test_cb_consistency_strike_is_cash_payment():
    """★ 납입구조: warrant strike↑ → total 단조 감소(CB 대비 현금납입 shares·K 만큼 낮음).
    K=0 이면 CB 와 동일, K>0 이면 그 현금납입만큼 낮아짐(납입구조 차이로 설명)."""
    _, bw0 = _cb_and_bw0()
    vals = []
    for K in (0, 3000, 10000):
        c = copy.deepcopy(bw0)
        c["rights"]["warrant"]["strike"] = K
        vals.append(calculate_bw(c)["total_fair_value"])
    assert vals[0] > vals[1] > vals[2]   # 현금납입 strike↑ → 가치↓


# --- registry 라우팅 -----------------------------------------------------
def test_registry_routing_bw():
    res = LIBRARY.calculate(_sep())
    assert res["instrument_type"] == "BW"
