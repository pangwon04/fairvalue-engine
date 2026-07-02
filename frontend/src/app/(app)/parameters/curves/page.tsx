"use client";
import { ComingSoon } from "@/components/ComingSoon";
export default function CurvesPage() {
  return <ComingSoon
    title="수익률 커브"
    purpose="무위험·신용등급 수익률 커브를 CSV로 업로드하고 기준일·등급·버전별로 관리하며, 부트스트래핑으로 평가에 쓸 zero 커브를 산출합니다."
    features={["커브 CSV 업로드(무위험/신용, as_of·grade)", "부트스트래핑·보간 결과 조회", "버전 이력과 평가 시 스냅샷 연결"]} />;
}
