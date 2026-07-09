# =============================================================================
# FairValue Frontend — Next.js 14 (standalone 출력)
# -----------------------------------------------------------------------------
# ★ Next 특성: NEXT_PUBLIC_* 는 "빌드 시점"에 번들에 인라인된다.
#     → API 주소(NEXT_PUBLIC_API_BASE_URL)·배너(NEXT_PUBLIC_DEMO_BANNER)는 빌드 ARG.
#     → DOMAIN 변경 시 프론트는 반드시 재빌드(docker compose up -d --build).
# ★ next.config.mjs 에 output:'standalone' 필요(별도 커밋).
# ★ 멀티아치: node:20-alpine 은 amd64/arm64 공식 멀티아치.
# 빌드 컨텍스트: ./frontend
# =============================================================================

# ---- 빌드 스테이지 ----------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# 빌드 시점 주입(번들 인라인)
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_DEMO_BANNER
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL \
    NEXT_PUBLIC_DEMO_BANNER=$NEXT_PUBLIC_DEMO_BANNER \
    NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ---- 런타임 스테이지(standalone) -------------------------------------------
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000
# standalone 산출물 + 정적 자원 + public
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
CMD ["node", "server.js"]
