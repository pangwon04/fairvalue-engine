// ===========================================================================
// verify-bind.ts 자체 테스트 (W10)
//   - 실제 7개 폼: 위반 0건이어야 한다.
//   - 일부러 깨진 in-memory fixture: 위반이 검출돼야 한다(검증기가 작동함을 시연).
// ===========================================================================

import { test } from 'vitest';
import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildContext, verifySchema, verifyAll, type FormSchema } from '../verify-bind';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SHARED = join(__dirname, '..', '..', '..', 'shared', 'schemas');
const draft = JSON.parse(readFileSync(join(SHARED, 'valuation-context.draft.schema.json'), 'utf-8'));
const finalSchema = JSON.parse(readFileSync(join(SHARED, 'valuation-context.schema.json'), 'utf-8'));
const ctx = buildContext(draft, finalSchema);

test('실제 7개 폼은 bind 위반 0건이다', () => {
  const { violations, fileCount } = verifyAll();
  assert.equal(fileCount, 7, '폼 파일 7개');
  assert.deepEqual(violations, [], '위반: ' + violations.join(' | '));
});

test('깨진 fixture: 존재하지 않는 rawForm bind → 위반 검출', () => {
  const broken: FormSchema = {
    product: 'CB',
    steps: [{ id: 'terms', fields: [
      { key: 'bad', label: 'x', type: 'number', bind: 'nonexistent.path' },
    ] }],
  };
  const v = verifySchema('broken.json', broken, ctx);
  assert.ok(v.length >= 1, '깨진 bind 가 검출돼야 함');
  assert.ok(v.some((m) => m.includes('허용되지 않은 rawForm bind')), v.join(' | '));
});

test('깨진 fixture: computed 에 bind 존재 → 위반 검출', () => {
  const broken: FormSchema = {
    product: 'CB',
    steps: [{ id: 'rights.conversion', fields: [
      { key: 'parity', label: 'p', type: 'computed', bind: 'rights.conversion.parity' },
    ] }],
  };
  const v = verifySchema('broken.json', broken, ctx);
  assert.ok(v.some((m) => m.includes('computed 필드에 bind')), v.join(' | '));
});

test('깨진 fixture: curveSelector resolve.to 불일치 → 위반 검출', () => {
  const broken: FormSchema = {
    product: 'CB',
    steps: [{ id: 'parameters', fields: [
      { key: 'risk_free_curve', label: 'rf', type: 'curveSelector',
        bind: 'curves.risk_free_ref', resolve: { to: 'curves.credit_curve', kind: 'curve' } },
    ] }],
  };
  const v = verifySchema('broken.json', broken, ctx);
  assert.ok(v.some((m) => m.includes('resolve.to 불일치')), v.join(' | '));
});

test('깨진 fixture: 미정의 validation rule → 위반 검출', () => {
  const broken: FormSchema = {
    product: 'CB',
    steps: [{ id: 'terms', fields: [
      { key: 'x', label: 'x', type: 'number', bind: 'terms.x',
        validations: [{ rule: 'notARule' }] },
    ] }],
  };
  const v = verifySchema('broken.json', broken, ctx);
  assert.ok(v.some((m) => m.includes('미정의 validation rule')), v.join(' | '));
});
