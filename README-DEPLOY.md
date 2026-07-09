# FairValue Engine — 배포 가이드 (Phase 6-1)

로컬 4프로세스(Postgres·Python엔진·Kotlin백엔드·Next프론트)를 **docker compose 한 명령**으로
운영하는 배포 패키징입니다. 비상업·초소형(동시 ~10명, 다운 허용) · 예산 연 4만원 기준.

> 구성: 공개 지점은 **Caddy(80/443) 하나**. Postgres·엔진·백엔드·프론트는 외부 미공개.
> 특히 **Python 엔진(:8000)은 무인증이므로 절대 외부에 열지 않습니다**(compose 내부 전용).

```
브라우저 ─HTTPS→ Caddy ─┬─ https://<DOMAIN>      → frontend:3000
                        └─ https://api.<DOMAIN>  → backend:8080 → engine:8000 (내부)
                                                              └→ postgres:5432 (내부)
```

---

## 0. 준비물

- **로컬(Windows)**: Docker Desktop, PowerShell, Git.
- **서버**: 연결제 프로모션 VPS (KVM · RAM 2~3GB · Ubuntu 22/24 · IPv4 1개).
- **비용 감각**: VPS 연 $18~27 수준. 주소는 당분간 **sslip.io(무료)** 사용.

---

## 1. 로컬 리허설 (서버 구매 전 필수 게이트)

서버를 사기 **전에**, 로컬에서 compose 전체가 뜨고 E2E가 통과하는지 확인합니다.

### 1-1. `.env` 작성

저장소 루트에서 (PowerShell):

```powershell
Copy-Item .env.example .env
# JWT_SECRET 생성(64 hex = 32바이트):
$jwt = -join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) })
Write-Output $jwt
notepad .env
```

`.env`에서 최소 아래를 채웁니다(로컬 리허설이므로 DOMAIN은 localhost로):

```dotenv
DOMAIN=localhost
POSTGRES_PASSWORD=local-test-pw
JWT_SECRET=<위에서 생성한 값 붙여넣기>
NEXT_PUBLIC_DEMO_BANNER=true
```

> 로컬 리허설은 **HTTP 포트 직결 override**로 띄웁니다(아래 1-2). Caddy/HTTPS 자체서명 인증서는
> 브라우저의 API 호출(fetch)을 차단하므로, 로컬에서는 Caddy를 빼고 `http://localhost`로 확인합니다.
> `DOMAIN=localhost`는 형식상 넣어둡니다(로컬에선 실제로 쓰이지 않음).

### 1-2. 기동 (로컬 override)

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
docker compose ps                # 서비스 상태(healthy/running) 확인
docker compose logs -f backend   # 마이그레이션·기동 로그(Ctrl+C로 빠져나옴)
```

### 1-3. 접속 & E2E 체크리스트

브라우저에서 **`http://localhost:3000`**. 순서대로 확인:

1. 회원가입(새 조직 코드) → **대시보드** 랜딩(빈 상태 카드).
2. 파라미터 → **수익률 커브** 2종 등록(무위험·신용).
3. 파라미터 → **변동성** 등록.
4. 상품 평가 → 상품 생성 → 계약조건 저장 → **평가 실행**(DONE).
5. 평가 이력 → 상세 → **계산근거** 아코디언 → **보고서 발급**(PDF·Excel 다운로드).

모두 통과하면 **서버 구매 진행 판정**입니다.

### 1-4. 종료/정리

```powershell
docker compose down        # 컨테이너만 내림(데이터 유지)
docker compose down -v     # 데이터(볼륨)까지 삭제(완전 초기화)
```

---

## 2. 서버(VPS) 구매 기준

- **사양**: KVM 가상화 · RAM **2~3GB** · vCPU 1~2 · 디스크 20GB+ · Ubuntu **22.04/24.04**.
- **위치**: LA / San Jose 등(한국에서 지연 적정).
- **네트워크**: 공인 IPv4 **1개** 포함.
- **가격**: 연 **$18~27** 프로모션(연결제).
- 구매 후 받는 것: **서버 IP**(예 `203.0.113.7`) + **root 비밀번호**.

### SSH 접속(Windows 터미널/PowerShell)

```powershell
ssh root@203.0.113.7
# 최초 접속 시 fingerprint 질문 → yes, 이후 root 비밀번호 입력
```

---

## 3. 서버 초기 세팅 (복붙 블록)

SSH로 접속한 뒤 순서대로 실행합니다.

### 3-1. 시스템 업데이트 + Docker 설치

```bash
apt update && apt -y upgrade
curl -fsSL https://get.docker.com | sh      # Docker 공식 설치 스크립트
docker --version && docker compose version   # 설치 확인
```

### 3-2. 스왑 4GB (RAM 2GB에서 빌드 안정화)

```bash
fallocate -l 4G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab   # 재부팅 후에도 유지
free -h                                            # Swap 4.0Gi 확인
```

### 3-3. 소스 내려받기

```bash
apt -y install git
git clone <이-저장소-URL> fairvalue
cd fairvalue
```

### 3-4. `.env` 작성

```bash
cp .env.example .env
# JWT_SECRET 생성:
openssl rand -hex 32
nano .env
```

