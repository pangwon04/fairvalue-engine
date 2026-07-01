// Form Schema (shared/schemas/form-schema.ts v0.1) 의 런타임 타입.
export type FieldType =
  | "text" | "date" | "currency" | "percentage" | "number" | "select" | "toggle"
  | "assetSearch" | "curveSelector" | "computed" | "readonly";

export interface FieldOption { label: string; value: string | number; }
export interface ValidationRule {
  rule: string; params?: Record<string, unknown>; message: string; severity: "error" | "warning";
}
export interface ShowWhen { field: string; op: "truthy" | "falsy" | "in"; value?: unknown[]; }
export interface FieldSchema {
  key: string; label: string; type: FieldType; bind?: string;
  required?: boolean; unit?: string; help?: string; defaultValue?: unknown;
  options?: FieldOption[]; validations?: ValidationRule[]; showWhen?: ShowWhen;
  computeFrom?: string[]; resolve?: { to: string; kind: string };
}
export interface StepSchema { id: string; title: string; fields: FieldSchema[]; }
export interface ProductSchema { product: string; version: string; title: string; steps: StepSchema[]; }

export type FormValues = Record<string, unknown>;   // field.key -> value (flat)
