"""G1~G3 (RCPS): 격자 계수(보고서 u/d 대조), 1스텝 roll-back, host(우선주) 근접."""
import json
import math
from pathlib import Path

from app.models.rcps_calculator import calculate_rcps
from app.models.tf_lattice import CBLatticeSpec, crr_params, tf_value_split

FIX = Path(__file__).resolve().parent / "fixtures" / "rcps_real_2022_context.json"


def _ctx():
    return json.loads(FIX.read_text(encoding="utf-8"))


def test_g1_lattice_coefficients_match_report():
    """G1: sigma=61.74%, dt=1/12(=30/360 보고서 관례) -> u=1.1951, d=0.8368."""
    u, d, _ = crr_params(0.6174, 10.0 / 120, 0.0423, 0.0)
    assert abs(u - 1.1951) < 0.01, f"u={u}"
    assert abs(d - 0.8368) < 0.01, f"d={d}"


def test_g2_one_step_rollback():
    """G2: 1스텝 우선주(전환 off) Vb = e^{-rd*dt}*(face+cpn) 손계산 일치."""
    rd = 0.2327
    spec = CBLatticeSpec(
        s0=16639.0, sigma=0.6174, t_years=1.0, steps=1, rf=0.0423, rd=rd, q=0.0,
        face=15000.0, coupon_per_year=450.0, freq=1, conv_enabled=False,
    )
    vb, ve = tf_value_split(spec)
    expected_vb = math.exp(-rd * 1.0) * (15000.0 + 450.0)
    assert abs(vb - expected_vb) < 1e-6
    assert ve == 0.0


def test_g3_host_close_to_answer_key():
    """G3: host(preferred_share_value) 가 보고서 host(2,588)와 +-2% 내."""
    host = calculate_rcps(_ctx())["components"]["preferred_share_value"]
    assert abs(host - 2588) / 2588 < 0.02, f"host={host}"
