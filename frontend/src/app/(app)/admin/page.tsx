"use client";
import { ComingSoon } from "@/components/ComingSoon";
export default function AdminPage() {
  return <ComingSoon
    title="조직 관리"
    purpose="조직 구성원과 권한(평가자·커브관리자·뷰어)을 관리합니다. 조직 관리자(ORG_ADMIN) 전용입니다."
    features={["구성원 목록·역할 조회", "권한 부여·변경(RBAC)", "조직 단위 접근 제어"]} />;
}
