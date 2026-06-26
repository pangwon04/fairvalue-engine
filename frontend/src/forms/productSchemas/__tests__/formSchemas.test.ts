// ===========================================================================
// FairValue Engine — Product Form Schema 검증 테스트 (W8 1차+2차, 7종 전체)
// ---------------------------------------------------------------------------
// node:test + node:assert 기반(추가 러너 의존성 없음).
//   실행: tsc 로 컴파일 후 `node --test`, 또는 vitest/jest 환경에서도 호환.
// 검증 대상(프롬프트 [검증], 7종 기준):
//   1) 7개 스키마가 FormSchema 타입 필수 키에 부합
//   2) 모든 curveSelector 가 resolve.kind="curve" + 올바른 to 경로
//   3) 모든 assetSearch 가 resolve.kind="asset"
//   4) parity 필드는 bind 없음 + type="computed"
//   5) 모든 validations[].rule 이 표준 집합(20종)에 속함 — 미정의 rule 0건
//   6) CPS 에는 redemption/issuer_call/sale_claim 스텝이 없다
//   7) EB: conversion 없음 / exchange 있음 / market 라벨에 "교환대상"
//   8) BW: warrant + dilution 있음 / separable 토글 존재
//   9) SO·CSO: credit_curve selector 미노출 / vesting 필수 / maturity 비활성(enableWhen)
//  10) CSO: market_condition + performance_condition + tranche(table) 존재
//  11) parity 노출 상품 = {CB,RCPS,CPS,EB} 정확히
// ===========================================================================

import { test } from 'vitest';
import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

