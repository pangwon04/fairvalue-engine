import { api } from "../apiClient";
import type { InstrumentDto, InstrumentType } from "../types";
export const listInstruments = (type?: InstrumentType, includeArchived = false) => {
  const q = new URLSearchParams();
  if (type) q.set("type", type);
  if (includeArchived) q.set("include_archived", "true");
  const qs = q.toString();
  return api.get<{ items: InstrumentDto[] }>(`/instruments${qs ? `?${qs}` : ""}`);
};
export const getInstrument = (id: number) => api.get<InstrumentDto>(`/instruments/${id}`);
export const createInstrument = (type: InstrumentType, name: string, issuer: string) =>
  api.post<InstrumentDto>("/instruments", { type, name, issuer });

export interface DeleteInstrumentResponse { deleted: "soft" | "hard"; instrument_id: number; status: string | null; }
export const deleteInstrument = (id: number) => api.del<DeleteInstrumentResponse>(`/instruments/${id}`);
