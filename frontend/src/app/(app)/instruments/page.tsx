"use client";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { listInstruments } from "@/lib/api/instruments";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";

const statusTone: Record<string, "gray" | "navy" | "success"> = {
  DRAFT: "gray", TERMS_SAVED: "navy", PRICED: "success", ARCHIVED: "gray",
};

export default function InstrumentsPage() {
  const { data, isLoading, isError, error } = useQuery({ queryKey: ["instruments"], queryFn: () => listInstruments() });
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">평가 상품</h1>
        <Link href="/instruments/new"><Button>+ CB 상품 생성</Button></Link>
      </div>
      <Card>
        <CardHeader title="상품 목록" desc="조직 내 평가 대상 복합금융상품" />
        <CardBody>
          {isLoading && <Spinner label="불러오는 중…" />}
          {isError && <p className="text-sm text-danger">목록 로드 실패: {(error as Error).message}</p>}
          {data && data.items.length === 0 && <p className="text-sm text-slate-500">아직 상품이 없습니다. 위에서 CB 상품을 생성하세요.</p>}
          {data && data.items.length > 0 && (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-slate-500">
                  <th className="py-2 font-medium">ID</th><th className="font-medium">유형</th>
                  <th className="font-medium">상품명</th><th className="font-medium">발행사</th>
                  <th className="font-medium">상태</th><th></th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((i) => (
                  <tr key={i.id} className="border-b border-slate-100">
                    <td className="py-2 tnum">{i.id}</td>
                    <td><Badge tone="navy">{i.type}</Badge></td>
                    <td className="text-slate-800">{i.name}</td>
                    <td className="text-slate-600">{i.issuer}</td>
                    <td><Badge tone={statusTone[i.status] ?? "gray"}>{i.status}</Badge></td>
                    <td className="text-right"><Link href={`/instruments/${i.id}`} className="text-navy-700 hover:underline">열기 →</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
