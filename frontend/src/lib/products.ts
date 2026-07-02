import type { InstrumentType } from "./types";

export type ProductCategory = "사채형" | "우선주형" | "옵션형";

export interface ProductMeta {
  code: InstrumentType;
  name: string;            // 정식 명칭(계약·productSchemas title 일치)
  category: ProductCategory;
  desc: string;            // 1줄 설명(회계·실무)
  model: string;           // 적용 평가모형(상품 구조별 구분)
  active: boolean;         // false = 평가 모형 준비 중(SO·CSO)
}

// 계약 InstrumentType(RCPS,CPS,CB,EB,BW,SO,CSO) 과 일치. 임의 명칭 금지.
export const PRODUCTS: ProductMeta[] = [
  { code: "CB", name: "전환사채", category: "사채형",
    desc: "주식 전환권이 부여된 사채", model: "T&F / GS", active: true },
  { code: "EB", name: "교환사채", category: "사채형",
    desc: "발행사가 보유한 타사 주식으로 교환하는 권리가 부여된 사채", model: "T&F / GS", active: true },
  { code: "BW", name: "신주인수권부사채", category: "사채형",
    desc: "신주인수권(워런트)이 부여된 사채. 분리형/비분리형 구분", model: "T&F / GS", active: true },
  { code: "RCPS", name: "상환전환우선주", category: "우선주형",
    desc: "상환권과 전환권을 가진 우선주", model: "T&F / GS", active: true },
  { code: "CPS", name: "전환우선주", category: "우선주형",
    desc: "전환권을 가진 우선주(상환권 없음)", model: "T&F / GS", active: true },
  { code: "SO", name: "스톡옵션", category: "옵션형",
    desc: "임직원 주식매수선택권(주식보상)", model: "BSM / 이항모형", active: false },
  { code: "CSO", name: "조건부 스톡옵션", category: "옵션형",
    desc: "성과·시장조건부 주식매수선택권", model: "몬테카를로 / LSMC", active: false },
];

export const CATEGORIES: ProductCategory[] = ["사채형", "우선주형", "옵션형"];
export const isActiveProduct = (t: string) => PRODUCTS.find((p) => p.code === t)?.active ?? false;
