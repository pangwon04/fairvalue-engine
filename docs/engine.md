# 평가엔진 (pricing-engine)

Python/FastAPI로 만든 순수 계산 서버다. 상태도 DB도 인증도 없다. 백엔드가 넘긴 `ValuationContext`(dict)를 받아 `PricingResult`(dict)를 돌려준다. 계산기는 표준 라이브러리 `math`만 쓰는 순수 파이썬 격자라 numpy 같은 외부 수치 라이브러리가 없다.

```text
pricing-engine/
├── app/
│   ├── main.py            # FastAPI 진입점 (/health, /price)
│   ├── context.py         # ValuationContext 모델 (계약 ②)
│   ├── result.py          # PricingResult 모델 (계약 ③)
│   ├── reproducer.py      # input_hash 정본 구현
│   └── models/
│       ├── registry.py            # 모델 라우팅(다상품·다모형 확장점)
│       ├── tf_lattice.py          # Tsiveriotis-Fernandes 격자
│       ├── gs_model.py            # Goldman-Sachs 격자
│       ├── cb/rcps/cps/eb/bw_calculator.py  # 상품별 계산기
│       ├── curve_bootstrap.py     # 커브 부트스트래핑(par→spot→forward)
│       ├── sensitivity.py         # 민감도 3×3
│       └── mc.py                  # 교차검증용 GBM 몬테카를로
└── tests/                 # pytest 82
```

## 진입점 — `app/main.py`

FastAPI 앱 하나에 엔드포인트 둘이다. `GET /health`는 살아있는지와 지원 상품 목록(CB·RCPS·CPS·EB·BW)을 돌려주고, `POST /price`는 요청 바디(ValuationContext dict)를 그대로 받아 `registry.LIBRARY.calculate(ctx)`로 넘긴다. 이 파일은 HTTP 래핑만 하고 계산 로직은 없다.

## 계약 모델 — `context.py`, `result.py`

두 파일이 엔진의 입출력 계약(Pydantic)이다. `context.py`는 들어오는 ValuationContext를, `result.py`는 나가는 PricingResult를 정의한다. PricingResult는 `bond_value`·`conversion_option_value` 등 **12개 구성요소**와 총공정가치, 그리고 트리·부트스트래핑·민감도·재현정보(재현 가능한 근거)를 담는다. 구성요소의 합은 총액과 일치해야 한다.

## 모델 라우팅 — `models/registry.py`

`ModelLibrary`가 `model_name`(예: `TF_LATTICE`, `GS`)과 상품 유형을 보고 알맞은 계산기 함수로 보낸다. 새 모형이나 상품을 붙일 때 여기 등록만 추가하면 되도록 확장점으로 뒀다. 기존 라우팅은 건드리지 않는다.

## 격자 — `models/tf_lattice.py`, `models/gs_model.py`

둘 다 CRR 이항격자(`u = exp(σ√Δt)`, `d = 1/u`)를 공통 골격으로 backward induction 한다.

- **tf_lattice.py** — Tsiveriotis-Fernandes. 상품을 지분요소(주식가치, `rf` 할인)와 부채요소(현금흐름, `rd` 할인)로 나눠 각각 굴린다. 노드마다 보유자의 전환·상환청구와 발행자 콜을 최적행사로 반영한다.
- **gs_model.py** — Goldman-Sachs. 요소를 나누지 않고 노드별 전환확률 `q`를 전파하며 `y = q·rf + (1−q)·rd`로 한 트리를 할인한다.

## 상품 계산기 — `models/*_calculator.py`

상품마다 파일 하나다(cb·rcps·cps·eb·bw). 격자를 상품 약정(전환·상환·교환·신주인수권 등)에 맞게 조립하고, T&F와 GS 두 분기를 갖는다. 산출을 12개 구성요소로 분해하고 근거 트리를 채우는 것도 여기서 한다.

## 커브·민감도 — `models/curve_bootstrap.py`, `models/sensitivity.py`

- **curve_bootstrap.py** — 업로드된 par 커브를 YTM → Spot → Forward로 변환해 스텝별 할인율을 만든다.
- **sensitivity.py** — 변동성 × 기초자산 3×3 그리드로 결과가 어떻게 움직이는지 표를 뽑는다.

## 교차검증 — `models/mc.py`

격자의 전환옵션 조각을 독립적으로 확인하려는 GBM 몬테카를로다. 통제된 부분문제(European 행사)에서 격자와 1~2% 안으로 수렴하는지 본다. 표준 `random`만 쓰고 시드를 고정해 재현 가능하다.

## 재현성 — `app/reproducer.py`

input_hash의 **정본** 구현이다. 정규화 규칙(키 정렬, 정수/실수 구분, null 제거 등)에 따라 ValuationContext를 직렬화하고 SHA-256으로 해싱한다. 백엔드의 코틀린 구현(`contracts/InputHash.kt`)이 같은 해시를 내야 하며, 이를 `shared/schemas/hash-test-vectors.json`으로 교차검증한다.

## 테스트 — `tests/`

pytest 82개. 상품별 계산기 테스트, 골든/외부 대조(`test_golden.py`, `test_rcps_calculator.py`), 항등식·메커니즘(`test_gs.py`), 부트스트래핑·민감도·트리, 해시 벡터(`test_hash_vectors.py`)로 나뉜다. 검증 기준은 루트 README의 "테스트 & 검증"에 요약돼 있다.
