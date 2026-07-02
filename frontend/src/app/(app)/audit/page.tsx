"use client";
import { ComingSoon } from "@/components/ComingSoon";
export default function AuditPage() {
  return <ComingSoon
    title="계산 근거"
    purpose="각 평가의 입력값·파라미터·모델 버전·계산 로그를 추적해 재현성을 확인하고 근거를 제시합니다."
    features={["입력값·커브 스냅샷·input_hash 추적", "적용 모델·버전·시드 기록", "재현성 검증(동일 입력→동일 결과)"]} />;
}
