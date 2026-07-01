# ===========================================================================
# FairValue Pricing Engine — FastAPI 진입점 (Phase 5-2)
# ---------------------------------------------------------------------------
# Kotlin 백엔드(RealPricingEngineClient) → POST /price(ValuationContext dict) →
#   registry.LIBRARY.calculate(ctx) → PricingResult(dict) 반환.
#
# ★ 계산로직(calculator·registry) 불변. 이 파일은 HTTP 래핑만.
# ★ ValuationContext 는 백엔드 RealContextResolver 산출 dict 그대로(어댑터 없음):
#     instrument_type·model·seed·model_version·valuation_date·input_hash·terms·rights·
#     market·curves.{risk_free_curve,credit_curve=[[t,rate]]}·options.
# ★ input_hash: 계산기는 ctx["input_hash"] 를 echo(재계산 안 함) → 백엔드 값과 정합.
# ★ computed_at: 계산기 reproducibility 에 없으므로 여기서 보강(Kotlin DTO 필수 필드).
#
# 실행: uvicorn app.main:app --port 8000
# ===========================================================================
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.models.registry import LIBRARY

app = FastAPI(title="FairValue Pricing Engine", version="1.0.0")


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "engines": ["CB", "RCPS", "CPS", "EB", "BW"]}


@app.post("/price")
async def price(request: Request) -> Any:
    # dict 바디 그대로(extra 키 metadata 등 거부 없이 통과).
    ctx: dict = await request.json()
    try:
        result = LIBRARY.calculate(ctx)          # registry 라우팅(계산로직 불변)
    except KeyError as e:
        return JSONResponse(status_code=422, content={
            "error": "unsupported_or_invalid_context",
            "message": f"모델/입력 오류: {e}",
        })
    except Exception as e:  # noqa: BLE001 — 계산 실패를 명확한 5xx 로 전파
        return JSONResponse(status_code=500, content={
            "error": "engine_calculation_failed",
            "message": str(e),
        })
    # reproducibility.computed_at 보강(계산기엔 없음 → Kotlin DTO 필수).
    repro = result.setdefault("reproducibility", {})
    repro.setdefault("computed_at", datetime.now(timezone.utc).isoformat())
    return result
