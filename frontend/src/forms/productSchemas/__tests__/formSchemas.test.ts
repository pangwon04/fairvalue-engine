// ===========================================================================
// FairValue Engine — Product Form Schema 검증 테스트 (W8 1차)
// ---------------------------------------------------------------------------
// node:test + node:assert 기반(추가 러너 의존성 없음).
//   실행: tsc 로 컴파일 후 `node --test`, 또는 vitest/jest 환경에서도 호환.
// 검증 대상(프롬프트 [검증]):
//   1) CB·RCPS·CPS 3개 스키마가 FormSchema 타입 필수 키에 부합
//   2) 모든 curveSelector 가 resolve.kind="curve" + 올바른 to 경로
//   3) 모든 assetSearch 가 resolve.kind="asset"
//   4) parity 필드는 bind 없음 + type="computed"
//   5) 모든 validations[].rule 이 표준 집합(20종)에 속함 — 미정의 rule 0건
//   6) CPS 에는 redemption/issuer_call/sale_claim 스텝이 없다
// ===========================================================================

import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

type AnyField = {
  key: string;
  label: string;
  type: string;
  bind?: string;
  resolve?: { to?: string; kind?: string };
  validations?: { rule: string }[];
  columns?: AnyField[];
};
type AnyStep = { id: string; title: string; fields: AnyField[] };
type AnySchema = { product: string; version: string; title: string; steps: AnyStep[] };

function load(name: string): AnySchema {
  const raw = readFileSync(join(__dirname, '..', name + '.json'), 'utf-8');
  return JSON.parse(raw) as AnySchema;
}

const cb = load('cb');
const rcps = load('rcps');
const cps = load('cps');
const SCHEMAS: Record<string, AnySchema> = { cb: cb, rcps: rcps, cps: cps };

// validation-rules.ts(W3) 표준 20종. W206 은 issue code 이므로 폼 validation 대상 아님.
const STANDARD_RULES = new Set<string>([
  'required', 'min', 'max', 'positive',
  'dateOrder', 'dateWithin',
  'enum', 'percentageRange',
  'dependencyRequired', 'mutuallyExclusive', 'showWhenRequired',
  'curveRequired', 'assetRequired', 'modelCompatibility',
  'refixingFloorCheck', 'maturityAfterIssueDate',
  'exercisePeriodWithinMaturity',
  'volatilityPositive', 'pricePositive', 'dilutionRange',
]);

// curveSelector bind(rawForm) -> 기대 resolve.to(final) 매핑.
const CURVE_RESOLVE_TO: Record<string, string> = {
  'curves.risk_free_ref': 'curves.risk_free_curve',
  'curves.credit_ref': 'curves.credit_curve',
};

/** 스텝 내 field + table 컬럼까지 평탄화. */
function allFields(schema: AnySchema): AnyField[] {
  const out: AnyField[] = [];
  for (const step of schema.steps) {
    for (const f of step.fields) {
      out.push(f);
      if (Array.isArray(f.columns)) out.push.apply(out, f.columns);
    }
  }
  return out;
}

// --- 검증 1: FormSchema 필수 키 부합 ---
test('1. 3개 스키마가 FormSchema 필수 키에 부합한다', () => {
  for (const name of Object.keys(SCHEMAS)) {
    const s = SCHEMAS[name];
    assert.equal(typeof s.product, 'string', name + ': product');
    assert.equal(s.version, '0.1.0', name + ': version 은 0.1.0');
    assert.equal(typeof s.title, 'string', name + ': title');
    assert.ok(Array.isArray(s.steps) && s.steps.length > 0, name + ': steps[]');
    for (const step of s.steps) {
      assert.equal(typeof step.id, 'string', name + '.' + step.id + ': step.id');
      assert.equal(typeof step.title, 'string', name + '.' + step.id + ': step.title');
      assert.ok(Array.isArray(step.fields), name + '.' + step.id + ': step.fields[]');
      for (const f of step.fields) {
        assert.equal(typeof f.key, 'string', name + '.' + step.id + '.' + f.key + ': field.key');
        assert.equal(typeof f.label, 'string', name + '.' + step.id + '.' + f.key + ': field.label');
        assert.equal(typeof f.type, 'string', name + '.' + step.id + '.' + f.key + ': field.type');
      }
    }
  }
  assert.equal(cb.product, 'CB');
  assert.equal(rcps.product, 'RCPS');
  assert.equal(cps.product, 'CPS');
});

