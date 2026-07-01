# ===========================================================================
# FairValue Engine — ModelLibrary (Phase 3, 다상품/다모델 확장점)
# ---------------------------------------------------------------------------
# 인터페이스: calculate(context: dict) -> dict (PricingResult-like).
#   - instrument_type 으로 calculator 분기(CB / RCPS / CPS / EB).
# ===========================================================================
from __future__ import annotations

from typing import Callable

# Calculator: ValuationContext-like dict -> PricingResult-like dict
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


# 전역 라이브러리 + 등록(임포트 시 1회).
LIBRARY = ModelLibrary()


def _register_defaults() -> None:
    from .cb_calculator import calculate_cb
    from .cps_calculator import calculate_cps
    from .eb_calculator import calculate_eb
    from .rcps_calculator import calculate_rcps

    def tf_lattice_dispatch(ctx: dict) -> dict:
        """TF_LATTICE 모델은 instrument_type 으로 calculator 분기(CB / RCPS / CPS / EB)."""
        it = ctx.get("instrument_type")
        if it == "RCPS":
            return calculate_rcps(ctx)
        if it == "CPS":
            return calculate_cps(ctx)
        if it == "EB":
            return calculate_eb(ctx)
        return calculate_cb(ctx)

    LIBRARY.register("TF_LATTICE", tf_lattice_dispatch)
    LIBRARY.register("LATTICE", tf_lattice_dispatch)   # RCPS 보고서 model="LATTICE"
    LIBRARY.register("RCPS_TF_LATTICE", calculate_rcps)
    LIBRARY.register("CPS_TF_LATTICE", calculate_cps)
    LIBRARY.register("EB_TF_LATTICE", calculate_eb)


_register_defaults()
