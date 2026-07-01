// ===========================================================================
// FairValue Engine — Product Form Schema 매핑 (W8 완성: 7종 전체)
// ---------------------------------------------------------------------------
// W8-1차(CB·RCPS·CPS) + W8-2차(EB·BW·SO·CSO) 폼 스키마를 import 하여
// product → FormSchema 매핑으로 export. 7개 상품 전부 동일 골격·동일 계약.
// 형식 기준: shared/schemas/form-schema.ts (FormSchema 타입).
// ===========================================================================

import type { FormSchema, InstrumentType } from '../../../../shared/schemas/form-schema';

import cb from './cb.json';
import rcps from './rcps.json';
import cps from './cps.json';
import eb from './eb.json';
import bw from './bw.json';
import so from './so.json';
import cso from './cso.json';

/** 지원 상품 코드 = 7종 전체. */
export type SupportedProduct = InstrumentType;

/** product 코드 → FormSchema 매핑 (7종 전체). */
export const PRODUCT_SCHEMAS: Record<SupportedProduct, FormSchema> = {
  CB: cb as FormSchema,
  RCPS: rcps as FormSchema,
  CPS: cps as FormSchema,
  EB: eb as FormSchema,
  BW: bw as FormSchema,
  SO: so as FormSchema,
  CSO: cso as FormSchema,
};

/** product 코드로 FormSchema 조회. 미지원 상품은 undefined. */
export function getProductSchema(product: string): FormSchema | undefined {
  return (PRODUCT_SCHEMAS as Record<string, FormSchema>)[product];
}

/** 지원 상품 코드 목록 (7종). */
export const SUPPORTED_PRODUCTS: SupportedProduct[] = [
  'CB', 'RCPS', 'CPS', 'EB', 'BW', 'SO', 'CSO',
];

export { cb, rcps, cps, eb, bw, so, cso };
export default PRODUCT_SCHEMAS;
