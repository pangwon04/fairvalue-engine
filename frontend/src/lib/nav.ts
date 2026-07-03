// 사이드바 네비게이션 구성. active=false 는 "준비 중" 안내 페이지.
export interface NavItem {
  label: string;
  href: string;
  active: boolean;
  adminOnly?: boolean;
  children?: NavItem[];
}

export const NAV: NavItem[] = [
  { label: "대시보드", href: "/dashboard", active: false },
  { label: "상품 평가", href: "/instruments", active: true },
  {
    label: "파라미터 관리", href: "/parameters", active: true,
    children: [
      { label: "수익률 커브", href: "/parameters/curves", active: true },
      { label: "변동성", href: "/parameters/volatility", active: true },
    ],
  },
  { label: "평가 이력", href: "/jobs", active: true },
  { label: "보고서", href: "/reports", active: false },
  // ★5-7: 계산 근거는 평가 이력 상세에 종속(독립 메뉴 폐지). /audit → /jobs 리다이렉트.
  { label: "조직 관리", href: "/admin", active: false, adminOnly: true },
];
