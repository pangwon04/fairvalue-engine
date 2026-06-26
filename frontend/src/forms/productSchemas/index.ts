// ===========================================================================
// FairValue Engine — Product Form Schema 매핑 (W8 1차)
// ---------------------------------------------------------------------------
// CB·RCPS·CPS 3종 폼 스키마를 import 하여 product → FormSchema 매핑으로 export.
// 나머지 4종(EB·BW·SO·CSO)은 W8 2차에서 추가된다.
// 형식 기준: shared/schemas/form-schema.ts (FormSchema 타입).
// ===========================================================================

import type { FormSchema, InstrumentType } from '../../../../shared/schemas/form-schema';

import cb from './cb.json';
import rcps from './rcps.json';
import cps from './cps.json';

/** 이번 1차에서 지원하는 상품 코드. */
export type SupportedProduct = Extract<InstrumentType, 'CB' | 'RCPS' | 'CPS'>;

/** product 코드 → FormSchema 매핑. (W8 2차에서 EB·BW·SO·CSO 추가 예정) */
export const PRODUCT_SCHEMAS: Record<SupportedProduct, FormSchema> = {
  CB: cb as FormSchema,
  RCPS: rcps as FormSchema,
  CPS: cps as FormSchema,
};

/** product 코드로 FormSchema 조회. 미지원 상품은 undefined. */
export function getProductSchema(product: string): FormSchema | undefined {
  return (PRODUCT_SCHEMAS as Record<string, FormSchema>)[product];
}

/** 1차 지원 상품 코드 목록. */
export const SUPPORTED_PRODUCTS: SupportedProduct[] = ['CB', 'RCPS', 'CPS'];

export { cb, rcps, cps };
export default PRODUCT_SCHEMAS;
