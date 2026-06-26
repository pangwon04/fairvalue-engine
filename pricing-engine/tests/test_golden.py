"""golden fixture 합계 불변식(F-1) + 부호 규칙(F-8) 검증."""
import json
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
GOLDEN_DIR = REPO_ROOT / "golden-values"

# total 을 제외한 합산 대상 component key
SUM_KEYS = (
    "bond_value", "preferred_share_value", "conversion_option_value",
    "exchange_option_value", "warrant_value", "redemption_option_value",
    "issuer_call_value", "sale_claim_value", "stock_option_value",
    "conditional_option_value", "dilution_effect",
)
NEGATIVE_KEYS = ("issuer_call_value", "sale_claim_value", "dilution_effect")

FIXTURES = ["cb_case1", "rcps_case1"]


def _load(name):
    return json.loads((GOLDEN_DIR / f"{name}.json").read_text(encoding="utf-8"))


@pytest.mark.parametrize("name", FIXTURES)
def test_sum_components_equals_total(name):
    g = _load(name)
    comp = g["expected"]["components"]
    tol = g["tolerance"]["absolute"]
    s = sum((comp.get(k) or 0.0) for k in SUM_KEYS)
    total = comp["total_fair_value"]
    assert abs(s - total) <= tol, f"{name}: Σ={s} != total={total} (tol {tol})"
    # top-level total 과도 정합
    assert abs(g["expected"]["total_fair_value"] - total) <= tol


@pytest.mark.parametrize("name", FIXTURES)
def test_sign_rules(name):
    comp = _load(name)["expected"]["components"]
    for k in NEGATIVE_KEYS:
        v = comp.get(k)
        if v is not None:
            assert v <= 0, f"{name}: {k}={v} 는 0 이하여야 함(§4.10)"
