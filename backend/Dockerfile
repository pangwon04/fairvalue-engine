# =============================================================================
# FairValue Backend — Kotlin/Spring Boot 3.3.2 (JDK21 빌드 → JRE21 런타임)
# -----------------------------------------------------------------------------
# ★ 빌드 컨텍스트는 저장소 "루트"여야 한다.
#     backend/build.gradle.kts 의 processResources 가 ../shared 와
#     ../frontend/src/forms/productSchemas 를 classpath 로 복사하기 때문.
#   → docker-compose 의 backend.build.context 는 "." (루트), dockerfile 은 backend/Dockerfile.
# ★ 멀티아치: eclipse-temurin 은 amd64/arm64 공식 멀티아치.
# ★ fonts(NotoSansKR-Regular.ttf)는 backend/src/main/resources/fonts 에 있어 jar 에 자동 포함.
# =============================================================================

# ---- 빌드 스테이지 ----------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src

# Gradle 래퍼·빌드스크립트 먼저(레이어 캐시)
COPY backend/gradlew backend/gradlew
COPY backend/gradle backend/gradle
COPY backend/build.gradle.kts backend/settings.gradle.kts backend/

# 계약 스키마 단일 출처(processResources 가 ../shared, ../frontend/... 를 복사)
COPY shared shared
COPY frontend/src/forms/productSchemas frontend/src/forms/productSchemas

# 백엔드 소스
COPY backend/src backend/src

WORKDIR /src/backend
# 테스트는 Testcontainers(Docker) 의존이라 이미지 빌드 시 제외. bootJar 만 생성.
RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar -x test
# 부트 실행가능 jar(비 -plain)만 선택해 고정 이름으로 복사.
RUN cp "build/libs/$(ls build/libs | grep -vE 'plain' | grep -E '\.jar$' | head -1)" /app.jar

# ---- 런타임 스테이지 --------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
# healthcheck(curl)용 최소 패키지
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# 초소형 VPS(RAM 2~3GB) 대비 힙 상한.
ENV JAVA_TOOL_OPTIONS="-Xmx768m"
COPY --from=build /app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
