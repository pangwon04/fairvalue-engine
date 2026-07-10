# FairValue Engine

복합금융상품(전환사채·교환사채·신주인수권부사채·상환전환우선주 등)의 공정가치를 평가하고, 계산 근거와 감사대응 보고서까지 뽑아내는 웹 플랫폼.

> _Fair-value measurement for complex financial instruments — schema-driven input, lattice pricing, transparent calculation trails, and audit-ready reports._

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-6DB33F)
![Java](https://img.shields.io/badge/JDK-21-orange)
![Next.js](https://img.shields.io/badge/Next.js-14.2-black)
![TypeScript](https://img.shields.io/badge/TypeScript-5.5-3178C6)
![Python](https://img.shields.io/badge/Python-3.12%20·%20FastAPI-3776AB)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1)
![Docker](https://img.shields.io/badge/Docker%20Compose-1D63ED)

> 🔗 데모: {{DEMO_URL}} · 데모 계정: {{DEMO_ACCOUNT}}

## 배경

복합금융상품 평가는 아직도 담당자 스프레드시트로 이뤄지는 경우가 많다. 같은 입력을 다시 계산했을 때 값이 재현되는지, 중간 근거는 어디에 남는지, 감사 때 입력과 산출을 어떻게 되짚을지가 늘 문제였다. 재현성·추적성·감사대응, 이 세 가지를 처음부터 설계에 넣고 만든 평가 플랫폼이다. 회계·금융 실무 지식을 바탕으로 스펙을 짜고, 구현을 AI와 협업해 완성했다.

## 미리보기

> 스크린샷은 데모 촬영 후 `docs/images/`에 추가 예정.

| 화면 | 내용 |
|---|---|
| 상품 입력 폼 | 상품 유형별 스키마로 렌더링되는 폼 |
| 평가 결과 | 총공정가치, 단위당, 12개 구성요소 분해 |
| 계산근거 | 가격 트리, 위험중립확률, 이자율표, 민감도 3×3 |
| 보고서 | 평가보고서 PDF + 계산근거 엑셀 |
| 대시보드 | 등록 상품·평가·보고서·파라미터 현황 |

<!-- ![상품 입력 폼](docs/images/01-input-form.png) -->
<!-- ![평가 결과](docs/images/02-result.png) -->
<!-- ![계산근거](docs/images/03-basis.png) -->
<!-- ![보고서 PDF](docs/images/04-report-pdf.png) -->
<!-- ![대시보드](docs/images/05-dashboard.png) -->

## 기능

- 상품별 입력 폼을 JSON 스키마로 정의 (RCPS·CPS·CB·EB·BW·SO·CSO 7종)
- 같은 상품을 T&F, GS 두 모형으로 평가 (엔진 구현은 CB·RCPS·CPS·EB·BW)
- 수익률 커브 부트스트래핑(YTM→Spot→Forward)과 역사적 변동성 관리
- 가격 트리·위험중립확률·이자율표·민감도까지 계산 근거를 전부 노출
- 평가보고서 PDF와 계산근거 엑셀 발급
- input_hash와 평가시점 입력 스냅샷으로 감사 추적
- 조직 격리 + 역할 기반 접근제어(RBAC), JWT 인증

## 기술 스택

| 계층 | 기술 |
|---|---|
| 프론트엔드 | Next.js 14.2 (App Router), TypeScript 5.5, Tailwind CSS, TanStack Query |
| 백엔드 | Kotlin 1.9.24, Spring Boot 3.3.2, JDK 21, JPA, JWT, Flyway |
| 평가엔진 | Python 3.12, FastAPI, Pydantic 2 (순수 파이썬 격자, 외부 수치 라이브러리 없음) |
| 데이터 | PostgreSQL 16 |
| 보고서 | OpenPDF(PDF), Apache POI(Excel) |
| 인프라 | Docker Compose, Caddy 2 (자동 HTTPS) |

스키마는 Flyway로 관리한다. 조직·상품(V1), 평가 결과(V2·V3), 커브(V4), 변동성(V5), 입력 스냅샷(V6), 이력 숨김(V7), 보고서(V8)까지 여덟 단계로 쌓아 올렸다.

## 아키텍처

```mermaid
flowchart LR
    U[브라우저] -->|HTTPS| C[Caddy 80/443 공개]
    C -->|DOMAIN| F[Next.js 프론트엔드 3000]
    C -->|api.DOMAIN| B[Spring Boot 백엔드 8080]
    B -->|POST /price| E[Python 평가엔진 8000 내부전용]
    B --> P[(PostgreSQL 5432)]
```

밖으로 열리는 건 Caddy(80/443) 하나뿐이다. 프론트·백엔드·엔진·DB는 컨테이너 내부 네트워크에만 있고 포트를 밖으로 열지 않는다. 특히 평가엔진은 인증이 없어서, 노출하면 누구나 계산 API를 호출할 수 있다. 그래서 엔진 비공개를 최우선 제약으로 뒀다.

### 세 계층으로 나눈 이유

- **평가엔진 (Python/FastAPI)** 은 순수 계산만 한다. 상태도, DB도, 인증도 없다. ValuationContext(dict)를 받아 PricingResult(dict)를 돌려주는 함수 하나에 가깝다. 이렇게 떼어 놓으면 엔진만 pytest로 따로 검증할 수 있고, 나중에 언어나 수치 라이브러리를 통째로 바꿔도 계약만 지키면 백엔드는 손댈 필요가 없다.
- **백엔드 (Kotlin/Spring Boot)** 는 도메인 규칙이 모이는 곳이다. 인증·권한(RBAC), 조직 격리, 입력 정규화, 커브·변동성 매핑, input_hash 산출, 캐시, 결과·스냅샷 저장, 보고서 생성을 맡는다. 엔진은 인터페이스(`PricingEngineClient`) 뒤에 둬서 더미와 실엔진을 갈아끼운다.
- **프론트엔드 (Next.js)** 는 스키마로 폼을 그리고 결과·근거를 보여주는 얇은 층이다. 상품마다 화면을 새로 짜지 않고, JSON 스키마 하나로 7종 폼을 렌더링한다.

### 계약으로만 붙는다

세 모듈은 아래 세 계약으로만 통신한다. 필드는 더하되 기존 계약은 깨지 않는 것(additive)을 원칙으로 했고, 스키마 원본은 `shared/schemas` 한 곳에만 두고 빌드할 때 백엔드 클래스패스와 프론트로 복사해 drift를 막는다.

| 계약 | 역할 | 정본 위치 |
|---|---|---|
| Form Schema | 상품별 입력 폼 정의 | `shared/schemas/form-schema.ts`, `frontend/.../productSchemas/*.json` |
| ValuationContext | 정규화된 평가 입력(엔진 입력) | `shared/schemas/valuation-context.schema.json` |
| PricingResult | 엔진 산출(12 구성요소·근거·재현정보) | `shared/schemas/pricing-result.schema.json` |

### 평가 요청의 흐름

```mermaid
sequenceDiagram
    participant F as 프론트엔드
    participant B as 백엔드
    participant E as 평가엔진
    participant DB as PostgreSQL
    F->>B: POST /instruments/{id}/price
    B->>B: 커브·변동성 매핑, ValuationContext 정규화
    B->>B: input_hash 산출 (SHA-256)
    B->>DB: 같은 해시의 완료 Job 있으면 재사용(캐시)
    B->>E: POST /price (ValuationContext)
    E->>E: CRR 격자 backward induction
    E-->>B: PricingResult (트리·부트스트래핑·민감도 포함)
    B->>DB: 결과 원문 + 평가시점 입력 스냅샷 저장
    B-->>F: job_id, status
    F->>B: GET /jobs/{id}/result, 보고서 발급
    B-->>F: 결과·계산근거, PDF·Excel
```

이 흐름에서 두 가지가 핵심이다. 하나는 **input_hash** 다. 정규화한 입력 전체를 SHA-256으로 묶어, 같은 입력이면 캐시로 재사용하고 동시에 감사 식별자로 쓴다. 다른 하나는 **원문 보존** 이다. 엔진이 준 JSON을 타입 DTO로 좁혀 받지 않고 원문 그대로 저장한다. 그래야 가격 트리·부트스트래핑·민감도 같은 근거가 중간에 잘리지 않고 보고서까지 이어진다.

## 평가 엔진

CRR 이항격자(`u = exp(σ√Δt)`, `d = 1/u`)를 공통 골격으로 두 모형을 얹었다.

**T&F** 는 상품을 지분요소(전환 시 주식가치, `rf`로 할인)와 부채요소(현금흐름, `rd`로 할인)로 나눠 backward induction 한다. 각 노드에서 보유자의 전환·상환청구권과 발행자의 콜을 최적행사로 반영한다.

**GS** 는 요소를 나누지 않는다. 노드마다 전환확률 `q`를 전파하면서 할인율 `y = q·rf + (1−q)·rd`를 섞어 한 트리로 푼다.

산출은 12개 구성요소로 쪼개지고, 그 합은 항상 총공정가치와 같다. 커브 부트스트래핑(par → spot → forward)과 변동성×기초자산 민감도 3×3도 함께 뽑는다.

## 실행

개발 모드 (프로세스 4개)

```bash
docker compose -f backend/docker-compose.yml up -d db   # DB
cd pricing-engine && uvicorn app.main:app --port 8000   # 엔진
cd backend && ./gradlew bootRun                          # 백엔드
cd frontend && npm run dev                               # 프론트
```

Docker Compose 한 번에 (로컬)

```bash
cp .env.example .env      # JWT_SECRET 등 채우기
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
# http://localhost:3000
```

서버 배포(단일 VPS, Caddy 자동 HTTPS)는 [README-DEPLOY.md](./README-DEPLOY.md)에 정리했다.

## 저장소 구조

```text
fairvalue-engine/
├── pricing-engine/     # Python 평가엔진 (FastAPI). 격자·모형·registry·result
├── backend/            # Kotlin/Spring Boot. 컨트롤러·서비스·보고서 생성·엔진 연동
│   └── .../db/migration/   # Flyway V1~V8, openapi.yaml
├── frontend/           # Next.js (App Router)
├── shared/schemas/     # 세 계약의 단일 출처
├── golden-values/      # 검증용 골든·외부 대조 케이스
├── docs/               # 코드 구조 문서
├── docker-compose.yml  # Caddyfile, README-DEPLOY.md
```

코드가 모듈 단위로 어떻게 나뉘는지는 [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)에 정리했다. 엔진·백엔드·프론트 개별 문서로 이어진다.

## 테스트 & 검증

| 대상 | 개수 |
|---|---|
| 엔진 (pytest) | 82 |
| 백엔드 (@Test, Testcontainers PostgreSQL) | 101 |
| 프론트 (vitest) | 16 |

CI(GitHub Actions)가 푸시마다 위 테스트에 타입체크와 OpenAPI 린트까지 돌린다. 엔진 정확성은 세 방향으로 잡았다.

- 실제 상용 평가보고서(RCPS 동일 상품, 3개 평가시점)와 총액을 대조했다. 오차는 −0.4% / +2.0% / +0.7%로 ±2% 안에 들어온다.
- `rd = rf`이면 GS와 T&F 결과가 같아야 한다는 항등식을 확인한다 (< 1e-6).
- input_hash(SHA-256)로 같은 입력이면 같은 결과가 나온다. 파이썬 정본과 코틀린 구현을 테스트 벡터로 교차검증한다.

## 감사 추적

- **input_hash 체인**: 정규화한 입력을 SHA-256으로 묶어 입력 스냅샷·계산근거·보고서 식별정보를 잇는다.
- **평가시점 스냅샷**: 평가할 때 입력 전체를 `context_json`으로 저장한다. 나중에 커브나 변동성이 바뀌거나 삭제돼도 과거 평가는 그대로 재현된다.
- **삭제 정책**: 감사 가치를 기준으로 나눴다. 결과가 있는 상품은 보관하고 없으면 지운다. 완료된 평가는 숨기고 실패한 건만 삭제한다. 파라미터는 지워도 과거 평가가 스냅샷으로 남아 안전하다.

## 만든 방식

설계와 도메인(회계·금융) 판단을 직접 하고 구현을 AI에 맡기는 스펙 주도 방식으로 개발했다. 기능마다 설계 → 승인 → 구현 → 로컬 검증 → 커밋을 반복했고, 검증을 통과하지 못한 변경은 커밋하지 않는 것을 규칙으로 뒀다.

## 로드맵

- 금리모형 확장 (BDT, Hull-White)
- LSMC 기반 리픽싱(경로의존 전환가 조정)
- SO·CSO 평가 엔진 (지금은 입력 스키마만 있다)

## 고지

포트폴리오 데모다. 실제 서비스가 아니며 데이터는 예고 없이 초기화될 수 있다. 평가값은 선택한 모형과 입력 가정에 의존하고, 데모에 올리는 커브·변동성은 실제 시장데이터가 아니라 샘플이다.
