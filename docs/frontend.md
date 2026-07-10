# 프론트엔드 (frontend)

Next.js 14 (App Router) + TypeScript다. 얇은 층을 지향한다. 상품마다 화면을 새로 짜지 않고 JSON 스키마 하나로 폼을 렌더링하며, 백엔드 API를 호출해 결과·근거를 보여준다. 서버 상태는 TanStack Query로 다룬다.

```text
frontend/src/
├── app/            # 라우트 (App Router)
│   ├── (auth)/     #   login, signup
│   └── (app)/      #   dashboard, instruments, jobs, parameters, reports, admin
├── forms/          # 스키마 구동 폼 엔진
├── components/     # 화면 조각 (ui, layout, 도메인별)
└── lib/            # API 클라이언트·인증·유틸
```

## app/ — 라우트

App Router의 route group으로 인증 화면과 앱 화면을 나눈다.

- **`(auth)/`** — `login`, `signup`. 로그인 성공 시 대시보드로 보낸다.
- **`(app)/`** — 로그인 뒤 화면. `dashboard`(현황 요약), `instruments`(상품 목록·생성·상세), `jobs`(평가 이력·결과·계산근거), `parameters/curves`·`parameters/volatility`(파라미터 관리), `reports`(보고서 목록), `admin`(조직 관리). `(app)/layout.tsx`가 인증 가드와 사이드바 셸을 감싼다.

## forms/ — 스키마 구동 폼 엔진

이 프로젝트의 핵심 화면 로직이다. 상품별 JSON 스키마를 읽어 폼을 만든다.

- **`FormRenderer.tsx`** — 스키마를 받아 섹션·필드를 배치하는 상위 렌더러.
- **`FieldRenderer.tsx`** — 필드 하나를 타입(텍스트·숫자·날짜·커브 선택·변동성 불러오기 등)에 맞게 그린다.
- **`productSchemas/*.json`** — 상품 7종(cb·rcps·cps·eb·bw·so·cso)의 폼 정의. `index.ts`가 상품 → 스키마로 매핑한다. 이 JSON이 백엔드 룰 검증이 읽는 것과 같은 폼 스키마다.
- **`validate.ts` · `bindPath.ts` · `showWhen.ts` · `types.ts`** — 클라이언트 검증, 값 바인딩 경로(`market.volatility` 같은 점 경로), 조건부 노출(showWhen), 폼 타입 정의.

## components/ — 화면 조각

- **`ui/`** — 공통 UI(Button·Card·Modal·Input·Select·Badge·Tabs·Spinner 등).
- **`layout/`** — `AppShell`(사이드바·헤더), `AuthGuard`.
- **도메인별** — `curves/`(업로드 탭·목록·차트), `volatility/`(산출·목록), `reports/`(발급 버튼), `audit/`(계산근거 아코디언 `CalculationBasis`), `parameters/`(삭제 버튼), 그리고 `ResultView`·`ProductPicker`·`DeleteInstrumentButton` 등.

## lib/ — API·인증·유틸

- **`apiClient.ts`** — fetch 래퍼. 베이스 URL(`NEXT_PUBLIC_API_BASE_URL`), JWT 헤더, 에러 처리, GET/POST/PUT/DELETE와 파일 업로드를 한 곳에서 다룬다.
- **`api/*.ts`** — 도메인별 API 함수(`auth`·`instruments`·`terms`·`pricing`·`curves`·`volatilities`·`reports`·`dashboard`). 컴포넌트는 이 함수만 부른다.
- **`auth.ts` · `nav.ts` · `products.ts` · `types.ts` · `cn.ts` · `QueryProvider.tsx`** — 토큰·사용자 저장, 사이드바 구성, 상품 메타(모형 배지 등), 공용 타입, className 유틸, React Query provider.

## 설정 메모

API 주소와 데모 배너는 `NEXT_PUBLIC_*` 환경변수이고 **빌드 시점에 번들로 인라인**된다. 그래서 주소가 바뀌면 프론트를 다시 빌드해야 한다(배포 문서 참고). 빌드는 `output: 'standalone'`으로 자립 실행 산출물을 만든다.

## 테스트·검증

`scripts/`의 `verify-bind`(스키마 바인딩 경로 점검)와 `lint-openapi`(OpenAPI 계약 린트), 그리고 폼 스키마 vitest가 있다. `npm run verify:all`이 vitest·타입체크·이 검증들을 한 번에 돌린다.