type AnyField = {
  key: string;
  label: string;
  type: string;
  bind?: string;
  resolve?: { to?: string; kind?: string };
  enableWhen?: unknown;
  showWhen?: unknown;
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
const eb = load('eb');
const bw = load('bw');
const so = load('so');
const cso = load('cso');
const SCHEMAS: Record<string, AnySchema> = {
  cb: cb, rcps: rcps, cps: cps, eb: eb, bw: bw, so: so, cso: cso,
};

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

function stepIds(schema: AnySchema): string[] {
  return schema.steps.map(function (s) { return s.id; });
}

function findField(schema: AnySchema, key: string): AnyField | undefined {
  return allFields(schema).filter(function (f) { return f.key === key; })[0];
}

// --- 검증 1: FormSchema 필수 키 부합 ---
test('1. 7개 스키마가 FormSchema 필수 키에 부합한다', () => {
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
  assert.equal(eb.product, 'EB');
  assert.equal(bw.product, 'BW');
  assert.equal(so.product, 'SO');
  assert.equal(cso.product, 'CSO');
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
  assert.ok(count >= 11, 'curveSelector 최소 11개(CB/RCPS/CPS/EB/BW x2 + SO/CSO x1) — 실제 ' + count);
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
  assert.equal(count, 7, 'assetSearch 는 7종 각 1개 — 실제 ' + count);
});

// --- 검증 4: parity 는 type="computed" + bind 없음 ---
test('4. parity 필드는 type=computed 이고 bind 가 없다', () => {
  for (const name of Object.keys(SCHEMAS)) {
    for (const f of allFields(SCHEMAS[name])) {
      if (f.key !== 'parity') continue;
      assert.equal(f.type, 'computed', name + '.parity: type=computed');
      assert.equal(f.bind, undefined, name + '.parity: bind 없음');
    }
  }
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
  const ids = stepIds(cps);
  for (const fid of forbidden) {
    assert.ok(ids.indexOf(fid) === -1, 'CPS 에 ' + fid + ' 스텝이 존재하면 안 됨');
  }
  assert.ok(ids.indexOf('rights.conversion') !== -1);
  assert.ok(ids.indexOf('rights.refixing') !== -1);
});

// --- 검증 7: EB — conversion 없음 / exchange 있음 / market 라벨에 "교환대상" ---
test('7. EB 는 conversion 없고 exchange 있으며 market 라벨에 교환대상 표기', () => {
  const ids = stepIds(eb);
  assert.ok(ids.indexOf('rights.conversion') === -1, 'EB 에 conversion 스텝 없어야 함');
  assert.ok(ids.indexOf('rights.exchange') !== -1, 'EB 에 exchange 스텝 있어야 함');
  for (const key of ['volatility', 'dividend_yield', 'spot']) {
    const f = findField(eb, key);
    assert.ok(f, 'EB.' + key + ' 존재');
    assert.ok(f!.label.indexOf('교환대상') !== -1, 'EB.' + key + ' 라벨에 "교환대상" 포함: ' + f!.label);
  }
  // 교환대상 assetSearch 는 rights.exchange.target_asset_id 에 bind
  const ex = findField(eb, 'exchange_asset');
  assert.ok(ex && ex.bind === 'rights.exchange.target_asset_id', 'EB 교환대상 assetSearch bind 확인');
});

// --- 검증 8: BW — warrant + dilution 있음 / separable 토글 존재 ---
test('8. BW 는 warrant + dilution 스텝과 separable 토글을 가진다', () => {
  const ids = stepIds(bw);
  assert.ok(ids.indexOf('rights.warrant') !== -1, 'BW 에 warrant 스텝');
  assert.ok(ids.indexOf('rights.dilution') !== -1, 'BW 에 dilution 스텝');
  assert.ok(ids.indexOf('rights.conversion') === -1, 'BW 에 conversion 없음');
  const sep = findField(bw, 'wt_separable');
  assert.ok(sep, 'BW.wt_separable 존재');
  assert.equal(sep!.type, 'toggle', 'wt_separable 는 toggle');
  assert.equal(sep!.bind, 'rights.warrant.separable', 'wt_separable bind 확인');
  // 희석계수용 shares_outstanding 필수
  const sh = findField(bw, 'shares_outstanding');
  assert.ok(sh && sh.bind === 'market.shares_outstanding', 'BW shares_outstanding bind 확인');
});

// --- 검증 9: SO·CSO — credit selector 미노출 / vesting 필수 / maturity 비활성 ---
test('9. SO·CSO 는 credit_curve selector 미노출 / vesting 스텝 / maturity 비활성', () => {
  for (const s of [so, cso]) {
    const ids = stepIds(s);
    assert.ok(ids.indexOf('rights.vesting') !== -1, s.product + ' 에 vesting 스텝');
    // credit_curve curveSelector 미노출
    const creditSelectors = allFields(s).filter(function (f) {
      return f.type === 'curveSelector' && f.bind === 'curves.credit_ref';
    });
    assert.equal(creditSelectors.length, 0, s.product + ' 에 credit_curve selector 미노출');
    // risk_free selector 는 존재
    const rf = allFields(s).filter(function (f) {
      return f.type === 'curveSelector' && f.bind === 'curves.risk_free_ref';
    });
    assert.equal(rf.length, 1, s.product + ' 에 risk_free selector 1개');
    // maturity_date 는 enableWhen 으로 비활성
    const mat = findField(s, 'maturity_date');
    assert.ok(mat, s.product + '.maturity_date 존재');
    assert.ok(mat!.enableWhen, s.product + '.maturity_date 는 enableWhen 보유(비활성)');
    // expected_term 사용
    const et = findField(s, 'expected_term');
    assert.ok(et && et.bind === 'terms.expected_term', s.product + ' expected_term 사용');
  }
});

// --- 검증 10: CSO — market_condition + performance_condition + tranche(table) ---
test('10. CSO 는 market_condition + performance_condition + tranche(table) 를 가진다', () => {
  const ids = stepIds(cso);
  assert.ok(ids.indexOf('rights.market_condition') !== -1, 'CSO 에 market_condition 스텝');
  assert.ok(ids.indexOf('rights.performance_condition') !== -1, 'CSO 에 performance_condition 스텝');
  const tr = findField(cso, 'tranche');
  assert.ok(tr, 'CSO.tranche 존재');
  assert.equal(tr!.type, 'table', 'tranche 는 table');
  assert.equal(tr!.bind, 'rights.vesting.tranche', 'tranche bind 확인');
  // 조건 bind 확인
  assert.ok(findField(cso, 'mc_target_price')!.bind === 'rights.market_condition.target_price');
  assert.ok(findField(cso, 'pc_probability')!.bind === 'rights.performance_condition.probability');
});

// --- 검증 11: parity 노출 상품 = {CB,RCPS,CPS,EB} 정확히 ---
test('11. parity 는 전환/교환 보유 상품(CB·RCPS·CPS·EB)에만 존재한다', () => {
  const withParity: string[] = [];
  for (const name of Object.keys(SCHEMAS)) {
    if (findField(SCHEMAS[name], 'parity')) withParity.push(SCHEMAS[name].product);
  }
  withParity.sort();
  assert.deepEqual(withParity, ['CB', 'CPS', 'EB', 'RCPS'], 'parity 보유 상품 집합: ' + withParity.join(','));
});
