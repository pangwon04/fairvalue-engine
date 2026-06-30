"""G4~G5 (RCPS): 12키 분해+합계, ★G5 BLOCKING 정답지 1% 이내."""
import json
from pathlib import Path

from app.models.rcps_calculator import calculate_rcps
from app.models.registry import LIBRARY
from app.result import PricingResult

FIX = Path(__file__).resolve().parent / "fixtures" / "rcps_real_2022_context.json"
GOLDEN = Path(__file__).resolve().parents[2] / "golden-values" / "rcps_real_cases.json"

SUM_KEYS = (
    "bond_value", "preferred_share_value", "conversion_option_value",
    "exchange_option_value", "warrant_value", "redemption_option_value",
    "issuer_call_value", "sale_claim_value", "stock_option_value",
    "conditional_option_value", "dilution_effect",
)
NEG_KEYS = ("issuer_call_value", "sale_claim_value", "dilution_effect")


def _ctx():
    return json.loads(FIX.read_text(encoding="utf-8"))


def _golden_issue():
    cases = json.loads(GOLDEN.read_text(encoding="utf-8"))["cases"]
    return next(c for c in cases if c["fixture_id"] == "rcps_real_2022_issue")["expected"]["per_unit"]


def test_g4_decomposition_and_sum_invariant():
    """G4: 12키·부호규칙·Σ=total(0.01). PricingResult Pydantic 검증."""
    res = calculate_rcps(_ctx())
    comp = res["components"]
    for k in (*SUM_KEYS, "total_fair_value"):
        assert k in comp
    for k in NEG_KEYS:
        assert (comp.get(k) or 0.0) <= 1e-9
    assert comp["preferred_share_value"] > 0 and comp["conversion_option_value"] > 0
    s = sum((comp.get(k) or 0.0) for k in SUM_KEYS)
    assert abs(s - comp["total_fair_value"]) <= 0.01
    pr = PricingResult.model_validate(res)
    assert pr.status == "DONE" and pr.instrument_type == "RCPS"


def test_g4_registry_routing():
    """ModelLibrary 가 instrument_type=RCPS 를 calculate_rcps 로 라우팅."""
    res = LIBRARY.calculate(_ctx())
    assert res["instrument_type"] == "RCPS"


def test_g5_blocking_answer_key_within_1pct():
    """★G5 BLOCKING: 발행일 정답지 total/host/embedded 재현.
    total 1% 이내, host ±2%, embedded 1% 이내 (C 모델: 측도 분리, q_conv=우선배당률)."""
    res = calculate_rcps(_ctx())
    c = res["components"]
    g = _golden_issue()
    total = res["total_fair_value"]
    host = c["preferred_share_value"]
    embedded = c["conversion_option_value"]
    print(f"\n[G5] total={total:.0f} (golden {g['total_fair_value']}, {(total-g['total_fair_value'])/g['total_fair_value']*100:+.2f}%)")
    print(f"     host={host:.0f} (golden {g['host']}, {(host-g['host'])/g['host']*100:+.2f}%)")
    print(f"     embedded={embedded:.0f} (golden {g['embedded_derivative']}, {(embedded-g['embedded_derivative'])/g['embedded_derivative']*100:+.2f}%)")
    assert abs(total - g["total_fair_value"]) / g["total_fair_value"] < 0.01, "total 1% 초과(BLOCKING)"
    assert abs(host - g["host"]) / g["host"] < 0.02, "host ±2% 초과"
    assert abs(embedded - g["embedded_derivative"]) / g["embedded_derivative"] < 0.01, "embedded 1% 초과"
    # host + embedded = total (보고서 2분할 정합)
    assert abs((host + embedded) - total) <= 0.01
