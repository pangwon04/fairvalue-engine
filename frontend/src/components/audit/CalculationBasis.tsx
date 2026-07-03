"use client";
import { useState, useTransition } from "react";
import { useQuery } from "@tanstack/react-query";
import type { PricingResult, Trees, SensitivityGrid, RateTable, TreeRow } from "@/lib/types";
import { getJobContext } from "@/lib/api/pricing";
import { Disclosure } from "@/components/ui/Disclosure";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";

const num = (n: number | null | undefined) =>
  n == null ? "–" : n.toLocaleString("ko-KR", { maximumFractionDigits: 4 });
const DEFAULT_N = 11;      // 축약 기본 노출 개수(tree_meta.display_nodes 기본)
const HEAVY = 120;         // 전체 보기 성능 경고 임계

/** 축약↔전체 토글(성능 가드: useTransition 로 렌더 지연·pending 표시). */
function ExpandToggle({ full, setFull, total, shown }: {
  full: boolean; setFull: (v: boolean) => void; total: number; shown: number;
}) {
  const [pending, start] = useTransition();
  if (total <= shown) return null;
  return (
    <div className="mt-1 flex items-center gap-2 text-xs">
      <button type="button" className="text-navy-700 hover:underline"
        onClick={() => start(() => setFull(!full))}>
        {full ? "축약 보기" : `전체 보기 (${total})`}
      </button>
      {pending && <span className="text-slate-400">렌더 중…</span>}
      {!full && total > HEAVY && <span className="text-warning">항목이 많아 렌더에 시간이 걸릴 수 있습니다</span>}
    </div>
  );
}

