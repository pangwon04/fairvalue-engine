import { api } from "../apiClient";
import type { TermsSaveResponse, TermsDraftResponse } from "../types";
// rawForm 전체(중첩 객체)를 PUT — 폼 렌더러가 bind 경로로 조립.
export const saveTerms = (id: number, rawForm: Record<string, unknown>) =>
  api.put<TermsSaveResponse>(`/instruments/${id}/terms`, rawForm);
export const getTerms = (id: number) => api.get<TermsDraftResponse>(`/instruments/${id}/terms`);
