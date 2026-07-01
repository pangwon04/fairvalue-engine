import type { ProductSchema, FormValues } from "./types";

export function setByPath(obj: Record<string, any>, path: string, value: unknown) {
  const keys = path.split(".");
  let cur = obj;
  for (let i = 0; i < keys.length - 1; i++) {
    const k = keys[i];
    if (typeof cur[k] !== "object" || cur[k] === null) cur[k] = {};
    cur = cur[k];
  }
  cur[keys[keys.length - 1]] = value;
}

// flat form values(field.key)를 bind 경로로 중첩 rawForm 조립.
// 빈 값(undefined/"")은 제외. PUT /instruments/{id}/terms 본문.
export function buildRawForm(schema: ProductSchema, values: FormValues): Record<string, unknown> {
  const raw: Record<string, unknown> = {};
  for (const step of schema.steps) {
    for (const f of step.fields) {
      if (!f.bind) continue;
      const v = values[f.key];
      if (v === undefined || v === "" || v === null) continue;
      setByPath(raw, f.bind, v);
    }
  }
  return raw;
}

export function initialValues(schema: ProductSchema): FormValues {
  const v: FormValues = {};
  for (const step of schema.steps) {
    for (const f of step.fields) {
      if (f.defaultValue !== undefined) v[f.key] = f.defaultValue;
    }
  }
  return v;
}
