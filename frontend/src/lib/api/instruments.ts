import { api } from "../apiClient";
import type { InstrumentDto, InstrumentType } from "../types";
export const listInstruments = (type?: InstrumentType) =>
  api.get<{ items: InstrumentDto[] }>(`/instruments${type ? `?type=${type}` : ""}`);
export const getInstrument = (id: number) => api.get<InstrumentDto>(`/instruments/${id}`);
export const createInstrument = (type: InstrumentType, name: string, issuer: string) =>
  api.post<InstrumentDto>("/instruments", { type, name, issuer });
