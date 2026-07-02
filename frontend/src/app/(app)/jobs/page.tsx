"use client";
import { ComingSoon } from "@/components/ComingSoon";
export default function JobsPage() {
  return <ComingSoon
    title="평가 이력"
    purpose="과거에 실행한 평가 Job과 결과를 조회하고, 평가시점·입력 변경에 따른 공정가치 변화를 비교합니다."
    features={["Job 목록·상태·입력해시 조회", "결과(12키 분해) 재조회", "시점·시나리오 간 값 비교"]} />;
}
