"use client";
// 경량 SVG 수익률 곡선(새 의존성 없음). 만기(x) - 수익률%(y).
export function CurveChart({ points }: { points: { tenor_years: number; rate_percent: number }[] }) {
  const pts = [...points].sort((a, b) => a.tenor_years - b.tenor_years);
  if (pts.length < 2) return <p className="text-xs text-slate-400">곡선 표시에 최소 2개 포인트가 필요합니다.</p>;
  const W = 560, H = 200, pad = 36;
  const xs = pts.map((p) => p.tenor_years), ys = pts.map((p) => p.rate_percent);
  const xmin = Math.min(...xs), xmax = Math.max(...xs);
  const ymin = Math.min(...ys), ymax = Math.max(...ys);
  const sx = (x: number) => pad + (W - 2 * pad) * (x - xmin) / (xmax - xmin || 1);
  const sy = (y: number) => H - pad - (H - 2 * pad) * (y - ymin) / (ymax - ymin || 1);
  const d = pts.map((p, i) => `${i ? "L" : "M"}${sx(p.tenor_years).toFixed(1)},${sy(p.rate_percent).toFixed(1)}`).join(" ");
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="w-full" role="img" aria-label="수익률 곡선">
      <line x1={pad} y1={H - pad} x2={W - pad} y2={H - pad} stroke="#cbd5e1" />
      <line x1={pad} y1={pad} x2={pad} y2={H - pad} stroke="#cbd5e1" />
      <path d={d} fill="none" stroke="#20235e" strokeWidth={2} />
      {pts.map((p, i) => <circle key={i} cx={sx(p.tenor_years)} cy={sy(p.rate_percent)} r={2.5} fill="#20235e" />)}
      <text x={pad} y={H - 10} className="fill-slate-400 text-[10px]">만기(년) {xmin}~{xmax}</text>
      <text x={pad} y={pad - 8} className="fill-slate-400 text-[10px]">수익률(%) {ymin}~{ymax}</text>
    </svg>
  );
}
