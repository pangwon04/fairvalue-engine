# ===========================================================================
# FairValue Engine — input_hash 정본 구현 (W7, §2.5)
# ---------------------------------------------------------------------------
# 정규화 8단계(패치 §2.5). Backend Kotlin(InputHash.kt)도 동일 로직을 구현하며
# hash-test-vectors.json 으로 양측 동일 hash 를 CI 에서 교차검증한다.
#   1. 대상 추출   : 해시 대상 필드만 추출(§3.8). ref·metadata·org_id·instrument_id·source 제외.
#   2. null 제거   : 값이 None 인 키는 삭제(존재/부재 차이로 인한 해시 흔들림 방지).
#   3. 빈 권리조건 : enabled:false 권리 객체는 {} 로 정규화(잔여 키 삭제).
#   4. float 반올림: 모든 number 소수 10자리 반올림. 정수는 그대로.
#   5. 날짜       : 전부 ISO 'YYYY-MM-DD' 문자열(직렬화 default=str).
#   6. 배열       : 순서 보존(커브 포인트 정렬 금지).
#   7. 직렬화      : sort_keys=True, separators=(",",":"), ensure_ascii=False(UTF-8).
#   8. 해시       : SHA-256 → 소문자 hex 64자.
# ===========================================================================
from __future__ import annotations

import hashlib
import json


def round_floats(o, nd: int = 10):
    """모든 float 를 nd 자리로 반올림하고, dict 에서 None 값 키는 제거한다(2·4단계)."""
    if isinstance(o, bool):
        # bool 은 int 의 서브클래스이므로 먼저 분기(반올림 대상 아님).
        return o
    if isinstance(o, float):
        return round(o, nd)
    if isinstance(o, dict):
        return {k: round_floats(v, nd) for k, v in o.items() if v is not None}
    if isinstance(o, (list, tuple)):
        return [round_floats(v, nd) for v in o]
    return o


def normalize_rights(rights: dict) -> dict:
    """3단계: enabled:false 권리 객체는 빈 객체로 정규화. None 권리는 제거."""
    out: dict = {}
    for k, v in (rights or {}).items():
        if isinstance(v, dict) and v.get("enabled") is False:
            out[k] = {}                       # 비활성 권리 → 빈 객체(잔여 키 삭제)
        elif v is not None:
            out[k] = v
    return out


def canonicalize(ctx: dict) -> dict:
    """1단계: 해시 대상만 추출 + 정규화. ref/metadata/org_id/instrument_id/source 제외."""
    market = ctx.get("market", {}) or {}
    curves = ctx.get("curves", {}) or {}
    pick = {
        "instrument_type": ctx["instrument_type"],
        "valuation_date": ctx["valuation_date"],
        "terms": ctx.get("terms", {}),
        "rights": normalize_rights(ctx.get("rights", {})),
        "market": {
            k: market.get(k)
            for k in ["asset_id", "spot", "volatility", "dividend_yield",
                      "shares_outstanding", "as_of"]
        },
        "curves": {
            # 포인트 배열 스냅샷만 대상. *_ref 는 비대상. curve_source 표시문구 제외.
            "risk_free_curve": curves.get("risk_free_curve"),
            "credit_curve": curves.get("credit_curve"),
            "interpolation_method": curves.get("interpolation_method"),
            "curve_version": curves.get("curve_version"),
        },
        "model": ctx["model"],
        "model_version": ctx["model_version"],
        "seed": ctx["seed"],
        "options": ctx.get("options", {}) or {},
    }
    # null 제거는 round_floats 내부에서 수행(2·4단계).
    return round_floats(pick, 10)


def canonical_blob(ctx: dict) -> str:
    """정규화된 컨텍스트의 정본 직렬화 문자열(7단계)."""
    return json.dumps(
        canonicalize(ctx),
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
        default=str,                          # date → ISO 문자열
    )


def input_hash(ctx: dict) -> str:
    """final ValuationContext(dict) → input_hash(소문자 hex 64자)."""
    return hashlib.sha256(canonical_blob(ctx).encode("utf-8")).hexdigest()


if __name__ == "__main__":
    # hash-test-vectors.json 의 expected_hash 를 생성/검증하는 CLI.
    #   python -m app.reproducer <path/to/hash-test-vectors.json> [--write]
    # 비교 모드(--write 없음): 불일치 1건 이상이면 exit 1, 전부 일치면 exit 0.
    import sys
    from pathlib import Path

    args = sys.argv[1:]
    if not args:
        print("usage: python -m app.reproducer <hash-test-vectors.json> [--write]")
        raise SystemExit(1)

    path = Path(args[0])
    write = "--write" in args
    data = json.loads(path.read_text(encoding="utf-8"))

    changed = False
    mismatches = []
    for case in data["vectors"]:
        blob = canonical_blob(case["input"])
        h = hashlib.sha256(blob.encode("utf-8")).hexdigest()
        prev = case.get("expected_hash")
        status = "OK" if prev == h else ("SET" if prev in (None, "", "TBD") else "MISMATCH")
        print("{:<24} {:<9} {}".format(case["name"], status, h))
        if write:
            case["canonical_blob"] = blob
            case["expected_hash"] = h
            changed = True
        elif status == "MISMATCH":
            print("  expected={}".format(prev))
            mismatches.append(case["name"])

    if write and changed:
        out = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
        path.write_text(out, encoding="utf-8")
        print("\n[written] {}".format(path))
        raise SystemExit(0)

    if mismatches:
        print("\n[FAIL] expected_hash 불일치 {}건: {}".format(len(mismatches), ", ".join(mismatches)))
        raise SystemExit(1)
    print("\n[OK] {}개 벡터 전부 expected_hash 일치".format(len(data["vectors"])))
    raise SystemExit(0)
