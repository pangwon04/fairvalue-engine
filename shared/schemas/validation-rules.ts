// ===========================================================================
// FairValue Engine — Validation Rule 카탈로그 (W3, §6)
// ---------------------------------------------------------------------------
// Frontend(Zod 변환)와 Backend 검증이 동일 카탈로그를 공유한다.
// severity: error=제출/계산 차단, warning=주석(진행 가능).
// 패치 반영(2.2): 경고 코드표에 W206(기초자산 자동값 수동 override) 추가.
// ===========================================================================

import type { ValidationRuleType } from './form-schema';

export type Severity = 'error' | 'warning';

/** rule 카탈로그 단일 항목 정의. */
export interface RuleCatalogEntry {
  rule: ValidationRuleType;
  description: string;          // 규칙 설명
  appliesToExample: string;    // 적용 필드 예시
  defaultMessage: string;      // 기본 오류 메시지
  severity: Severity;          // 차단 여부
  /** 이 rule 이 받는 params 키 목록(타입 참조용). */
  paramKeys?: string[];
}

/** §6 표준 20종 rule 카탈로그. */
export const VALIDATION_RULES: readonly RuleCatalogEntry[] = [
  { rule: 'required', description: '필수값 존재', appliesToExample: 'valuation_date, conv_price',
    defaultMessage: '필수 입력 항목입니다.', severity: 'error' },
  { rule: 'min', description: '최솟값 이상', appliesToExample: 'lattice_steps(min 100)',
    defaultMessage: '{min} 이상이어야 합니다.', severity: 'error', paramKeys: ['min'] },
  { rule: 'max', description: '최댓값 이하', appliesToExample: 'coupon_rate(max 100)',
    defaultMessage: '{max} 이하여야 합니다.', severity: 'error', paramKeys: ['max'] },
  { rule: 'positive', description: '0 초과', appliesToExample: 'issue_amount, spot',
    defaultMessage: '0보다 커야 합니다.', severity: 'error' },
  { rule: 'dateOrder', description: '날짜 선후관계', appliesToExample: 'conv_start ≤ conv_end',
    defaultMessage: '시작일이 종료일보다 늦을 수 없습니다.', severity: 'error', paramKeys: ['from', 'to'] },
  { rule: 'dateWithin', description: '기간 내 포함', appliesToExample: 'exercise_date ∈ [issue,maturity]',
    defaultMessage: '유효 기간을 벗어났습니다.', severity: 'error', paramKeys: ['start', 'end'] },
  { rule: 'enum', description: '허용값 집합', appliesToExample: 'model, redemption_type',
    defaultMessage: '허용되지 않은 값입니다.', severity: 'error', paramKeys: ['values'] },
  { rule: 'percentageRange', description: '% 범위', appliesToExample: 'coupon_rate, volatility',
    defaultMessage: '{min}~{max}% 범위여야 합니다.', severity: 'error', paramKeys: ['min', 'max'] },
  { rule: 'dependencyRequired', description: 'A 입력 시 B 필수', appliesToExample: 'use_refixing→refix_floor',
    defaultMessage: '해당 조건 적용 시 필수 입력입니다.', severity: 'error', paramKeys: ['when', 'requires'] },
  { rule: 'mutuallyExclusive', description: '동시 입력 불가', appliesToExample: 'put.enabled vs call 동일조건',
    defaultMessage: '두 옵션을 동시에 설정할 수 없습니다.', severity: 'error', paramKeys: ['fields'] },
  { rule: 'showWhenRequired', description: '노출 시에만 필수', appliesToExample: 'sc_strike_pct',
    defaultMessage: '해당 권리조건의 필수값입니다.', severity: 'error' },
  { rule: 'curveRequired', description: '커브 선택 필수', appliesToExample: 'risk_free_curve, credit_curve',
    defaultMessage: '커브를 선택하세요.', severity: 'error', paramKeys: ['kind'] },
  { rule: 'assetRequired', description: '기초자산 매핑 필수', appliesToExample: 'market.asset_id',
    defaultMessage: '기초자산을 선택하세요.', severity: 'error' },
  { rule: 'modelCompatibility', description: '모형↔조건 호환', appliesToExample: 'model vs rights',
    defaultMessage: '선택 모형이 입력 권리조건과 호환되지 않습니다.', severity: 'error' },
  { rule: 'refixingFloorCheck', description: '0 ≤ floor ≤ init_strike', appliesToExample: 'refix_floor',
    defaultMessage: '하한가는 0 이상, 최초 전환가 이하여야 합니다.', severity: 'error', paramKeys: ['initField'] },
  { rule: 'maturityAfterIssueDate', description: '만기 > 발행', appliesToExample: 'maturity_date',
    defaultMessage: '만기일은 발행일 이후여야 합니다.', severity: 'error', paramKeys: ['issueField'] },
  { rule: 'exercisePeriodWithinMaturity', description: '행사기간 ⊆ 만기', appliesToExample: 'conv_start, warrant.end',
    defaultMessage: '행사기간은 만기 이내여야 합니다.', severity: 'error' },
  { rule: 'volatilityPositive', description: '변동성 > 0', appliesToExample: 'market.volatility',
    defaultMessage: '변동성은 0보다 커야 합니다.', severity: 'error' },
  { rule: 'pricePositive', description: '가격 > 0', appliesToExample: 'spot, exercise_price',
    defaultMessage: '가격은 0보다 커야 합니다.', severity: 'error' },
  { rule: 'dilutionRange', description: '0 ≤ 희석계수 ≤ 1', appliesToExample: 'dilution.new_shares 비율',
    defaultMessage: '희석 비율이 유효 범위를 벗어났습니다.', severity: 'warning' },
] as const;

