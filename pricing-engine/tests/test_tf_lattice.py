"""G1~G3: TF 격자 계수·1스텝 roll-back·전체 역산 단계 검증."""
import math

from app.models.tf_lattice import CBLatticeSpec, crr_params, tf_value


def test_g1_crr_params():
    """G1: u=e^{σ√Δt}, d=1/u, p=(e^{(rf−q)Δt}−d)/(u−d) 손계산 일치."""
    sigma, dt, rf, q = 0.2, 0.01, 0.05, 0.0
    u, d, p = crr_params(sigma, dt, rf, q)
    exp_u = math.exp(0.2 * math.sqrt(0.01))
    assert abs(u - exp_u) < 1e-12
    assert abs(d - 1.0 / exp_u) < 1e-12
    assert abs(p - (math.exp((0.05) * 0.01) - d) / (u - d)) < 1e-12
    assert 0.0 < p < 1.0


def test_g2_one_step_two_rate_rollback():
    """G2: 1스텝 격자에서 (up=전환=지분@rf, down=상환=부채@rd) 혼합할인 손계산 일치."""
    s0, sigma, t, rf, rd, q = 3000.0, 0.45, 1.0, 0.03, 0.15, 0.0
    face, ratio = 10000.0, 3.0
    spec = CBLatticeSpec(
        s0=s0, sigma=sigma, t_years=t, steps=1, rf=rf, rd=rd, q=q,
        face=face, coupon_per_year=0.0, freq=0,
        conv_enabled=True, conv_ratio=ratio, conv_start_t=0.0,
    )
    dt = t
    u, d, p = crr_params(sigma, dt, rf, q)
    su, sd = s0 * u, s0 * d
    conv_u, conv_d = ratio * su, ratio * sd
    # 설정상 up 은 전환(지분), down 은 상환(부채)
    assert conv_u >= face and conv_d < face
    vb_root = math.exp(-rd * dt) * ((1 - p) * face)          # down 부채
    ve_root = math.exp(-rf * dt) * (p * conv_u)              # up 지분
    expected = vb_root + ve_root
    # 루트에서 즉시 전환(ratio*s0)이 보유가치보다 작아야(=보유) 위 식이 루트값
    assert ratio * s0 < expected
    assert abs(tf_value(spec) - expected) < 1e-9


def test_g3_pure_discount_bond():
    """G3: 옵션 없는 순수 무이표 채권 = face·e^{-rd·T} (연속복리)."""
    face, rd, t = 10000.0, 0.12, 2.0
    spec = CBLatticeSpec(
        s0=3000.0, sigma=0.4, t_years=t, steps=600, rf=0.03, rd=rd, q=0.0,
        face=face, coupon_per_year=0.0, freq=0,
        conv_enabled=False, put_enabled=False, call_enabled=False,
    )
    expected = face * math.exp(-rd * t)
    assert abs(tf_value(spec) - expected) < 1e-6
