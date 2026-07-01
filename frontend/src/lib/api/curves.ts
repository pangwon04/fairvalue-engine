import { api } from "../apiClient";
import type { CurveListResponse } from "../types";
export const listCurves = (kind?: "RISK_FREE" | "CREDIT") =>
  api.get<CurveListResponse>(`/curves${kind ? `?kind=${kind}` : ""}`);
