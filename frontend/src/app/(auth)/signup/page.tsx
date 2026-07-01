"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { signup } from "@/lib/api/auth";
import { setAuth } from "@/lib/auth";
import { ApiError } from "@/lib/apiClient";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Field } from "@/components/ui/Field";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";

export default function SignupPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [pw, setPw] = useState("");
  const [orgCode, setOrgCode] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(""); setBusy(true);
    try {
      const r = await signup(email, pw, orgCode);
      setAuth(r.token, r.user);
      router.replace("/instruments");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "회원가입 실패");
    } finally { setBusy(false); }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader title="회원가입" desc="신규 조직코드면 조직이 생성되고 관리자 권한이 부여됩니다." />
        <CardBody>
          <form onSubmit={submit} className="space-y-4">
            <Field label="이메일"><Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></Field>
            <Field label="비밀번호"><Input type="password" value={pw} onChange={(e) => setPw(e.target.value)} required /></Field>
            <Field label="조직 코드" error={err} help="기존 조직에 합류하려면 해당 코드, 새로 만들려면 새 코드">
              <Input value={orgCode} onChange={(e) => setOrgCode(e.target.value)} required />
            </Field>
            <Button type="submit" className="w-full" disabled={busy}>{busy ? "가입 중…" : "회원가입"}</Button>
          </form>
          <p className="mt-4 text-center text-sm text-slate-500">
            이미 계정이 있나요? <Link href="/login" className="font-medium text-navy-700 hover:underline">로그인</Link>
          </p>
        </CardBody>
      </Card>
    </div>
  );
}
