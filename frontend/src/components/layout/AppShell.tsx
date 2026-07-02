"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { cn } from "@/lib/cn";
import { clearAuth, getUser } from "@/lib/auth";
import { NAV, type NavItem } from "@/lib/nav";
import { Button } from "@/components/ui/Button";

function isActivePath(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(href + "/");
}

function NavLink({ item, pathname, indent }: { item: NavItem; pathname: string; indent?: boolean }) {
  const on = isActivePath(pathname, item.href);
  return (
    <Link
      href={item.href}
      className={cn(
        "flex items-center justify-between rounded-lg px-3 py-2 text-sm transition",
        indent && "pl-6",
        on ? "bg-navy-700 text-white" : "text-slate-300 hover:bg-navy-800 hover:text-white",
      )}
    >
      <span>{item.label}</span>
      {!item.active && (
        <span className="ml-2 rounded px-1.5 py-0.5 text-[10px] font-medium text-navy-200 ring-1 ring-inset ring-navy-600">
          준비 중
        </span>
      )}
    </Link>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const user = getUser();

  const items = NAV.filter((n) => !n.adminOnly || user?.role === "ORG_ADMIN");

  return (
    <div className="flex min-h-screen">
      {/* 사이드바 */}
      <aside className="flex w-56 shrink-0 flex-col bg-navy-900 text-slate-200">
        <div className="px-5 py-5 text-lg font-semibold text-white">FairValue</div>
        <nav className="mt-1 space-y-0.5 px-3">
          {items.map((item) => (
            <div key={item.href} className="space-y-0.5">
              <NavLink item={item} pathname={pathname} />
              {item.children?.map((c) => (
                <NavLink key={c.href} item={c} pathname={pathname} indent />
              ))}
            </div>
          ))}
        </nav>
        <div className="mt-auto px-5 py-4 text-xs text-slate-500">복합금융상품 공정가치 평가</div>
      </aside>

      {/* 본문 */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
          <div className="text-sm text-slate-500">복합금융상품 공정가치 평가 플랫폼</div>
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
