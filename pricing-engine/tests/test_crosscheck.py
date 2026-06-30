"""교차검증: 전환옵션(통제된 European call)을 격자 vs Monte Carlo 로 1~2% 내 수렴 확인."""
from app.models.mc import european_call_mc
from app.models.tf_lattice import european_call_crr


def test_lattice_vs_mc_european_call():
    s0, strike, sigma, t, rf, q = 3260.0, 3260.0, 0.45, 1.0, 0.03, 0.0
    lat = european_call_crr(s0, strike, sigma, t, rf, q, steps=800)
    mc = european_call_mc(s0, strike, sigma, t, rf, q, paths=200_000, seed=20240101)
    rel = abs(lat - mc) / lat
    print(f"\n[교차검증] lattice={lat:.4f} MC={mc:.4f} rel_err={rel*100:.3f}%")
    assert rel < 0.02, f"격자 vs MC 수렴 실패: {rel*100:.3f}%"