// ── 1. 입력 스냅샷 ───────────────────────────────────────────────────────────
function InputSnapshot({ jobId, instrumentId }: { jobId: number; instrumentId: number }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["job-context", jobId], queryFn: () => getJobContext(jobId),
  });
  if (isLoading) return <Spinner label="입력 스냅샷 불러오는 중…" />;
  if (isError || !data || !data.has_context || !data.context) {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-warning">
        이 평가에는 입력 스냅샷이 저장되지 않았습니다(엔진 확장 이전 평가).
        <div className="mt-1 text-slate-600">재평가 실행 시 평가시점 스냅샷이 저장됩니다. 현재 조건은 <a className="text-navy-700 hover:underline" href={`/instruments/${instrumentId}`}>상품 #{instrumentId}</a> 에서 확인하세요.</div>
      </div>
    );
  }
  const c = data.context as Record<string, any>;
  const rows: [string, unknown][] = [
    ["평가기준일", c.valuation_date], ["모형", c.model], ["시드", c.seed],
    ["기초자산 spot", c.market?.spot], ["변동성", c.market?.volatility], ["배당수익률", c.market?.dividend_yield],
    ["커브 출처", c.curves?.curve_source], ["커브 버전", c.curves?.curve_version], ["보간", c.curves?.interpolation_method],
  ];
  return (
    <div className="space-y-2">
      <p className="text-xs text-slate-500">★ 평가 당시 resolve 스냅샷(현재 계약조건이 아닌 평가시점 입력 — 감사 정통).</p>
      <table className="w-full text-sm">
        <tbody>
          {rows.map(([k, v]) => (
            <tr key={k} className="border-b border-slate-100">
              <td className="py-1 text-slate-500">{k}</td>
              <td className="text-right tnum text-slate-800">{v == null ? "–" : String(v)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <RawJson obj={c} />
    </div>
  );
}

function RawJson({ obj }: { obj: unknown }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="text-xs">
      <button type="button" className="text-navy-700 hover:underline" onClick={() => setOpen((o) => !o)}>
        {open ? "원본 JSON 닫기" : "원본 JSON 보기"}
      </button>
      {open && <pre className="mt-1 max-h-64 overflow-auto rounded bg-slate-900 p-2 text-[11px] text-slate-100">{JSON.stringify(obj, null, 2)}</pre>}
    </div>
  );
}

// ── 2. 적용 파라미터 ─────────────────────────────────────────────────────────
const KP_LABELS: Record<string, string> = {
  model_name: "모형", model_version: "모형버전", volatility: "변동성(%)", risk_free_rate: "무위험율(%)",
  discount_rate: "위험할인율 Rd(%)", credit_spread: "신용스프레드(%)", dividend_yield: "배당수익률(%)",
  ytm: "YTM(%)", parity: "패리티", u: "상승계수 u", d: "하락계수 d", lattice_steps: "격자 스텝(사용자)",
  simulation_paths: "시뮬 패스",
};
function ParamsTable({ kp }: { kp: Record<string, unknown> }) {
  const entries = Object.entries(kp).filter(([, v]) => v != null);
  return (
    <table className="w-full text-sm">
      <tbody>
        {entries.map(([k, v]) => (
          <tr key={k} className="border-b border-slate-100">
            <td className="py-1 text-slate-500">{KP_LABELS[k] ?? k}</td>
            <td className="text-right tnum text-slate-800">{typeof v === "number" ? num(v) : String(v)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// ── 3. 이자율 산정(curve_bootstrap) ─────────────────────────────────────────
function RateTableView({ table, title }: { table: RateTable; title: string }) {
  const [full, setFull] = useState(false);
  const cols = table.spot.length;
  const shown = full ? cols : Math.min(DEFAULT_N, cols);
  const tenors = table.spot.slice(0, shown).map((r) => r[0]);
  const rowOf = (rows: [number, number][]) => rows.slice(0, shown).map((r) => r[1]);
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-slate-600">{title}</p>
      <div className="overflow-x-auto">
        <table className="text-xs">
          <thead><tr className="text-slate-500">
            <th className="px-2 py-1 text-left font-medium">만기(y)</th>
            {tenors.map((t, i) => <th key={i} className="px-2 py-1 text-right font-medium tnum">{t}</th>)}
          </tr></thead>
          <tbody>
            {([["YTM", table.ytm], ["SPOT", table.spot], ["FORWARD", table.forward]] as [string, [number, number][]][]).map(([lbl, rows]) => (
              <tr key={lbl} className="border-t border-slate-100">
                <td className="px-2 py-1 font-medium text-slate-700">{lbl}</td>
                {rowOf(rows).map((v, i) => <td key={i} className="px-2 py-1 text-right tnum">{num(v)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ExpandToggle full={full} setFull={setFull} total={cols} shown={Math.min(DEFAULT_N, cols)} />
    </div>
  );
}

// ── 4. 위험중립확률 ─────────────────────────────────────────────────────────
function ProbTable({ prob }: { prob: Trees["risk_neutral_prob"] }) {
  const [full, setFull] = useState(false);
  const shown = full ? prob.length : Math.min(DEFAULT_N, prob.length);
  return (
    <div className="space-y-1">
      <div className="overflow-x-auto">
        <table className="text-xs">
          <thead><tr className="text-slate-500">
            <th className="px-2 py-1 text-left font-medium">스텝</th>
            {prob.slice(0, shown).map((x) => <th key={x.step} className="px-2 py-1 text-right font-medium tnum">{x.step}</th>)}
          </tr></thead>
          <tbody>
            <tr className="border-t border-slate-100"><td className="px-2 py-1 font-medium text-slate-700">p(상승)</td>
              {prob.slice(0, shown).map((x) => <td key={x.step} className="px-2 py-1 text-right tnum">{num(x.p)}</td>)}</tr>
            <tr className="border-t border-slate-100"><td className="px-2 py-1 font-medium text-slate-700">1−p</td>
              {prob.slice(0, shown).map((x) => <td key={x.step} className="px-2 py-1 text-right tnum">{num(x.q)}</td>)}</tr>
          </tbody>
        </table>
      </div>
      <ExpandToggle full={full} setFull={setFull} total={prob.length} shown={Math.min(DEFAULT_N, prob.length)} />
    </div>
  );
}

// ── 5. 가격트리 ─────────────────────────────────────────────────────────────
function TreeTable({ title, tree, note }: { title: string; tree: TreeRow[]; note?: string }) {
  const [full, setFull] = useState(false);
  const n = tree.length;
  const shown = full ? n : Math.min(DEFAULT_N, n);
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-slate-600">{title}{note && <span className="ml-2 font-normal text-slate-400">{note}</span>}</p>
      <div className="max-h-96 overflow-auto">
        <table className="text-[11px]">
          <thead><tr className="text-slate-400">
            <th className="px-1.5 py-0.5 text-left font-medium">step\node</th>
            {Array.from({ length: shown }, (_, j) => <th key={j} className="px-1.5 py-0.5 text-right font-medium">{j}</th>)}
          </tr></thead>
          <tbody>
            {tree.slice(0, shown).map((row, st) => (
              <tr key={st} className="border-t border-slate-50">
                <td className="px-1.5 py-0.5 font-medium text-slate-500">{st}</td>
                {row.slice(0, shown).map((v, j) => (
                  <td key={j} className={`px-1.5 py-0.5 text-right tnum ${v == null ? "text-slate-300" : "text-slate-700"}`}>{v == null ? "–" : num(v)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ExpandToggle full={full} setFull={setFull} total={n} shown={Math.min(DEFAULT_N, n)} />
    </div>
  );
}

function PriceTrees({ trees }: { trees: Trees }) {
  const isGS = trees.tree_meta.model === "GS";
  return (
    <div className="space-y-4">
      <TreeTable title="기초자산 트리 (underlying)" tree={trees.underlying_tree} />
      <TreeTable title="상품가치 트리 (composite)" tree={trees.composite_tree} />
      <TreeTable title={isGS ? "지분분 트리 (equity = q·V)" : "지분요소 트리 (equity)"} tree={trees.equity_tree}
        note={isGS ? "전환확률 가중 분해" : undefined} />
      <TreeTable title={isGS ? "부채분 트리 (debt = (1−q)·V)" : "부채요소 트리 (debt)"} tree={trees.debt_tree}
        note={isGS ? "전환확률 가중 분해" : undefined} />
      {isGS && trees.conversion_prob_tree && (
        <TreeTable title="전환확률 트리 (q, GS 고유)" tree={trees.conversion_prob_tree} />
      )}
    </div>
  );
}

// ── 6. 민감도 ───────────────────────────────────────────────────────────────
function SensitivityTable({ sens }: { sens: SensitivityGrid }) {
  return (
    <div className="space-y-1">
      <div className="overflow-x-auto">
        <table className="text-xs">
          <thead><tr className="text-slate-500">
            <th className="px-2 py-1 text-left font-medium">σ＼기초자산</th>
            {sens.spot_axis.map((s, i) => <th key={i} className="px-2 py-1 text-right font-medium tnum">{num(s)}</th>)}
          </tr></thead>
          <tbody>
            {sens.total_grid.map((rowv, ri) => (
              <tr key={ri} className="border-t border-slate-100">
                <td className="px-2 py-1 font-medium text-slate-700 tnum">{num(sens.vol_axis[ri] * 100)}%</td>
                {rowv.map((v, ci) => {
                  const base = ri === 1 && ci === 1;
                  return <td key={ci} className={`px-2 py-1 text-right tnum ${base ? "bg-navy-50 font-semibold text-navy-900" : "text-slate-700"}`}>{num(v)}</td>;
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-slate-400">
        중앙(굵게)=기준 셀. 축: 변동성 ±{((sens.meta.vol_bump ?? 0.05) * 100).toFixed(0)}%p × 기초자산 ±{((sens.meta.spot_bump ?? 0.05) * 100).toFixed(0)}%.
        report_steps {sens.meta.steps_used}·{sens.meta.model}{sens.meta.vol_floor_applied ? " · 변동성 하한 적용됨" : ""}
      </p>
    </div>
  );
}

// ── 없음 안내(하위호환) ──────────────────────────────────────────────────────
const NotIncluded = () => (
  <p className="text-xs text-slate-500">이 평가 결과에는 포함되지 않습니다(엔진 확장 이전 평가).</p>
);

// ── 최상위 ──────────────────────────────────────────────────────────────────
export function CalculationBasis({ result, jobId }: { result: PricingResult; jobId: number }) {
  const trees = result.trees;
  const meta = trees?.tree_meta;
  const userSteps = (result.key_parameters?.lattice_steps as number | undefined) ?? undefined;
  return (
    <Card>
      <CardHeader title="계산 근거" desc="실보고서 Appendix 대응 — 입력·파라미터·이자율·트리·민감도·재현성(추적성/재현성)." />
      <CardBody>
        <Disclosure title="계산 근거 상세 열기" right={<Badge tone="gray">7개 항목</Badge>}>
          <div className="space-y-2">
            <Disclosure title="1. 입력 스냅샷 (평가 당시)">
              <InputSnapshot jobId={jobId} instrumentId={result.instrument_id} />
            </Disclosure>

            <Disclosure title="2. 적용 파라미터">
              <ParamsTable kp={result.key_parameters ?? {}} />
            </Disclosure>

            <Disclosure title="3. 이자율 산정 (YTM·SPOT·FORWARD)">
              {result.curve_bootstrap ? (
                <div className="space-y-3">
                  <p className="text-xs text-slate-400">모드: {result.curve_bootstrap.rate_mode} · {result.curve_bootstrap.rf?.assumption ?? ""}</p>
                  {result.curve_bootstrap.rf && <RateTableView table={result.curve_bootstrap.rf} title="무위험(rf)" />}
                  {result.curve_bootstrap.rd && <RateTableView table={result.curve_bootstrap.rd} title="위험(rd)" />}
                </div>
              ) : <NotIncluded />}
            </Disclosure>

            <Disclosure title="4. 위험중립확률 (스텝별 p)">
              {trees ? <ProbTable prob={trees.risk_neutral_prob} /> : <NotIncluded />}
            </Disclosure>

            <Disclosure title="5. 가격 트리"
              right={meta ? <span>보고서용 {meta.steps_used}스텝 · {meta.rate_mode ?? "-"}</span> : undefined}>
              {trees ? (
                <div className="space-y-2">
                  <p className="text-xs text-slate-500">
                    트리는 보고서용 <b>{meta?.steps_used}</b>스텝(가독·저장), 평가값(12키)은 사용자 <b>{userSteps ?? "?"}</b>스텝(정밀). 정방향 step0→n, 상삼각 외 ‘–’. u={num(meta?.u)}·d={num(meta?.d)}.
                  </p>
                  <PriceTrees trees={trees} />
                </div>
              ) : <NotIncluded />}
            </Disclosure>

            <Disclosure title="6. 민감도 분석 (변동성 × 기초자산)">
              {result.sensitivity ? <SensitivityTable sens={result.sensitivity} /> : <NotIncluded />}
            </Disclosure>

            <Disclosure title="7. 재현성(심화)">
              <ReproDeep repro={result.reproducibility ?? {}} rateMode={meta?.rate_mode} />
            </Disclosure>
          </div>
        </Disclosure>
      </CardBody>
    </Card>
  );
}

function ReproDeep({ repro, rateMode }: { repro: Record<string, unknown>; rateMode?: string }) {
  const rows: [string, unknown][] = [
    ["input_hash", repro.input_hash], ["seed", repro.seed], ["model_version", repro.model_version],
    ["engine_commit", repro.engine_commit], ["computed_at", repro.computed_at], ["rate_mode", rateMode],
  ];
  return (
    <table className="w-full text-sm">
      <tbody>
        {rows.map(([k, v]) => (
          <tr key={k} className="border-b border-slate-100">
            <td className="py-1 text-xs text-slate-500">{k}</td>
            <td className="break-all text-right font-mono text-xs text-slate-800">{v == null ? "–" : String(v)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
