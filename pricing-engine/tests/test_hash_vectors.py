"""TV1~TV5: reproducer.input_hash(input) == expected_hash 교차 동결 검증."""
import json
from pathlib import Path

import pytest

from app.reproducer import input_hash

REPO_ROOT = Path(__file__).resolve().parents[2]
VECTORS = json.loads(
    (REPO_ROOT / "shared" / "schemas" / "hash-test-vectors.json").read_text(encoding="utf-8")
)["vectors"]


@pytest.mark.parametrize("case", VECTORS, ids=[c["name"] for c in VECTORS])
def test_input_hash_matches_expected(case):
    got = input_hash(case["input"])
    assert got == case["expected_hash"], (
        f"{case['name']}: got {got} != expected {case['expected_hash']}"
    )


def test_all_five_vectors_present():
    names = {c["name"] for c in VECTORS}
    assert len(VECTORS) == 5
    assert names == {
        "TV1_CB_basic", "TV2_CB_refixing", "TV3_RCPS",
        "TV4_SO_maturity_null", "TV5_CSO_tranche",
    }
