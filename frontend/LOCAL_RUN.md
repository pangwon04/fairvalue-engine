# FairValue 프론트엔드 — 로컬 실행 절차 (B)

> 대상: CLI 초보. **PowerShell에서 한 줄씩** 실행하고 결과를 확인합니다.
> 전제: 백엔드는 이미 로컬 구동 검증됨 — Postgres(Docker `fv-postgres`, localhost:5432), 백엔드(`gradlew bootRun`, http://localhost:8080, `/health` 200).
> 각 단계: **① 명령 → ② 기대 결과 → ③ 막히면**.

---

## 0. 사전 확인 (백엔드가 떠 있는지)

**① 명령**
```powershell
curl.exe http://localhost:8080/health
```
**② 기대**: `{"status":"UP"}` 비슷한 200 응답.
**③ 막히면**: 백엔드 미기동. 새 PowerShell 창에서 `cd C:\fv_engine_project\backend` → `.\gradlew bootRun`. Postgres가 없으면 `docker start fv-postgres`.

## 1. Node 확인
```powershell
node -v    # v18 이상이면 OK (예: v20.x)
npm -v     # 9 이상이면 OK
```
**③ 막히면**: Node 미설치 → https://nodejs.org 에서 LTS 설치 후 PowerShell 재시작.

## 2. 프론트 의존성 설치
```powershell
cd C:\fv_engine_project\frontend
npm install
```
**② 기대**: 몇 분 후 `added N packages`. `node_modules` 폴더 생성.
**③ 막히면**: 사내 프록시면 `npm config set registry https://registry.npmjs.org/` 후 재시도.

## 3. 환경변수 (백엔드 주소)
```powershell
Copy-Item .env.local.example .env.local
```
`.env.local` 안에 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` 이 있으면 OK(수정 불필요).

## 4. 개발 서버 실행
```powershell
npm run dev
```
**② 기대**: `▲ Next.js ... Local: http://localhost:3000`. 이 창은 **켜둔 채로** 둡니다(끄면 사이트 종료).
**③ 막히면**: 3000 포트 사용 중이면 `npm run dev -- -p 3001` 후 아래 주소를 3001로.

## 5. 브라우저 — 회원가입/로그인
브라우저에서 **http://localhost:3000** 열기 → 자동으로 `/login`.
- **회원가입** 클릭 → 이메일 / 비밀번호 / **조직코드(새 코드 아무거나, 예: `ACME`)** 입력 → 가입.
**② 기대**: 가입 후 자동 로그인되어 **상품 목록** 화면(사이드바 "상품" + "+ CB 상품 생성").
**③ 막히면**: 콘솔(F12)에 CORS/네트워크 에러면 → 아래 [CORS] 참고.

## 6. ★ 평가용 커브 업로드 (저장소 실제 커브 사용)

CB가 실제로 평가되려면 DB에 **무위험(RISK_FREE) + 신용(CREDIT)** 커브가 있어야 합니다(커브 관리 UI는 다음 슬라이스). 저장소의 **실제 커브 CSV**(`golden-values/curves/`)를 그대로 업로드합니다. **평가기준일과 커브 as_of를 2022-10-13으로 맞춥니다.**

**리포지토리 루트에서** 실행:
```powershell
cd C:\fv_engine_project

# (1) 로그인해서 토큰 얻기 (5단계에서 만든 계정)
$login = Invoke-RestMethod -Uri http://localhost:8080/auth/login -Method Post `
  -ContentType "application/json" `
  -Body '{"email":"you@example.com","pw":"여기비밀번호"}'
$token = $login.token
$token   # 긴 문자열이 출력되면 OK

# (2) 무위험 커브 업로드 (2022-10-13)
curl.exe -X POST http://localhost:8080/curves `
  -H "Authorization: Bearer $token" `
  -F "as_of=2022-10-13" -F "kind=RISK_FREE" -F "source=KISPRICING" `
  -F "file=@golden-values/curves/risk_free_2022-10-13.csv"

# (3) 신용 커브 업로드 (2022-10-13, 등급 B-)
curl.exe -X POST http://localhost:8080/curves `
  -H "Authorization: Bearer $token" `
  -F "as_of=2022-10-13" -F "kind=CREDIT" -F "grade=B-" -F "source=KISPRICING" `
  -F "file=@golden-values/curves/credit_2022-10-13.csv"
```
**② 기대**: 각 업로드가 `{"upload_id":..., "validation":[]}` (201). validation이 비었으면 정상.
**③ 막히면**:
- 파일 경로 오류 → 반드시 `C:\fv_engine_project`(리포 루트)에서 실행. `dir golden-values\curves` 로 파일 확인.
- 401 Unauthorized → `$token` 이 비었음. (1)을 다시(이메일/비번 정확히).
- 커브 CSV는 이미 업로드형(맨 위 `# as_of=...` 메타 + `tenor_years,rate_percent`)이라 그대로 사용합니다.

## 7. 브라우저 — CB 생성 → 입력 → 평가 → 결과

1. **+ CB 상품 생성** → 상품명·발행사 입력 → 생성. 입력폼(위저드)으로 이동.
2. **기본정보**: 발행사·상품명·**평가기준일 = `2022-10-13`** (★ 업로드한 커브 as_of와 동일해야 함).
3. **계약조건**: 발행일·만기일(발행일 이후)·발행금액·액면·표면이자율·이자주기 등(기본값 채워져 있음).
4. **기초자산**: 자산 ID는 이번 슬라이스에선 숫자 수동 입력(예: `880`).
5. **평가 파라미터**:
   - **무위험수익률 커브** 드롭다운 → 방금 올린 `#.. · RISK_FREE · 2022-10-13` 선택.
   - **신용등급 커브** → `#.. · CREDIT B- · 2022-10-13` 선택.
   - 변동성(예: 45), 배당수익률(0), 기준일 주가(예: 3260) 입력.
6. **전환권**: 전환가액(예: 3260)·전환청구 시작일 등.
7. 나머지 스텝(리픽싱·상환권·모형)은 기본값으로 두어도 됩니다. **평가모형 = TF_LATTICE**.
8. **입력 저장** 클릭 → "저장 완료"(또는 서버 검증 메시지). 오류 메시지가 있으면 해당 항목 수정.
9. **평가 실행** 클릭 → "평가 중…" → 잠시 후 **평가 결과** 카드 표시.

**② 기대(정상)**:
- 상단에 **공정가치 합계(total)** 와 **단위당(per unit)** 숫자.
- **컴포넌트 분해(12키)** 표: 채권가치·전환옵션·상환권 등, 하단에 **Σ = total 정합 ✓**.
**③ 막히면**:
- 평가 실패에 "커브" 관련 메시지 → 6단계 커브 업로드 또는 평가기준일(2022-10-13) 정합 재확인.
- "검증 오류로 평가 중단" → 폼의 빨간 메시지 항목 수정 후 재실행.
- 상태가 계속 RUNNING/QUEUED → 백엔드 로그(bootRun 창) 확인.

---

## [CORS] 프론트(3000)가 백엔드(8080)에 막힐 때
백엔드에 CORS는 이미 설정되어 있습니다(확인됨). 그래도 브라우저 콘솔에 `CORS`/`blocked by CORS policy` 가 뜨면:
- 백엔드가 `http://localhost:3000` origin을 허용하는지 확인(백엔드 SecurityConfig/CORS 설정).
- 프론트를 다른 포트로 띄웠다면(3001 등) 그 origin도 허용 필요.

## 참고
- JWT는 브라우저 localStorage(`fv_token`)에 저장됩니다(속도 우선; 프로덕션 전 httpOnly 쿠키로 강화 예정).
- 이 슬라이스는 **CB 한 상품** 수직 관통입니다. 다른 상품은 스키마(`src/forms/productSchemas/*.json`)만 추가하면 같은 렌더러로 동작합니다.
