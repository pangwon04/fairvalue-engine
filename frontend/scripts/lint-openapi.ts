// ===========================================================================
// FairValue Engine — OpenAPI lint (W10)
// ---------------------------------------------------------------------------
// backend/src/main/resources/openapi.yaml 을 검증한다.
//   - @apidevtools/swagger-parser 로 dereference(모든 $ref 해소) 시도 → 깨진 $ref 탐지.
//   - OpenAPI 3.1 구조 규칙 추가 점검(버전·operationId·응답·PricingResult 필수필드·표준 component key).
// 외부 풀린터(@redocly/cli)도 사용 가능하나, CI 결정성을 위해 자체 점검을 기본으로 둔다.
//
// 위반이 있으면 목록 출력 후 exit 1, 통과면 exit 0.
// ===========================================================================

import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse as parseYaml } from 'yaml';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..', '..');
const OPENAPI_PATH = join(REPO_ROOT, 'backend', 'src', 'main', 'resources', 'openapi.yaml');

const STD_COMPONENT_KEYS = [
  'bond_value', 'preferred_share_value', 'conversion_option_value', 'exchange_option_value',
  'warrant_value', 'redemption_option_value', 'issuer_call_value', 'sale_claim_value',
  'stock_option_value', 'conditional_option_value', 'dilution_effect', 'total_fair_value',
];
const PRICING_RESULT_REQUIRED = [
  'job_id', 'total_fair_value', 'components', 'key_parameters', 'reproducibility', 'warnings', 'errors',
];
const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'options', 'head'];

export interface LintResult { errors: string[]; opCount: number; pathCount: number }

/** 구조 규칙 점검(파싱된 spec 대상). */
export function lintSpec(spec: any): LintResult {
  const errors: string[] = [];

  // 1) openapi 버전
  if (!spec || typeof spec.openapi !== 'string' || !spec.openapi.startsWith('3.1')) {
    errors.push(`openapi 버전이 3.1.x 가 아님 (${spec && spec.openapi})`);
  }
  if (!spec.info || spec.info.version !== '0.1.0') {
    errors.push(`info.version 이 "0.1.0" 이 아님 (${spec.info && spec.info.version})`);
  }

  const paths = (spec && spec.paths) || {};
  let opCount = 0;
  for (const p of Object.keys(paths)) {
    for (const m of Object.keys(paths[p])) {
      if (!HTTP_METHODS.includes(m)) continue;
      opCount++;
      const op = paths[p][m];
      if (!op.operationId) errors.push(`${m.toUpperCase()} ${p}: operationId 누락`);
      if (!op.responses || Object.keys(op.responses).length === 0) {
        errors.push(`${m.toUpperCase()} ${p}: responses 누락`);
      }
    }
  }

  const schemas = (spec.components && spec.components.schemas) || {};

  // 2) PricingResult 필수필드
  const pr = schemas.PricingResult;
  if (!pr) {
    errors.push('components.schemas.PricingResult 누락');
  } else {
    const req = new Set<string>(pr.required || []);
    for (const f of PRICING_RESULT_REQUIRED) {
      if (!req.has(f)) errors.push(`PricingResult.required 에 ${f} 누락`);
    }
  }

  // 3) 표준 component key 12종만
  const comp = schemas.Components;
  if (!comp) {
    errors.push('components.schemas.Components 누락');
  } else {
    const keys = Object.keys(comp.properties || {});
    const extra = keys.filter((k) => !STD_COMPONENT_KEYS.includes(k));
    const missing = STD_COMPONENT_KEYS.filter((k) => !keys.includes(k));
    if (extra.length) errors.push('Components 비표준 key: ' + extra.join(', '));
    if (missing.length) errors.push('Components 누락 key: ' + missing.join(', '));
  }

  // 4) securitySchemes bearer JWT
  const sec = spec.components && spec.components.securitySchemes;
  if (!sec || !sec.bearerAuth || sec.bearerAuth.scheme !== 'bearer') {
    errors.push('securitySchemes.bearerAuth(scheme=bearer) 누락');
  }

  return { errors, opCount, pathCount: Object.keys(paths).length };
}

/** 모든 로컬 $ref('#/...')가 실제 노드를 가리키는지 점검. */
export function checkLocalRefs(spec: any): string[] {
  const errors: string[] = [];
  function resolve(ref: string): boolean {
    if (!ref.startsWith('#/')) return true; // 외부 ref 는 이 단계에서 미검사
    const parts = ref.slice(2).split('/');
    let cur: any = spec;
    for (const part of parts) {
      const key = part.replace(/~1/g, '/').replace(/~0/g, '~');
      if (cur && typeof cur === 'object' && key in cur) cur = cur[key];
      else return false;
    }
    return true;
  }
  function walk(node: any, path: string) {
    if (!node || typeof node !== 'object') return;
    if (typeof node.$ref === 'string' && !resolve(node.$ref)) {
      errors.push(`미해결 $ref: ${node.$ref} (at ${path})`);
    }
    for (const k of Object.keys(node)) walk(node[k], path + '/' + k);
  }
  walk(spec, '');
  return errors;
}

export async function lintFile(path: string): Promise<LintResult & { refErrors: string[] }> {
  const text = readFileSync(path, 'utf-8');
  const spec = parseYaml(text);
  const structural = lintSpec(spec);
  const refErrors = checkLocalRefs(spec);

  // @apidevtools/swagger-parser 로 dereference 시도(깨진 $ref/구조 추가 탐지).
  // OpenAPI 3.1 메타스키마 미지원 환경을 대비해 dereference 실패만 치명으로 본다.
  try {
    const mod: any = await import('@apidevtools/swagger-parser');
    const SwaggerParser = mod.default || mod;
    await SwaggerParser.dereference(JSON.parse(JSON.stringify(spec)));
  } catch (e: any) {
    refErrors.push('swagger-parser dereference 실패: ' + (e && e.message ? e.message : String(e)));
  }

  return { ...structural, refErrors };
}

// --- CLI 진입 ---
const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  lintFile(OPENAPI_PATH).then((res) => {
    const all = [...res.errors, ...res.refErrors];
    if (all.length === 0) {
      console.log(`lint:openapi: PASS — paths ${res.pathCount}, operations ${res.opCount}, $ref/구조 정상`);
      process.exit(0);
    } else {
      console.error(`lint:openapi: FAIL — ${all.length}건`);
      for (const m of all) console.error('  - ' + m);
      process.exit(1);
    }
  });
}
