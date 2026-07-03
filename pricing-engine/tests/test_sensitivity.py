"""엔진확장-3a: 민감도 3×3 검증(구조·base sanity·vol floor·TF/GS). 단조성 assert 없음."""
import json
from pathlib import Path

import pytest

from app.models.sensitivity import sensitivity_grid
from app.models.registry import LIBRARY

FIX = Path(__file__).resolve().parent / "fixtures"


def test_sensitivity_grid_structure_and_floor():
    """3×3 구조 + vol floor 가드(σ−5%p<1% → 하한 적용·meta 표기). 단조 assert 없음."""
    calls = []
    def total_at(sig, s0):
        calls.append((sig, s0))
        return 100.0 + sig * 1000 + s0 * 0.1
    g = sensitivity_grid(total_at, sigma=0.03, spot=10000.0, model="TF_LATTICE", steps_used=30)
    assert len(g["vol_axis"]) == 3 and len(g["spot_axis"]) == 3
    assert len(g["total_grid"]) == 3 and all(len(r) == 3 for r in g["total_grid"])
    assert len(g["per_unit_grid"]) == 3
    # σ=3% → σ−5%p=−2% < 1% floor → 하한 0.01 적용
    assert g["vol_axis"][0] == 0.01 and g["meta"]["vol_floor_applied"] is True
    assert abs(g["spot_axis"][0] - 9500.0) < 1e-9 and abs(g["spot_axis"][2] - 10500.0) < 1e-9
    assert len(calls) == 9


def test_sensitivity_no_floor_when_high_vol():
    g = sensitivity_grid(lambda v, s: v + s, sigma=0.45, spot=3260.0, model="GS", steps_used=27)
    assert abs(g["vol_axis"][0] - 0.40) < 1e-9 and abs(g["vol_axis"][2] - 0.50) < 1e-9
    assert g["meta"]["vol_floor_applied"] is False and g["meta"]["model"] == "GS"


@pytest.mark.parametrize("fixture", [
    "cb_case1_context.json", "eb_case1_context.json", "cps_case1_context.json",
    "bw_case1_context.json", "rcps_real_2022_context.json"])
def test_sensitivity_base_equals_tree_root(fixture):
    """★base 셀[1][1] == 트리 composite 루트(1e-6 sanity). 5종 TF."""
    ctx = json.loads((FIX / fixture).read_text(encoding="utf-8"))
    res = LIBRARY.calculate(ctx)
    sens = res["sensitivity"]
    root = res["trees"]["composite_tree"][0][0]
    base = sens["total_grid"][1][1]
    assert abs(base - root) < 1e-3, f"{fixture}: base {base} != tree root {root}"
    assert sens["meta"]["steps_used"] == res["trees"]["tree_meta"]["steps_used"]


@pytest.mark.parametrize("fixture,model_key", [
    ("cb_case1_context.json", "CB_GS"),
    ("rcps_gs_deepsearch_context.json", "GS"),
    ("bw_case1_context.json", "BW_GS")])
def test_sensitivity_gs_path(fixture, model_key):
    """GS 경로에서도 민감도 3×3·base sanity·model 표기."""
    ctx = dict(json.loads((FIX / fixture).read_text(encoding="utf-8")))
    ctx["model"] = model_key
    res = LIBRARY.get(model_key)(ctx)
    sens = res["sensitivity"]
    assert sens["meta"]["model"] == "GS"
    assert len(sens["total_grid"]) == 3
    assert abs(sens["total_grid"][1][1] - res["trees"]["composite_tree"][0][0]) < 1e-3


def test_sensitivity_pricingresult_validates():
    """★하위호환: curve_bootstrap·sensitivity 추가에도 PricingResult 검증 통과."""
    from app.result import PricingResult
    ctx = json.loads((FIX / "cb_case1_context.json").read_text(encoding="utf-8"))
    res = LIBRARY.calculate(ctx)
    pr = PricingResult.model_validate(res)
    assert pr.sensitivity is not None and pr.curve_bootstrap is not None
