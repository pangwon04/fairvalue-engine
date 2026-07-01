// ===========================================================================
// FairValue Engine — bind 일치 검증기 (W10)
// ---------------------------------------------------------------------------
// frontend/src/forms/productSchemas/*.json 7종을 정적 분석한다.
//   - rawForm 허용 경로 집합은 shared/schemas/valuation-context.draft.schema.json 에서,
//   - final 경로 집합은 shared/schemas/valuation-context.schema.json 에서 동적 생성한다.
// 네트워크/런타임 앱 불필요. 순수 정적 분석.
//
// 검사 항목:
//   1) bind 경로 유효성  — 입력 field 의 bind 가 허용 rawForm 경로 집합에 속하는지
//   2) resolve 규칙      — curveSelector(curve)/assetSearch(asset) bind·resolve 짝 일치
//   3) final 일치        — resolve.to 가 final 스키마에 실제 존재하는지
//   4) parity 규칙       — type=computed 필드는 bind 가 없어야 함
//   5) bind 필수         — computed/readonly 외 모든 입력 field 는 bind 필수
//   6) rule 무결성       — validations[].rule 이 표준 20종(+W206)에 속하는지
//
// 위반이 하나라도 있으면 목록 출력 후 exit 1, 전부 통과면 exit 0.
// ===========================================================================

import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..', '..');
const SCHEMAS_DIR = join(REPO_ROOT, 'frontend', 'src', 'forms', 'productSchemas');
const SHARED_DIR = join(REPO_ROOT, 'shared', 'schemas');

// --- 타입 ---
export interface Field {
  key: string;
  label?: string;
  type: string;
  bind?: string;
  resolve?: { to?: string; kind?: string };
  validations?: { rule: string }[];
  columns?: Field[];
}
export interface Step { id: string; title?: string; fields: Field[] }
export interface FormSchema { product: string; version?: string; title?: string; steps: Step[] }

// --- 검증 컨텍스트 (스키마에서 생성한 허용 집합) ---
export interface VerifyContext {
  rawExact: Set<string>;          // 정확 일치 허용 (valuation_date, model, seed, ...)
  rawPrefixes: string[];          // prefix 허용 (terms., rights., market., options., metadata.)
  curveBindAllowed: Set<string>;  // curves.* 중 bind 허용 (ref/method 등, *_curve 제외)
  finalPaths: Set<string>;        // final 스키마에 존재하는 경로 (resolve.to 대상)
  standardRules: Set<string>;     // validations[].rule 표준 집합
  curveResolveMap: Record<string, string>; // bind(ref) → 기대 resolve.to(curve)
}

// validation-rules.ts(W3) 표준 20종 + W206(패치 2.2 issue code, 허용).
const STANDARD_RULES = [
  'required', 'min', 'max', 'positive',
  'dateOrder', 'dateWithin',
  'enum', 'percentageRange',
  'dependencyRequired', 'mutuallyExclusive', 'showWhenRequired',
  'curveRequired', 'assetRequired', 'modelCompatibility',
  'refixingFloorCheck', 'maturityAfterIssueDate',
  'exercisePeriodWithinMaturity',
  'volatilityPositive', 'pricePositive', 'dilutionRange',
  'W206',
];

const CURVE_RESOLVE_MAP: Record<string, string> = {
  'curves.risk_free_ref': 'curves.risk_free_curve',
  'curves.credit_ref': 'curves.credit_curve',
};

function isObjectType(t: unknown): boolean {
  if (t === 'object') return true;
  if (Array.isArray(t) && t.includes('object')) return true;
  return false;
}

/** draft 스키마(rawForm)에서 허용 경로 규칙을 생성한다. */
export function buildRawFormRules(draft: any): {
  rawExact: Set<string>; rawPrefixes: string[]; curveBindAllowed: Set<string>;
} {
  const rawExact = new Set<string>();
  const rawPrefixes: string[] = [];
  const curveBindAllowed = new Set<string>();
  const props = (draft && draft.properties) || {};
  for (const key of Object.keys(props)) {
    const val = props[key];
    if (key === 'curves') {
      const cprops = (val && val.properties) || {};
      for (const ck of Object.keys(cprops)) {
        // *_curve(포인트 배열)는 final 전용이므로 bind 허용에서 제외.
        if (!ck.endsWith('curve')) curveBindAllowed.add('curves.' + ck);
      }
    } else if (isObjectType(val && val.type)) {
      rawPrefixes.push(key); // terms / rights / market / options / metadata
    } else {
      rawExact.add(key);     // valuation_date / model / seed / instrument_type / ...
    }
  }
  return { rawExact, rawPrefixes, curveBindAllowed };
}

/** final 스키마에서 점(.)경로 집합을 생성한다(properties 재귀). */
export function buildFinalPaths(finalSchema: any): Set<string> {
  const out = new Set<string>();
  function walk(props: any, prefix: string) {
    if (!props) return;
    for (const k of Object.keys(props)) {
      const path = prefix ? prefix + '.' + k : k;
      out.add(path);
      const v = props[k];
      if (v && v.properties) walk(v.properties, path);
    }
  }
  walk(finalSchema && finalSchema.properties, '');
  return out;
}

