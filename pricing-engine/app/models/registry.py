# ===========================================================================
# FairValue Engine — ModelLibrary (Phase 3, 다상품/다모델 확장점)
# ---------------------------------------------------------------------------
# 인터페이스: calculate(context: dict) -> dict (PricingResult-like).
#   - instrument_type 으로 calculator 분기(CB / RCPS / CPS / EB / BW).
# ===========================================================================
from __future__ import annotations

from typing import Callable

Calculator = Callable[[dict], dict]


class ModelLibrary:
    def __init__(self) -> None:
        self._models: dict[str, Calculator] = {}

    def register(self, model_name: str, calculator: Calculator) -> None:
        self._models[model_name] = calculator

    def get(self, model_name: str) -> Calculator:
        if model_name not in self._models:
            raise KeyError(f"등록되지 않은 모델: {model_name} (등록됨: {list(self._models)})")
        return self._models[model_name]

    def calculate(self, context: dict) -> dict:
        """context['model'] 로 라우팅해 평가."""
        model = context.get("model")
        return self.get(model)(context)


LIBRARY = ModelLibrary()


def _register_defaults() -> None:
    from .cb_calculator import calculate_cb, calculate_cb_gs
    from .cps_calculator import calculate_cps, calculate_cps_gs
    from .eb_calculator import calculate_eb, calculate_eb_gs
    from .bw_calculator import calculate_bw, calculate_bw_gs
    from .rcps_calculator import calculate_rcps, calculate_rcps_gs

    def tf_lattice_dispatch(ctx: dict) -> dict:
        """TF_LATTICE 모델은 instrument_type 으로 calculator 분기(CB / RCPS / CPS / EB / BW)."""
        it = ctx.get("instrument_type")
        if it == "RCPS":
            return calculate_rcps(ctx)
        if it == "CPS":
            return calculate_cps(ctx)
        if it == "EB":
            return calculate_eb(ctx)
        if it == "BW":
            return calculate_bw(ctx)
        return calculate_cb(ctx)

    def gs_dispatch(ctx: dict) -> dict:
        """GS(Goldman-Sachs) 모델은 instrument_type 으로 GS calculator 분기.
        CB·RCPS·CPS·EB·BW 만 지원(전환·교환·신주인수권 부채요소). 나머지는 각자 모형."""
        it = ctx.get("instrument_type")
        if it == "RCPS":
            return calculate_rcps_gs(ctx)
        if it == "CPS":
            return calculate_cps_gs(ctx)
        if it == "EB":
            return calculate_eb_gs(ctx)
        if it == "BW":
            return calculate_bw_gs(ctx)
        return calculate_cb_gs(ctx)

    LIBRARY.register("TF_LATTICE", tf_lattice_dispatch)
    LIBRARY.register("LATTICE", tf_lattice_dispatch)
    LIBRARY.register("RCPS_TF_LATTICE", calculate_rcps)
    LIBRARY.register("CPS_TF_LATTICE", calculate_cps)
    LIBRARY.register("EB_TF_LATTICE", calculate_eb)
    LIBRARY.register("BW_TF_LATTICE", calculate_bw)
    # ★ GS 모델 라우팅(엔진확장-2). 기존 TF 라우팅 불변.
    LIBRARY.register("GS", gs_dispatch)
    LIBRARY.register("GS_LATTICE", gs_dispatch)
    LIBRARY.register("CB_GS", calculate_cb_gs)
    LIBRARY.register("RCPS_GS", calculate_rcps_gs)
    LIBRARY.register("CPS_GS", calculate_cps_gs)
    LIBRARY.register("EB_GS", calculate_eb_gs)
    LIBRARY.register("BW_GS", calculate_bw_gs)


_register_defaults()
