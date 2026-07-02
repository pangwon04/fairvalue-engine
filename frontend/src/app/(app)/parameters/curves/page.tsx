"use client";
import { useState } from "react";
import { Tabs } from "@/components/ui/Tabs";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { DirectUploadTab } from "@/components/curves/DirectUploadTab";
import { KofiaParseTab } from "@/components/curves/KofiaParseTab";
import { AutoFetchTab } from "@/components/curves/AutoFetchTab";
import { CurveList } from "@/components/curves/CurveList";

const TABS = [
  { id: "direct", label: "직접 업로드" },
  { id: "kofia", label: "KOFIA 엑셀 파싱" },
  { id: "auto", label: "자동 조회" },
];

export default function CurvesPage() {
  const [tab, setTab] = useState("direct");
  const [refreshKey, setRefreshKey] = useState(0);
  const refresh = () => setRefreshKey((k) => k + 1);

  return (
    <div className="max-w-4xl space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">수익률 커브</h1>
      <Card>
        <CardHeader title="커브 등록" desc="무위험(RISK_FREE)·신용(CREDIT) 커브를 등록합니다. 3가지 방식 중 선택하세요." />
        <CardBody className="space-y-4">
          <Tabs tabs={TABS} active={tab} onChange={setTab} />
          {tab === "direct" && <DirectUploadTab onSaved={refresh} />}
          {tab === "kofia" && <KofiaParseTab onSaved={refresh} />}
          {tab === "auto" && <AutoFetchTab />}
        </CardBody>
      </Card>
      <CurveList refreshKey={refreshKey} />
    </div>
  );
}
