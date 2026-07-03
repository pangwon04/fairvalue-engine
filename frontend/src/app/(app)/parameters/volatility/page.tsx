"use client";
import { useState } from "react";
import { Tabs } from "@/components/ui/Tabs";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { DirectTab } from "@/components/volatility/DirectTab";
import { ComputeTab } from "@/components/volatility/ComputeTab";
import { AutoTab } from "@/components/volatility/AutoTab";
import { VolatilityList } from "@/components/volatility/VolatilityList";

const TABS = [
  { id: "direct", label: "직접 입력" },
  { id: "compute", label: "주가 CSV 산출" },
  { id: "auto", label: "자동 조회" },
];

export default function VolatilityPage() {
  const [tab, setTab] = useState("direct");
  const [refreshKey, setRefreshKey] = useState(0);
  const refresh = () => setRefreshKey((k) => k + 1);

  return (
    <div className="max-w-4xl space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">변동성</h1>
      <Card>
        <CardHeader title="변동성 등록"
          desc="평가 파라미터인 주가변동성을 직접 입력하거나, 유사회사 주가 CSV로 역사적 변동성을 산출해 등록합니다." />
        <CardBody className="space-y-4">
          <Tabs tabs={TABS} active={tab} onChange={setTab} />
          {tab === "direct" && <DirectTab onSaved={refresh} />}
          {tab === "compute" && <ComputeTab onSaved={refresh} />}
          {tab === "auto" && <AutoTab />}
        </CardBody>
      </Card>
      <VolatilityList refreshKey={refreshKey} />
    </div>
  );
}
