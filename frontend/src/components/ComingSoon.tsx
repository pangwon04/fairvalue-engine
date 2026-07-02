import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";

export function ComingSoon({ title, purpose, features }: {
  title: string; purpose: string; features?: string[];
}) {
  return (
    <div className="max-w-2xl space-y-4">
      <div className="flex items-center gap-2">
        <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
        <Badge tone="warning">준비 중</Badge>
      </div>
      <Card>
        <CardHeader title="이 화면의 목적" desc="다음 단계에서 제공될 기능입니다." />
        <CardBody className="space-y-3">
          <p className="text-sm leading-relaxed text-slate-600">{purpose}</p>
          {features && features.length > 0 && (
            <div>
              <p className="mb-1 text-sm font-medium text-slate-700">담을 기능</p>
              <ul className="list-disc space-y-1 pl-5 text-sm text-slate-600">
                {features.map((f, i) => <li key={i}>{f}</li>)}
              </ul>
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
