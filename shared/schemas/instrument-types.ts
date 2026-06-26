// ===========================================================================
// FairValue Engine — 공통 enum 단일 소스 (W1, 계약 공통)
// ---------------------------------------------------------------------------
// 규칙: 아래 타입/상수는 이 파일에서만 선언한다. (Freeze 패치 §2.3)
//   - form-schema.ts / valuation-context.ts / pricing-result.ts 는 import만 한다.
//   - 재선언 금지 (CI에서 중복 선언 린트로 강제).
//   - ModelName 에서 TRINOMIAL 은 미사용으로 제외한다.
// ===========================================================================

/** 7개 지원 상품군. 전 계약 공통 코드값. */
export type InstrumentType = 'RCPS' | 'CPS' | 'CB' | 'EB' | 'BW' | 'SO' | 'CSO';

/** 평가모형명. TRINOMIAL 제외(미사용). */
export type ModelName =
  | 'TF_LATTICE'
  | 'LATTICE'
  | 'MONTE_CARLO'
  | 'LSMC'
  | 'BSM'
  | 'DCF';

/** 옵션 행사 유형. */
export type ExerciseStyle = 'AMERICAN' | 'EUROPEAN' | 'BERMUDAN';

/** 커브 종류. */
export type CurveKind = 'RISK_FREE' | 'CREDIT';

/** 상환/콜 방향. */
export type RedemptionSide = 'PUT' | 'CALL';

/** 평가 Job 상태. */
export type JobStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED' | 'PARTIAL';

// --- 런타임 사용용 const 배열 (enum 대체) ---

/** 상품군 코드 배열(렌더·검증·테스트 공용). */
export const INSTRUMENT_TYPES: readonly InstrumentType[] = [
  'RCPS', 'CPS', 'CB', 'EB', 'BW', 'SO', 'CSO',
] as const;

/** 모형명 배열. */
export const MODEL_NAMES: readonly ModelName[] = [
  'TF_LATTICE', 'LATTICE', 'MONTE_CARLO', 'LSMC', 'BSM', 'DCF',
] as const;

/** 행사 유형 배열. */
export const EXERCISE_STYLES: readonly ExerciseStyle[] = [
  'AMERICAN', 'EUROPEAN', 'BERMUDAN',
] as const;

/** 커브 종류 배열. */
export const CURVE_KINDS: readonly CurveKind[] = ['RISK_FREE', 'CREDIT'] as const;

/** 상환/콜 방향 배열. */
export const REDEMPTION_SIDES: readonly RedemptionSide[] = ['PUT', 'CALL'] as const;

/** Job 상태 배열. */
export const JOB_STATUSES: readonly JobStatus[] = [
  'QUEUED', 'RUNNING', 'DONE', 'FAILED', 'PARTIAL',
] as const;