// --- 검증 2: curveSelector resolve.kind="curve" + 올바른 to ---
test('2. 모든 curveSelector 가 resolve.kind=curve 와 올바른 to 경로를 가진다', () => {
  let count = 0;
  for (const name of Object.keys(SCHEMAS)) {
    for (const f of allFields(SCHEMAS[name])) {
      if (f.type !== 'curveSelector') continue;
      count++;
      assert.ok(f.bind && CURVE_RESOLVE_TO[f.bind], name + '.' + f.key + ': bind 가 rawForm 커브 경로');
      assert.ok(f.resolve, name + '.' + f.key + ': resolve 존재');
      assert.equal(f.resolve!.kind, 'curve', name + '.' + f.key + ': resolve.kind=curve');
      assert.equal(f.resolve!.to, CURVE_RESOLVE_TO[f.bind!], name + '.' + f.key + ': resolve.to 일치');
    }
  }
  assert.ok(count >= 6, 'curveSelector 최소 6개(3종x2) — 실제 ' + count);
});

// --- 검증 3: assetSearch resolve.kind="asset" ---
test('3. 모든 assetSearch 가 resolve.kind=asset 를 가진다', () => {
  let count = 0;
  for (const name of Object.keys(SCHEMAS)) {
    for (const f of allFields(SCHEMAS[name])) {
      if (f.type !== 'assetSearch') continue;
      count++;
      assert.ok(f.resolve, name + '.' + f.key + ': resolve 존재');
      assert.equal(f.resolve!.kind, 'asset', name + '.' + f.key + ': resolve.kind=asset');
      assert.equal(f.resolve!.to, 'market.asset_id', name + '.' + f.key + ': resolve.to=market.asset_id');
    }
  }
  assert.equal(count, 3, 'assetSearch 는 3종 각 1개 — 실제 ' + count);
});

// --- 검증 4: parity 는 type="computed" + bind 없음 ---
test('4. parity 필드는 type=computed 이고 bind 가 없다', () => {
  let count = 0;
  for (const name of Object.keys(SCHEMAS)) {
    for (const f of allFields(SCHEMAS[name])) {
      if (f.key !== 'parity') continue;
      count++;
      assert.equal(f.type, 'computed', name + '.parity: type=computed');
      assert.equal(f.bind, undefined, name + '.parity: bind 없음');
    }
  }
  assert.equal(count, 3, 'parity 는 3종 각 1개 — 실제 ' + count);
});

// --- 검증 5: 모든 validations[].rule 이 표준 집합 소속 (미정의 0건) ---
test('5. 모든 validations[].rule 이 표준 20종 집합에 속한다', () => {
  const unknown: string[] = [];
  for (const name of Object.keys(SCHEMAS)) {
    for (const f of allFields(SCHEMAS[name])) {
      const vs = f.validations || [];
      for (const v of vs) {
        if (!STANDARD_RULES.has(v.rule)) unknown.push(name + '.' + f.key + ': ' + v.rule);
      }
    }
  }
  assert.deepEqual(unknown, [], '미정의 rule: ' + unknown.join(', '));
});

// --- 검증 6: CPS 에 redemption/issuer_call/sale_claim 스텝 없음 ---
test('6. CPS 에는 redemption/issuer_call/sale_claim 스텝이 없다', () => {
  const forbidden = ['rights.redemption', 'rights.issuer_call', 'rights.sale_claim'];
  const ids = cps.steps.map(function (s) { return s.id; });
  for (const fid of forbidden) {
    assert.ok(ids.indexOf(fid) === -1, 'CPS 에 ' + fid + ' 스텝이 존재하면 안 됨');
  }
  const cbIds = cb.steps.map(function (s) { return s.id; });
  for (const fid of forbidden) {
    assert.ok(cbIds.indexOf(fid) !== -1, 'CB 에는 ' + fid + ' 스텝이 있어야 함');
  }
  assert.ok(ids.indexOf('rights.conversion') !== -1);
  assert.ok(ids.indexOf('rights.refixing') !== -1);
});
