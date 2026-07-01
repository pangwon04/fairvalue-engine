import type { User } from "./types";

const TOKEN_KEY = "fv_token";
const USER_KEY = "fv_user";

// ★ 속도 우선 localStorage 저장. 프로덕션 전 httpOnly 쿠키로 강화 예정.
export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}
export function getUser(): User | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as User) : null;
}
export function setAuth(token: string, user: User) {
  window.localStorage.setItem(TOKEN_KEY, token);
  window.localStorage.setItem(USER_KEY, JSON.stringify(user));
}
export function clearAuth() {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(USER_KEY);
}
export function isAuthed(): boolean { return !!getToken(); }
