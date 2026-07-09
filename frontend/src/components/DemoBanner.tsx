// ★ Phase 6-1 데모 프레이밍. NEXT_PUBLIC_DEMO_BANNER 로 on/off(빌드 시점 인라인).
//   서버 컴포넌트 — env 값이 번들에 인라인되어 클라이언트 JS 불요.
export function DemoBanner() {
  if (process.env.NEXT_PUBLIC_DEMO_BANNER !== "true") return null;
  return (
    <div className="w-full border-b border-amber-200 bg-amber-50 px-4 py-1.5 text-center text-xs text-amber-900">
      본 사이트는 포트폴리오 데모입니다. 실제 평가 서비스가 아니며, 데이터는 예고 없이 초기화될 수 있습니다.
    </div>
  );
}
