"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { cn } from "@/lib/cn";
import { clearAuth, getUser } from "@/lib/auth";
import { Button } from "@/components/ui/Button";

const nav = [{ href: "/instruments", label: "상품" }];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const user = getUser();
  return (
    <div className="flex min-h-screen">
      {/* 사이드바 */}
      <aside className="w-56 shrink-0 bg-navy-900 text-slate-200">
        <div className="px-5 py-5 text-lg font-semibold text-white">FairValue</div>
        <nav className="mt-2 space-y-1 px-3">
          {nav.map((n) => (
            <Link
              key={n.href}
              href={n.href}
              className={cn("block rounded-lg px-3 py-2 text-sm transition",
                pathname.startsWith(n.href) ? "bg-navy-700 text-white" : "text-slate-300 hover:bg-navy-800 hover:text-white")}
            >
              {n.label}
            </Link>
          ))}
        </nav>
      </aside>
      {/* 본문 */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
          <div className="text-sm text-slate-500">복합금융상품 공정가치 평가</div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-slate-600">{user?.email}</span>
            <Button variant="secondary" onClick={() => { clearAuth(); router.replace("/login"); }}>로그아웃</Button>
          </div>
        </header>
        <main className="min-w-0 flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
