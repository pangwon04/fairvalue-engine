# FairValue Frontend — 계약 검증 패키지

> **이 패키지는 풀앱이 아니다.** Next.js / React 페이지 / 라우팅 / 상태관리 / API 클라이언트 등
> 런타임 애플리케이션 코드는 포함하지 않는다. 오직 **계약 검증**(폼 스키마 테스트 · bind 일치
> 검증기 · OpenAPI lint)을 실행하기 위한 최소 Node 환경이다. 실제 앱 스캐폴딩은 후속 단계다.

## 구성

| 경로 | 역할 |
|---|---|
| `src/forms/productSchemas/*.json` | 7개 상품 Dynamic Form Schema (W8 산출물) |
| `src/forms/productSchemas/index.ts` | product → FormSchema 매핑 |
| `src/forms/productSchemas/__tests__/formSchemas.test.ts` | 폼 스키마 구조 테스트(vitest) |
| `scripts/verify-bind.ts` | bind 일치 검증기(정적 분석) |
| `scripts/__tests__/verify-bind.test.ts` | 검증기 자체 테스트(정상 0위반 + 깨진 fixture exit 1 시연) |
| `scripts/lint-openapi.ts` | `../backend/src/main/resources/openapi.yaml` 유효성 검사 |

## 사용법

```bash
cd frontend
npm install

npm run test          # vitest: 폼 스키마 + 검증기 테스트
npm run verify:bind   # bind/resolve/parity/rule 일치 검증 (위반 시 exit 1)
npm run lint:openapi  # openapi.yaml 유효성 검사
npm run verify:all    # 위 셋을 순차 실행 (CI 게이트와 동일)
```

## verify:bind 가 검사하는 것

`shared/schemas/valuation-context.draft.schema.json`(rawForm)과
`shared/schemas/valuation-context.schema.json`(final)을 읽어 경로 집합을 만든 뒤 7개 폼을 정적 분석한다.

1. **bind 경로 유효성** — 입력 field 의 bind 가 허용 rawForm 경로 집합에 속하는지.
2. **resolve 규칙** — curveSelector(curve)·assetSearch(asset) 의 bind/resolve 짝이 규칙에 맞는지.
3. **final 일치** — `resolve.to` 가 final 스키마에 실제 존재하는지.
4. **parity 규칙** — `type=computed` 필드는 bind 가 없어야 함.
5. **bind 필수** — computed/readonly 외 모든 입력 field 는 bind 필수.
6. **rule 무결성** — `validations[].rule` 이 표준 20종(+W206) 집합에 속하는지.

전부 통과하면 exit 0, 위반이 하나라도 있으면 위반 목록 출력 후 exit 1.

## OpenAPI lint

`scripts/lint-openapi.ts` 는 `@apidevtools/swagger-parser` 로 dereference·검증을 시도하고,
OpenAPI 3.1 구조 규칙(필수 필드·operationId·PricingResult 필수필드·표준 component key)을 추가 점검한다.
외부 린터를 쓰고 싶으면 `npx @redocly/cli@latest lint ../backend/src/main/resources/openapi.yaml` 도 가능.
