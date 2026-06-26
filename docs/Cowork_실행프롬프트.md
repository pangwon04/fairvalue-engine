# Cowork 실행 프롬프트 — FairValue 3대 계약 v0.1 파일화 (W1\~W7)

> 아래 박스 안 전체를 \*\*그대로 복사해 Claude Cowork에 붙여넣으세요.\*\*
> 첨부물: `FairValue\_3대계약\_v0.1\_Freeze패치.md`(필수 기준), `FairValue\_3대계약\_명세.md`, `FairValue\_샘플입력\_템플릿.xlsx`, `curve\_riskfree\_template.csv`, `curve\_credit\_template.csv`.

\---

```
역할: 너는 이 저장소에서 FairValue Engine 3대 계약(v0.1)을 실제 파일로 생성하는 작업을 한다.
첨부한 「FairValue 3대계약 v0.1 Freeze 패치」 문서를 유일한 기준으로 삼아라. 패치 문서와 충돌하는 다른 문서 내용이 있으면 패치 문서를 따른다.

\[이번 작업 범위]
- W1\~W7 파일만 생성/수정한다.
- frontend / backend 애플리케이션 전체 스캐폴딩(Next.js 프로젝트 생성, Spring Boot 프로젝트 생성, 빌드 설정, 의존성 트리 구성 등)은 이번에 하지 않는다. 아래 명시된 파일만 만든다.
- W8(상품 폼 스키마), W9(openapi.yaml), W10(bind 검증기)은 이번 범위가 아니다.

\[생성/수정 대상 파일]
W1 shared/schemas/instrument-types.ts
W2 shared/schemas/form-schema.ts
W3 shared/schemas/validation-rules.ts
W4 shared/schemas/valuation-context.schema.json
   shared/schemas/valuation-context.draft.schema.json
   shared/schemas/valuation-context.ts
W5 shared/schemas/pricing-result.schema.json
   shared/schemas/pricing-result.ts
W6 pricing-engine/app/context.py
   pricing-engine/app/result.py
W7 pricing-engine/app/reproducer.py
   backend/src/main/resources/InputHash.kt
   shared/schemas/hash-test-vectors.json
   golden-values/cb\_case1.json
   golden-values/rcps\_case1.json

\[반드시 반영할 패치 — 자가검증 항목]
B-1) RCPS 예시/golden은 preferred\_share\_value=18760.00 으로 Σ(components)=total\_fair\_value=24180.55 가 정확히 성립해야 한다. rcps\_case1.json 에 이 값을 넣고, 부호 포함 합계가 total과 일치하는지 계산해서 보고하라.
B-2) Form Schema(form-schema.ts)에 ResolveSpec 타입과 FieldSchema.resolve 필드를 추가한다.
   - 커브 selector는 rawForm 경로 bind: "curves.risk\_free\_ref" / "curves.credit\_ref" 를 쓰고, resolve: { to:"curves.risk\_free\_curve"|"curves.credit\_curve", kind:"curve" } 를 가진다.
   - valuation-context.schema.json(final)은 curves에 risk\_free\_curve / credit\_curve 포인트 배열만 허용하고 \*\_ref 는 불허하며 additionalProperties:false 로 막는다. input\_hash, model\_version 을 required 로 둔다.
   - valuation-context.draft.schema.json(rawForm)은 curves.\*\_ref 를 허용하고 input\_hash, model\_version 을 required 로 두지 않는다.
B-3) PricingResult의 component key는 표준 12종만 사용한다:
   bond\_value, preferred\_share\_value, conversion\_option\_value, exchange\_option\_value, warrant\_value, redemption\_option\_value, issuer\_call\_value, sale\_claim\_value, stock\_option\_value, conditional\_option\_value, dilution\_effect, total\_fair\_value.
   bond, dilution 같은 약식 명칭을 절대 쓰지 않는다.
2.1) parity는 입력이 아니다. ValuationContext(final/draft) 및 input\_hash 대상에서 parity를 제외하고, PricingResult.key\_parameters.parity 에만 둔다. Conversion 타입/스키마에 parity 필드를 만들지 마라.
2.3) InstrumentType, ModelName, ExerciseStyle, CurveKind, RedemptionSide, JobStatus 는 instrument-types.ts 에서만 선언하고, 나머지 파일(form-schema.ts, valuation-context.ts, pricing-result.ts)은 import 만 한다. 재선언 금지. ModelName에 TRINOMIAL을 넣지 마라.
2.4) PricingResult의 warnings/errors는 JSON Schema(required + "default":\[]), TypeScript(필수 배열), Pydantic(default\_factory=list) 세 곳 모두 정합하게 처리한다.
2.5) input\_hash는 패치 §2.5의 정규화 8단계(대상 추출 / null 제거 / 빈 권리조건 {} / float 10자리 반올림 / 날짜 ISO / 배열 순서 보존 / sort\_keys·공백제거·UTF-8 / SHA-256)를 reproducer.py(Python)와 InputHash.kt(Kotlin)가 동일하게 구현한다.
   - hash-test-vectors.json 에 TV1(CB 기본), TV2(CB 리픽싱), TV3(RCPS), TV4(SO, maturity null), TV5(CSO, tranche) 5케이스를 만든다. 각 케이스는 { name, input, canonical\_blob, expected\_hash } 형식.
   - expected\_hash는 reproducer.py로 1회 생성한 값을 넣고, 동일 입력에 대해 Kotlin 구현도 같은 hash를 내야 함을 주석으로 명시한다.
2.2) market.spot/volatility/dividend\_yield 는 수동 입력이 asset 자동값보다 우선(override)이며, 편차 시 경고 코드 W206을 validation-rules.ts 코드표에 추가한다.

\[작업 절차 — 반드시 이 순서]
1. 먼저, 생성/수정할 파일 목록과(이미 존재하는 파일이 있으면) 변경 계획(diff 요지)을 표로 보여주고 내 승인을 기다린다. 기존 파일 덮어쓰기는 내 승인 전에는 하지 않는다.
2. 승인 후 W1 → W2 → W3 → W4 → W5 → W6 → W7 순서로 생성한다(선행 의존 순서 준수).
3. 각 파일 생성 후, 파일별 1\~2줄 요약 + 아래 Freeze 체크리스트(F-1\~F-8)를 표로 보고한다.
   F-1 Σcomponents=total(허용오차 0.01)  F-2 rawForm/final 스키마 분리  F-3 표준 component key만 사용
   F-4 parity 입력 제외  F-5 enum 단일소스(재선언 0)  F-6 warnings/errors 3측 정합
   F-7 input\_hash Python/Kotlin 동일 로직  F-8 부호 규칙 validator
4. 마지막에 B-1·B-2·B-3 자가검증 결과를 명시적으로 보고한다:
   - B-1: rcps\_case1의 components 합 계산 결과와 total 일치 여부(숫자로)
   - B-2: final 스키마가 curves.risk\_free\_ref 를 거부하고 draft 스키마는 허용하는지(검증 예시)
   - B-3: 생성 파일 전체에서 약식 키(bond, dilution 등) 사용 0건인지

\[코드 규약]
- TypeScript: strict, 타입만(런타임 의존성 추가 금지). enum은 union type + const 배열.
- Python: Pydantic v2, model\_config=ConfigDict(extra="forbid"). result.py는 @model\_validator로 Σ=total과 부호 규칙(issuer\_call/sale\_claim/dilution\_effect ≤ 0)을 런타임 강제.
- JSON Schema: draft 2020-12, $defs로 권리조건 분리, final은 additionalProperties:false.
- 주석/문서 문자열은 한국어로 간단히.

이번 응답에서는 위 \[작업 절차] 1단계(파일 목록 + 변경 계획 표 + 승인 요청)까지만 수행하라.

추가적으로 사용자가 직접 수행해야 할 절차(설치, 코드 입력, 파일 추가, 소스코드 추가 등)이 있다면, 해당 절차를 이해하기 쉽게 단계별로 제시하라.
```

\---

## 첨부 파일 안내 (Cowork에 함께 올릴 것)

|파일|역할|
|-|-|
|`FairValue\_3대계약\_v0.1\_Freeze패치.md`|**유일 기준 문서**. 패치 내용·표준 key·정규화 규칙 포함|
|`FairValue\_3대계약\_명세.md`|원본 계약 명세(타입/스키마 상세 참조용)|
|`FairValue\_샘플입력\_템플릿.xlsx`|CB·RCPS 계약조건/Golden Value(채우면 W7·골든 테스트 입력)|
|`curve\_riskfree\_template.csv` / `curve\_credit\_template.csv`|등급 커브 입력(채우면 resolve/부트스트랩 입력)|

> 샘플 데이터(xlsx·csv)는 W1\~W7 \*\*코드 생성에는 필수가 아니지만\*\*, W7 테스트벡터를 실제 값으로 채우거나 golden fixture를 확정할 때 사용한다. 비어 있어도 W1\~W7 골격 생성은 진행 가능하다.

