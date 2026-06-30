"""Phase 4-α 엔진 결선: resolve 된 커브 스냅샷이 rf/rd 로 흘러들어 CB 평가를 좌우하는지.

resolver(Kotlin)가 채우는 것과 동일한 ValuationContext 모양(curves.risk_free_curve/credit_curve)을
직접 만들어, pricing-engine 의 _rates_from_curves() 결선점이 커브를 실제로 소비하는지 검증한다.
"""
import json
from pathlib import Path

from app.models.cb_calculator import _interp_curve_at, _rates_from_curves, calculate_cb

FIX = Path(__file__).resolve().parent / "fixtures" / "cb_case1_context.json"


def _ctx_with_curves(rf_curve, cr_curve):
    c = json.loads(FIX.read_text(encoding="utf-8"))
    # resolve 결과처럼 *_curve 포인트 스냅샷을 채운다(*_ref 제거).
    c["curves"] = {
        "risk_free_curve": rf_curve,
        "credit_curve": cr_curve,
        "curve_source": "test",
        "curve_version": "v1",
        "interpolation_method": "LINEAR",
    }
    return c


def test_interp_curve_linear_and_flat():
    curve = [[1.0, 3.0], [3.0, 4.0]]
    assert abs(_interp_curve_at(curve, 2.0) - 3.5) < 1e-12      # 선형 보간
    assert abs(_interp_curve_at(curve, 0.1) - 3.0) < 1e-12      # 평탄외삽(아래)
    assert abs(_interp_curve_at(curve, 10.0) - 4.0) < 1e-12     # 평탄외삽(위)


def test_rates_derived_from_curve_snapshot():
    ctx = _ctx_with_curves([[0.25, 3.41], [1, 3.35], [3, 3.30]], [[0.25, 13.45], [1, 14.10], [3, 15.02]])
    rf, rd = _rates_from_curves(ctx, t_years=2.2)
    # 2.2y 보간: rf ~3.30%대, rd ~14~15%대 (커브에서 산출, 평탄근사 아님)
    assert 0.032 < rf < 0.034
    assert 0.14 < rd < 0.151
    assert rd > rf  # 신용 가산


def test_curve_drives_key_parameters():
    """커브 스냅샷에서 산출한 rf/rd 가 key_parameters 에 그대로 기록된다(직접 결선 증명)."""
    low = calculate_cb(_ctx_with_curves([[1, 2.0], [3, 2.0]], [[1, 8.0], [3, 8.0]]))["key_parameters"]
    high = calculate_cb(_ctx_with_curves([[1, 5.0], [3, 5.0]], [[1, 20.0], [3, 20.0]]))["key_parameters"]
    assert abs(low["risk_free_rate"] - 2.0) < 0.01 and abs(low["discount_rate"] - 8.0) < 0.01
    assert abs(high["risk_free_rate"] - 5.0) < 0.01 and abs(high["discount_rate"] - 20.0) < 0.01
    assert abs(high["credit_spread"] - 15.0) < 0.01  # rd-rf = 20-5


def test_curve_changes_bond_floor_value():
    """채권 floor 가 지배하는 깊은 OTM 전환(전환가 매우 높음)에서는 rd(커브)가 total 을 좌우한다."""
    def deep_otm(rf_c, cr_c):
        c = _ctx_with_curves(rf_c, cr_c)
        c["rights"]["conversion"]["strike"] = 1_000_000  # 전환 사실상 무가치 → bond floor 지배
        return c
    t_low_rd = calculate_cb(deep_otm([[1, 3.0], [3, 3.0]], [[1, 8.0], [3, 8.0]]))["total_fair_value"]
    t_high_rd = calculate_cb(deep_otm([[1, 3.0], [3, 3.0]], [[1, 20.0], [3, 20.0]]))["total_fair_value"]
    # rd 높을수록 채권 현가가 낮아짐 → total 작아야 함
    assert t_high_rd < t_low_rd - 1.0, f"커브 rd 가 채권가치에 반영 안 됨: {t_low_rd} vs {t_high_rd}"
