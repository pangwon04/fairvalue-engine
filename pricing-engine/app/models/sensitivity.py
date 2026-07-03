# ===========================================================================
# FairValue Engine — 민감도 분석(엔진확장-3a, 변동성×기초자산 3×3)
# ---------------------------------------------------------------------------
# 보고서 Appendix Sensitivity 표(KPMG·DeepSearch 동일 축)의 데이터 원천.
#   축: 변동성 {σ−5%p, σ, σ+5%p} × 기초자산 {S×0.95, S, S×1.05} = 3×3.
#   실행: report_steps(트리와 동일 — 가볍고 일관). 최종 완전 spec(전 옵션 활성) 1회/셀.
#         base 셀[1][1] == 트리 composite 루트(1e-6 sanity, 호출부 게이트).
#   모형: ctx.model 따라 TF/GS 동일 경로(total_at 클로저가 캡슐화).
# ★ 단조성 assert 금지(풋 바닥·조기행사로 비단조 정상). 구조 게이트만.
# ★ σ−5%p 가 하한 이하로 내려가면 vol_floor 로 가드 + meta 표기.
# pure Python. 새 의존성 없음.
# ===========================================================================
from __future__ import annotations

VOL_BUMP = 0.05     # ±5%p (절대)
SPOT_BUMP = 0.05    # ±5% (상대)
VOL_FLOOR = 0.01    # 변동성 하한 1%


def sensitivity_grid(total_at, sigma: float, spot: float, model: str, steps_used: int,
                     vol_bump: float = VOL_BUMP, spot_bump: float = SPOT_BUMP,
                     vol_floor: float = VOL_FLOOR) -> dict:
    """3×3 민감도 그리드.
    total_at(sig_decimal, s0) -> float : 최종 완전 spec 을 report_steps 로 실행한 총가치.
                                         (rf_steps/rd_steps·옵션은 클로저가 고정, σ·S 만 변동)
    반환: vol_axis/spot_axis/total_grid/per_unit_grid/meta.
    """
    floor_applied = (sigma - vol_bump) < vol_floor
    sig_lo = max(vol_floor, sigma - vol_bump)
    vol_axis = [sig_lo, sigma, sigma + vol_bump]
    spot_axis = [spot * (1.0 - spot_bump), spot, spot * (1.0 + spot_bump)]

    total_grid = [[total_at(v, s) for s in spot_axis] for v in vol_axis]

    return {
        "vol_axis": [round(v, 6) for v in vol_axis],
        "spot_axis": [round(s, 4) for s in spot_axis],
        "total_grid": [[round(x, 4) for x in row] for row in total_grid],
        "per_unit_grid": [[round(x, 4) for x in row] for row in total_grid],  # 1 unit = total
        "meta": {
            "steps_used": steps_used,
            "model": model,
            "vol_bump": vol_bump,
            "spot_bump": spot_bump,
            "vol_floor_applied": floor_applied,
        },
    }
