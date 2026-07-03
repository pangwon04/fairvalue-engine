import { redirect } from "next/navigation";

// ★5-7: 파라미터 관리 index → 수익률 커브로 이동(하위 메뉴가 실기능).
export default function ParametersPage() {
  redirect("/parameters/curves");
}
