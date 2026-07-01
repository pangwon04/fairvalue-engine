import type { FieldSchema, FormValues } from "./types";
export function visible(f: FieldSchema, values: FormValues): boolean {
  if (!f.showWhen) return true;
  const target = values[f.showWhen.field];
  switch (f.showWhen.op) {
    case "truthy": return !!target;
    case "falsy": return !target;
    case "in": return Array.isArray(f.showWhen.value) && f.showWhen.value.includes(target as never);
    default: return true;
  }
}
