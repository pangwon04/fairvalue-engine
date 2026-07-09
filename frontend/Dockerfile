# =============================================================================
# FairValue Frontend — Next.js 14 (standalone 출력)
# -----------------------------------------------------------------------------
# ★ 빌드 컨텍스트는 저장소 "루트"여야 한다(백엔드와 동일).
#     frontend/src/forms/productSchemas/index.ts 가 ../../../../shared/schemas/form-schema
#     를 type-only import 하므로, 빌드 타입체크 시 shared/ 가 frontend/ 의 "형제"로 있어야 한다.
#   → docker-compose 의 frontend.build.context 는 "." (루트), dockerfile 은 frontend/Dockerfile.
#   (type-only import 는 런타임 erase → standalone 산출물에는 shared 불필요. 루트 lockfile 없음 → flat 출력.)
# ★ Next 특성: NEXT_PUBLIC_* 는 빌드 시점에 번들 인라인. DOMAIN 변경 시 --build 필수.
# ★ 멀티아치: node:20-alpine 은 amd64/arm64 공식 멀티아치.
# ★ .dockerignore: 컨텍스트가 루트이므로 "루트 .dockerignore"만 적용된다
#     (frontend/.dockerignore 는 이 빌드에서 사용되지 않음 — node_modules·.next 는 루트에서 제외됨).
# =============================================================================

# ---- 빌드 스테이지 ----------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /src

# 1) shared (프론트가 type-only 로 참조 → frontend 의 형제로 배치)
COPY shared ./shared

# 2) 매니페스트 먼저(의존성 레이어 캐시)
COPY frontend/package.json frontend/package-lock.json ./frontend/
WORKDIR /src/frontend
RUN npm ci

# 3) 나머지 프론트 소스(node_modules·.next 는 .dockerignore 로 제외 → 컨테이너 설치본 유지)
WORKDIR /src
COPY frontend ./frontend
WORKDIR /src/frontend

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
# standalone 산출물(flat: server.js) + 정적 자원 + public
COPY --from=build /src/frontend/public ./public
COPY --from=build /src/frontend/.next/standalone ./
COPY --from=build /src/frontend/.next/static ./.next/static
EXPOSE 3000
CMD ["node", "server.js"]
