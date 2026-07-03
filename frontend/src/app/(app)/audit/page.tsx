import { redirect } from "next/navigation";

// ★5-7: 계산 근거 독립 메뉴 폐지 → 평가 이력 상세에 종속. 북마크/딥링크 대비 리다이렉트.
export default function AuditPage() {
  redirect("/jobs");
}
