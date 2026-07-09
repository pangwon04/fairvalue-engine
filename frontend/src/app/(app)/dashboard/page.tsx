"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getDashboardSummary, type DashboardRecentJob } from "@/lib/api/dashboard";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";

const statusTone: Record<string, "navy" | "success" | "danger" | "gray"> = {
  DONE: "success", FAILED: "danger", RUNNING: "navy", QUEUED: "gray", PARTIAL: "gray",
};
const won = (n: number | null) => (n == null ? "—" : n.toLocaleString("ko-KR", { maximumFractionDigits: 0 }));

function Kpi({ label, value, sub }: { label: string; value: React.ReactNode; sub?: string }) {
  return (
    <Card>
      <CardBody>
        <div className="text-sm text-slate-500">{label}</div>
        <div className="mt-1 text-2xl font-semibold tnum text-navy-800">{value}</div>
        {sub && <div className="mt-0.5 text-xs text-slate-500">{sub}</div>}
      </CardBody>
    </Card>
  );
}

const QUICK = [
  { label: "새 평가", desc: "상품 선택·평가 실행", href: "/instruments" },
  { label: "커브 등록", desc: "수익률 커브 업로드", href: "/parameters/curves" },
  { label: "변동성 등록", desc: "주가변동성 등록", href: "/parameters/volatility" },
  { label: "보고서", desc: "발급 이력·다운로드", href: "/reports" },
];

export default function DashboardPage() {
  const router = useRouter();
  const { data, isLoading, isError } = useQuery({ queryKey: ["dashboard-summary"], queryFn: getDashboardSummary });

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-semibold text-slate-900">대시보드</h1>

      {isLoading && <Spinner label="현황 불러오는 중…" />}
      {isError && <p className="text-sm text-danger">현황을 불러오지 못했습니다(백엔드 확인).</p>}

      {data && (
        <>
          {/* 1. KPI 카드 4 */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Kpi label="등록 상품" value={data.instruments.active} sub={`활성 ${data.instruments.active} · 보관 ${data.instruments.archived}`} />
            <Kpi label="평가 수행 (완료)" value={data.jobs.done} sub={`실패 ${data.jobs.failed}`} />
            <Kpi label="발급 보고서" value={data.reports.count} sub="누적 발급" />
            <Kpi label="등록 파라미터" value={data.parameters.curves + data.parameters.volatilities}
              sub={`커브 ${data.parameters.curves} · 변동성 ${data.parameters.volatilities}`} />
          </div>

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
            {/* 2. 최근 평가 (8) */}
            <div className="lg:col-span-2">
              <Card>
                <CardHeader title="최근 평가" desc="최신 8건. 행을 클릭하면 평가 이력으로 이동합니다." />
                <CardBody>
                  {data.recent_jobs.length === 0 ? (
                    <EmptyState message="아직 수행한 평가가 없습니다." href="/instruments" cta="새 평가 시작 →" />
                  ) : (
                    <table className="w-full text-sm">
                      <thead><tr className="border-b border-slate-200 text-left text-slate-500">
                        <th className="py-2 font-medium">상품</th><th className="font-medium">유형</th>
                        <th className="font-medium">기준일</th><th className="font-medium">모형</th>
                        <th className="text-right font-medium">총액</th><th className="font-medium">상태</th><th className="font-medium">보고서</th>
                      </tr></thead>
                      <tbody>
                        {data.recent_jobs.map((j: DashboardRecentJob) => (
                          <tr key={j.job_id} className="cursor-pointer border-b border-slate-100 hover:bg-slate-50" onClick={() => router.push("/jobs")}>
                            <td className="py-2 text-slate-800">{j.instrument_name ?? "—"}</td>
                            <td>{j.type ? <Badge tone="navy">{j.type}</Badge> : "—"}</td>
                            <td className="tnum text-slate-600">{j.valuation_date ?? "—"}</td>
                            <td className="text-slate-500">{j.model ?? "—"}</td>
                            <td className="text-right tnum">{won(j.total_fair_value)}</td>
                            <td><Badge tone={statusTone[j.status] ?? "gray"}>{j.status}</Badge></td>
                            <td>{j.report_issued ? <Badge tone="success">발급됨</Badge> : <span className="text-xs text-slate-400">미발급</span>}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </CardBody>
              </Card>
            </div>

            {/* 우측: 유형별 + 빠른 이동 */}
            <div className="space-y-5">
              {/* 3. 상품 유형별 현황 */}
              <Card>
                <CardHeader title="상품 유형별" desc="활성 상품 기준" />
                <CardBody>
                  {data.by_type.length === 0 ? (
                    <EmptyState message="아직 등록된 상품이 없습니다." href="/instruments/new" cta="상품 등록 →" />
                  ) : (
                    <table className="w-full text-sm">
                      <tbody>
                        {data.by_type.map((t) => (
                          <tr key={t.type} className="border-b border-slate-100 last:border-0">
                            <td className="py-2"><Badge tone="navy">{t.type}</Badge></td>
                            <td className="text-right tnum text-slate-700">{t.count}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </CardBody>
              </Card>

              {/* 4. 빠른 이동 */}
              <Card>
                <CardHeader title="빠른 이동" />
                <CardBody className="grid grid-cols-2 gap-2">
                  {QUICK.map((q) => (
                    <Link key={q.href} href={q.href}
                      className="rounded-lg border border-slate-200 px-3 py-2 transition hover:border-navy-300 hover:bg-navy-50">
                      <div className="text-sm font-medium text-navy-800">{q.label}</div>
                      <div className="text-xs text-slate-500">{q.desc}</div>
                    </Link>
                  ))}
                </CardBody>
              </Card>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function EmptyState({ message, href, cta }: { message: string; href: string; cta: string }) {
  return (
    <div className="py-6 text-center">
      <p className="text-sm text-slate-500">{message}</p>
      <Link href={href} className="mt-2 inline-block text-sm font-medium text-navy-700 hover:underline">{cta}</Link>
    </div>
  );
}