// ---------------------------------------------------------------------------
// warnings / errors 표준 코드 체계 (§4.11 + 패치 2.2 W206)
//   E(차단)·W(경고) + 3자리 (0xx 입력, 1xx 계산, 2xx 모형/커브/회계)
//   신규 코드는 append-only 등록.
// ---------------------------------------------------------------------------

export type IssueCategory = 'input' | 'routing' | 'calc' | 'model' | 'curve' | 'accounting';

export interface IssueCode {
  code: string;
  category: IssueCategory;
  meaning: string;
  severity: Severity;
}

export const ISSUE_CODES: readonly IssueCode[] = [
  { code: 'E001', category: 'input', meaning: '필수 커브 누락', severity: 'error' },
  { code: 'E002', category: 'input', meaning: '만기일 ≤ 발행일', severity: 'error' },
  { code: 'E003', category: 'input', meaning: '변동성/주가 누락(주식연계)', severity: 'error' },
  { code: 'E004', category: 'routing', meaning: 'model ↔ instrument_type 비호환', severity: 'error' },
  { code: 'E101', category: 'calc', meaning: '격자/시뮬 수치 발산', severity: 'error' },
  { code: 'E102', category: 'calc', meaning: 'input_hash 재계산 불일치', severity: 'error' },
  { code: 'W201', category: 'model', meaning: '리픽싱 경로의존 → LSMC 교차검증 권장', severity: 'warning' },
  { code: 'W202', category: 'curve', meaning: '음의 forward rate 발생', severity: 'warning' },
  { code: 'W203', category: 'calc', meaning: 'MC 표준오차 임계 초과', severity: 'warning' },
  { code: 'W204', category: 'input', meaning: '비상장 변동성 수기 입력 사용', severity: 'warning' },
  { code: 'W205', category: 'accounting', meaning: '비시장 성과조건 충족확률 수기 가정', severity: 'warning' },
  // ★신규(패치 2.2): 기초자산 자동값을 수기 입력이 override(편차 기록)
  { code: 'W206', category: 'input', meaning: '기초자산 자동값을 수기 입력이 override함(편차 기록)', severity: 'warning' },
] as const;
