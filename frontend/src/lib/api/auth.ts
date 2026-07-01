import { api } from "../apiClient";
import type { AuthResult } from "../types";
export const signup = (email: string, pw: string, org_code: string) =>
  api.post<AuthResult>("/auth/signup", { email, pw, org_code });
export const login = (email: string, pw: string) =>
  api.post<AuthResult>("/auth/login", { email, pw });