`nano` 사용법: 붙여넣기 → 값 수정 → `Ctrl+O`(저장) → `Enter` → `Ctrl+X`(종료).

**DOMAIN 구성(sslip.io)**: 서버 IP의 점(`.`)을 하이픈(`-`)으로 바꿉니다.

| 서버 IP | DOMAIN 값 |
|---|---|
| `203.0.113.7` | `203-0-113-7.sslip.io` |

`.env` 최소 채울 값:

```dotenv
DOMAIN=203-0-113-7.sslip.io
POSTGRES_PASSWORD=<강한-DB-비밀번호>
JWT_SECRET=<openssl로-생성한-값>
ENGINE_MODE=real
NEXT_PUBLIC_DEMO_BANNER=true
```

---

## 4. 배포(기동) & 확인

```bash
docker compose up -d --build      # 최초 빌드는 수 분 소요
docker compose ps                 # 5개 서비스 상태
docker compose logs -f caddy      # 인증서 발급 로그(Ctrl+C)
```

브라우저에서:

- 프론트: `https://203-0-113-7.sslip.io`
- API 헬스: `https://api.203-0-113-7.sslip.io/health`

> **인증서 발급 지연**: 첫 접속 시 Caddy가 Let's Encrypt 인증서를 받는 데 10~60초 걸릴 수 있습니다.
> 잠시 후 새로고침. 계속 실패하면 방화벽에서 **80·443 포트가 열려 있는지** 확인
> (일부 VPS는 클라우드 방화벽 별도). `docker compose logs caddy`로 원인 확인.

---

## 5. 데모 데이터 등록 (UI 절차)

1. **회원가입**으로 조직 생성(또는 안내된 데모 계정 로그인).
2. **파라미터 → 수익률 커브**: 무위험·신용 2종 등록.
   - ★ 반드시 **"샘플 커브"** 라벨의 **합성(가상) 수치**를 사용하세요.
     실제 시장데이터(예: KOFIA 실측치)를 공개 데모에 게시하지 않습니다.
3. **파라미터 → 변동성**: 대상 라벨 + 연변동성(합성값) 등록.
4. **상품 평가 → 새 평가**: 가상의 CB/RCPS 등 상품 생성 → 계약조건 입력.
5. **평가 실행**: T&F 및 GS 모형으로 각각 평가.
6. **평가 이력 → 상세 → 보고서 발급**: PDF·Excel 확인.

---

## 6. 운영 카드

**코드 갱신(재배포)**
```bash
cd fairvalue && git pull
docker compose up -d --build     # 프론트는 DOMAIN/코드 변경 시 --build 필수
```

**로그 보기**
```bash
docker compose logs -f backend        # 특정 서비스
docker compose logs --tail=200 caddy
```

**DB 백업(한 줄)**
```bash
docker compose exec -T postgres pg_dump -U fairvalue fairvalue > backup_$(date +%F).sql
```

**전체 초기화(데이터 삭제)**
```bash
docker compose down -v && docker compose up -d --build
```

**실도메인으로 교체**
1. 도메인 구매 후 DNS에 **A레코드 2개**: 루트(`example.com`)·`api`(`api.example.com`) → 서버 IP.
2. `.env`의 `DOMAIN=example.com`으로 변경.
3. `docker compose up -d --build`(프론트 재빌드 필수 — 주소가 번들에 인라인됨).

---

## 7. 보안 최소선 (지켜야 할 것)

- **엔진(:8000)·Postgres·백엔드·프론트는 절대 `ports:`로 공개하지 않는다.** 공개는 Caddy만.
  - 특히 엔진은 **무인증**이라 외부 노출 시 누구나 계산 API를 호출할 수 있습니다.
- **`.env`는 커밋 금지**(이미 `.gitignore` 처리). 저장소엔 `.env.example`만.
- `JWT_SECRET`·`POSTGRES_PASSWORD`는 추측 불가능한 값으로.
- CORS는 `FRONTEND_ORIGIN`(= `https://<DOMAIN>`)로 자동 제한됩니다.

---

## 8. 트러블슈팅

| 증상 | 확인 |
|---|---|
| 접속 시 인증서 경고/오류 | 80·443 방화벽 개방, `docker compose logs caddy`, DNS(sslip.io) 해석 |
| 백엔드 재시작 반복 | `logs backend` — DB 접속(비밀번호)·Flyway 마이그레이션 확인 |
| 평가 실행 실패 | `logs engine`(내부), 백엔드→`http://engine:8000` 도달 여부 |
| 메모리 부족(빌드 중단) | 스왑 4GB 생성 여부(`free -h`) |
| 프론트 API 호출 404/CORS | 프론트가 옛 주소로 빌드됨 → `up -d --build`로 재빌드 |

---

## 참고: 로컬 개발(변경 없음)

기존 개발 흐름은 그대로 유지됩니다.
- 백엔드: `cd backend && ./gradlew bootRun` (프로필 `local`)
- 엔진: `cd pricing-engine && uvicorn app.main:app --port 8000`
- 프론트: `cd frontend && npm run dev`
- DB만 컨테이너: `docker compose -f backend/docker-compose.yml up -d db`
