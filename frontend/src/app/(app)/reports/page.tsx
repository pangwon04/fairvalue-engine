"use client";
import { ComingSoon } from "@/components/ComingSoon";
export default function ReportsPage() {
  return <ComingSoon
    title="보고서"
    purpose="평가 결과를 회계·감사용 보고서와 Audit Pack으로 생성하고 다운로드합니다."
    features={["평가보고서(입력·모형·결과) 생성", "감사 대응 Audit Pack 묶음", "PDF/문서 다운로드"]} />;
}
