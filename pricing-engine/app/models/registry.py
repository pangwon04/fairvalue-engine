# ===========================================================================
# FairValue Engine — ModelLibrary (Phase 3, 다상품/다모델 확장점)
# ---------------------------------------------------------------------------
# 인터페이스: calculate(context: dict) -> dict (PricingResult-like).
#   - 이번 슬라이스는 TF_LATTICE(CB) 만 등록.
#   - 다음 묶음에서 RCPS·다른 모델이 같은 (model_name -> Calculator) 인터페이스로 붙는다.
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
    LIBRARY.register("TF_LATTICE", calculate_cb)


_register_defaults()
