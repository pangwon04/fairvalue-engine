"use client";
import { ComingSoon } from "@/components/ComingSoon";
export default function ParametersPage() {
  return <ComingSoon
    title="파라미터 관리"
    purpose="평가에 사용하는 시장 파라미터(수익률 커브·변동성)를 업로드하고 기준일·버전별로 관리합니다."
    features={["무위험·신용등급 수익률 커브(하위 메뉴)", "종목별 변동성 데이터(하위 메뉴)", "기준일(as_of)·버전 이력 관리"]} />;
}