export function buildContext(draft: any, finalSchema: any): VerifyContext {
  const { rawExact, rawPrefixes, curveBindAllowed } = buildRawFormRules(draft);
  return {
    rawExact,
    rawPrefixes,
    curveBindAllowed,
    finalPaths: buildFinalPaths(finalSchema),
    standardRules: new Set(STANDARD_RULES),
    curveResolveMap: CURVE_RESOLVE_MAP,
  };
}

function isAllowedRawBind(bind: string, ctx: VerifyContext): boolean {
  if (ctx.rawExact.has(bind)) return true;
  if (ctx.curveBindAllowed.has(bind)) return true;
  for (const p of ctx.rawPrefixes) {
    if (bind === p || bind.startsWith(p + '.')) return true;
  }
  return false;
}

/** 단일 폼 스키마 검증. 위반 메시지 배열을 반환(빈 배열=통과). */
export function verifySchema(name: string, schema: FormSchema, ctx: VerifyContext): string[] {
  const v: string[] = [];
  const tag = (step: string, key: string) => `[${name}] ${step}.${key}`;

  for (const step of schema.steps || []) {
    for (const f of step.fields || []) {
      const isComputed = f.type === 'computed';
      const isReadonly = f.type === 'readonly';

      // 4) parity/computed: bind 없어야 함
      if (isComputed && f.bind != null) {
        v.push(`${tag(step.id, f.key)}: computed 필드에 bind 가 있으면 안 됨 (bind=${f.bind})`);
      }

      // 5) bind 필수 (computed/readonly 제외)
      if (!isComputed && !isReadonly && f.bind == null) {
        v.push(`${tag(step.id, f.key)}: 입력 field(type=${f.type})에 bind 가 없음`);
      }

      // 1) bind 경로 유효성
      if (!isComputed && f.bind != null && !isAllowedRawBind(f.bind, ctx)) {
        v.push(`${tag(step.id, f.key)}: 허용되지 않은 rawForm bind 경로 (bind=${f.bind})`);
      }

      // 2) resolve 규칙 — curveSelector
      if (f.type === 'curveSelector') {
        if (!f.bind || !(f.bind in ctx.curveResolveMap)) {
          v.push(`${tag(step.id, f.key)}: curveSelector bind 는 curves.risk_free_ref|credit_ref 여야 함 (bind=${f.bind})`);
        }
        if (!f.resolve || f.resolve.kind !== 'curve') {
          v.push(`${tag(step.id, f.key)}: curveSelector 는 resolve.kind="curve" 필요`);
        } else if (f.bind && ctx.curveResolveMap[f.bind] && f.resolve.to !== ctx.curveResolveMap[f.bind]) {
          v.push(`${tag(step.id, f.key)}: resolve.to 불일치 (기대=${ctx.curveResolveMap[f.bind]}, 실제=${f.resolve.to})`);
        }
      }

      // 2) resolve 규칙 — assetSearch
      if (f.type === 'assetSearch') {
        if (!f.resolve || f.resolve.kind !== 'asset') {
          v.push(`${tag(step.id, f.key)}: assetSearch 는 resolve.kind="asset" 필요`);
        }
      }

      // 3) final 일치 — resolve.to 가 final 스키마에 존재
      if (f.resolve && f.resolve.to && !ctx.finalPaths.has(f.resolve.to)) {
        v.push(`${tag(step.id, f.key)}: resolve.to 가 final 스키마에 없음 (to=${f.resolve.to})`);
      }

      // 6) rule 무결성 — field + table 컬럼
      for (const rule of (f.validations || [])) {
        if (!ctx.standardRules.has(rule.rule)) {
          v.push(`${tag(step.id, f.key)}: 미정의 validation rule (${rule.rule})`);
        }
      }
      for (const col of (f.columns || [])) {
        for (const rule of (col.validations || [])) {
          if (!ctx.standardRules.has(rule.rule)) {
            v.push(`${tag(step.id, f.key)}.${col.key}: 미정의 validation rule (${rule.rule})`);
          }
        }
      }
    }
  }
  return v;
}

function readJson(path: string): any {
  return JSON.parse(readFileSync(path, 'utf-8'));
}

/** 7개 폼 파일을 읽어 전체 검증. */
export function verifyAll(): { violations: string[]; fileCount: number } {
  const draft = readJson(join(SHARED_DIR, 'valuation-context.draft.schema.json'));
  const finalSchema = readJson(join(SHARED_DIR, 'valuation-context.schema.json'));
  const ctx = buildContext(draft, finalSchema);

  const files = readdirSync(SCHEMAS_DIR).filter((f) => f.endsWith('.json'));
  const violations: string[] = [];
  for (const file of files.sort()) {
    const schema = readJson(join(SCHEMAS_DIR, file)) as FormSchema;
    violations.push(...verifySchema(file, schema, ctx));
  }
  return { violations, fileCount: files.length };
}

// --- CLI 진입 ---
const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  const { violations, fileCount } = verifyAll();
  if (violations.length === 0) {
    console.log(`verify-bind: PASS — ${fileCount}개 폼, 위반 0건`);
    process.exit(0);
  } else {
    console.error(`verify-bind: FAIL — ${violations.length}건 위반`);
    for (const m of violations) console.error('  - ' + m);
    process.exit(1);
  }
}
