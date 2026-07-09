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
      router.replace("/dashboard");
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
          {/* ★ 데모 계정 안내 자리(배포 시 값 기입). 회원가입으로 새 조직 생성도 가능. */}
          <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-center text-xs text-slate-500">
            데모 체험용 계정: <span className="font-medium text-slate-600">추후 안내 예정</span>
            <br />또는 회원가입으로 새 조직을 만들어 바로 사용할 수 있습니다.
          </div>
        </CardBody>
      </Card>
    </div>
  );
}
