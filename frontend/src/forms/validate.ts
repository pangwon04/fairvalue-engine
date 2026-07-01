import type { ProductSchema, FieldSchema, FormValues } from "./types";
import { visible } from "./showWhen";

// 클라이언트 1차 검증(백엔드 validation[]과 병합 표시). error 만 제출 차단.
export function validateForm(schema: ProductSchema, values: FormValues): Record<string, string> {
  const errors: Record<string, string> = {};
  const num = (v: unknown) => (v === "" || v == null ? NaN : Number(v));
  for (const step of schema.steps) {
    for (const f of step.fields) {
      if (!visible(f, values)) continue;
      const v = values[f.key];
      if (f.required && (v === undefined || v === "" || v === null)) {
        errors[f.key] = `${f.label}은(는) 필수입니다.`;
        continue;
      }
      for (const rule of f.validations ?? []) {
        if (rule.severity !== "error") continue;
        const bad = failsRule(rule.rule, rule.params, v, f, values, num);
        if (bad) { errors[f.key] = rule.message; break; }
      }
    }
  }
  return errors;
}

function failsRule(
  rule: string, params: Record<string, unknown> | undefined, v: unknown,
  f: FieldSchema, values: FormValues, num: (x: unknown) => number,
): boolean {
  switch (rule) {
    case "required": return v === undefined || v === "" || v === null;
    case "positive": case "pricePositive": case "volatilityPositive":
      return !(num(v) > 0);
    case "percentageRange": {
      const n = num(v); const min = Number(params?.min ?? 0); const max = Number(params?.max ?? 100);
      return isNaN(n) || n < min || n > max;
    }
    case "min": return num(v) < Number(params?.min ?? 0);
    case "maturityAfterIssueDate": {
      const issue = values[(params?.issueField as string) ?? "issue_date"];
      if (!v || !issue) return false;
      return new Date(String(v)) <= new Date(String(issue));
    }
    default: return false;   // 서버측 규칙(assetRequired/curveRequired 등)은 백엔드 검증에 위임
  }
}
