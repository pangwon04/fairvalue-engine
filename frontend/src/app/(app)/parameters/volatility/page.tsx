"use client";
import { ComingSoon } from "@/components/ComingSoon";
export default function VolatilityPage() {
  return <ComingSoon
    title="변동성"
    purpose="기초자산별 변동성(역사적·내재)을 입력·관리하여 격자·시뮬레이션 평가의 입력으로 사용합니다."
    features={["종목별 변동성 입력·이력", "역사적/내재 변동성 구분", "평가 파라미터로 연결"]} />;
}
