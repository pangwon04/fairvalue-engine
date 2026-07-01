"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { isAuthed } from "@/lib/auth";

// 미로그인 시 /login 으로. 클라이언트 가드(localStorage 토큰).
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [ok, setOk] = useState(false);
  useEffect(() => {
    if (!isAuthed()) router.replace("/login");
    else setOk(true);
  }, [router]);
  if (!ok) return null;
  return <>{children}</>;
}
