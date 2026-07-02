import { getToken } from "./auth";

const BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  body: unknown;
  constructor(status: number, message: string, body: unknown) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

function parse(text: string): unknown {
  if (!text) return null;
  try { return JSON.parse(text); } catch { return text; }
}

function fail(status: number, data: unknown): never {
  const msg =
    (data && typeof data === "object" && "message" in data && (data as { message?: string }).message) ||
    `요청 실패 (${status})`;
  throw new ApiError(status, String(msg), data);
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(`${BASE}${path}`, {
    method, headers, body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const data = parse(await res.text());
  if (!res.ok) fail(res.status, data);
  return data as T;
}

// multipart/form-data(파일 업로드). Content-Type 은 브라우저가 boundary 와 함께 자동 설정.
async function requestForm<T>(path: string, form: FormData): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(`${BASE}${path}`, { method: "POST", headers, body: form });
  const data = parse(await res.text());
  if (!res.ok) fail(res.status, data);
  return data as T;
}

export const api = {
  get: <T>(p: string) => request<T>("GET", p),
  post: <T>(p: string, b?: unknown) => request<T>("POST", p, b),
  put: <T>(p: string, b?: unknown) => request<T>("PUT", p, b),
  postForm: <T>(p: string, f: FormData) => requestForm<T>(p, f),
  base: BASE,
};
