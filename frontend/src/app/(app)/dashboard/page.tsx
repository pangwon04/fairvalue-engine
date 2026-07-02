"use client";
import { ComingSoon } from "@/components/ComingSoon";
export default function DashboardPage() {
  return <ComingSoon
    title="대시보드"
    purpose="최근 평가 현황과 조직의 주요 지표를 한 화면에 모아, 진행 중·완료된 평가와 자주 쓰는 작업으로 빠르게 이동합니다."
    features={["최근 평가 Job과 상태 요약", "상품군별 평가 건수·최근 공정가치", "상품 생성·평가 실행 바로가기"]} />;
}
