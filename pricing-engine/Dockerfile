# =============================================================================
# FairValue Pricing Engine — FastAPI (Python 3.12 slim)
# -----------------------------------------------------------------------------
# ★ 무인증 엔진 — 절대 외부 포트 미공개(docker-compose 내부 네트워크 전용).
# ★ 계산기는 pure-Python(math). uvicorn worker 1(초소형 VPS·동시 ~10명).
# ★ 멀티아치: python:3.12-slim 은 amd64/arm64 공식 멀티아치.
# 빌드 컨텍스트: ./pricing-engine
# =============================================================================
FROM python:3.12-slim AS runtime

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app

# 의존성 먼저(레이어 캐시)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 애플리케이션(app 패키지: main.py + models + ...)
COPY app ./app

EXPOSE 8000
# 백엔드(RealPricingEngineClient)가 http://engine:8000/price 로 호출.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
