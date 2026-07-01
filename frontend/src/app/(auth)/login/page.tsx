"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { login } from "@/lib/api/auth";
import { setAuth } from "@/lib/auth";
import { ApiError } from "@/lib/apiClient";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Field } from "@/components/ui/Field";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [pw, setPw] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(""); setBusy(true);
    try {
      const r = await login(email, pw);
      setAuth(r.token, r.user);
      router.replace("/instruments");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "로그인 실패");
    } finally { setBusy(false); }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader title="FairValue 로그인" desc="복합금융상품 공정가치 평가" />
        <CardBody>
          <form onSubmit={submit} className="space-y-4">
            <Field label="이메일"><Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></Field>
            <Field label="비밀번호" error={err}><Input type="password" value={pw} onChange={(e) => setPw(e.target.value)} required /></Field>
            <Button type="submit" className="w-full" disabled={busy}>{busy ? "로그인 중…" : "로그인"}</Button>
          </form>
          <p className="mt-4 text-center text-sm text-slate-500">
            계정이 없나요? <Link href="/signup" className="font-medium text-navy-700 hover:underline">회원가입</Link>
          </p>
        </CardBody>
      </Card>
    </div>
  );
}
