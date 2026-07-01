import { api } from "../apiClient";
import type { PriceJobResponse, JobDto, PricingResult, PricingTrigger } from "../types";
export const priceInstrument = (id: number, trigger: PricingTrigger) =>
  api.post<PriceJobResponse>(`/instruments/${id}/price`, trigger);
export const getJob = (jobId: number) => api.get<JobDto>(`/jobs/${jobId}`);
export const getResult = (jobId: number) => api.get<PricingResult>(`/jobs/${jobId}/result`);
