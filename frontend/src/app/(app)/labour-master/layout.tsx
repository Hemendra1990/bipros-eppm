"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

const TABS = [
  { href: "/labour-master",           label: "Dashboard" },
  { href: "/labour-master/cards",     label: "Cards" },
  { href: "/labour-master/table",     label: "Table" },
  { href: "/labour-master/reference", label: "Reference" },
];

function isActive(pathname: string, href: string): boolean {
  if (href === "/labour-master") return pathname === href;
  return pathname === href || pathname.startsWith(href + "/");
}

export default function LabourMasterLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname() ?? "";
  return (
    <div className="space-y-6 p-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">Labour Master</h1>
          <p className="text-sm text-muted-foreground">
            Designation catalogue and per-project deployments.
          </p>
        </div>
        <Link
          href="/labour-master/new"
          className="inline-flex items-center gap-1.5 rounded-md bg-foreground px-3.5 py-2 text-sm font-medium text-background hover:opacity-90 transition"
        >
          + Add Designation
        </Link>
      </header>

      <nav className="border-b border-border flex gap-1 text-sm" aria-label="Labour Master sections">
        {TABS.map((t) => {
          const active = isActive(pathname, t.href);
          return (
            <Link
              key={t.href}
              href={t.href}
              className={
                active
                  ? "px-3 py-2 -mb-px border-b-2 border-foreground text-foreground font-medium"
                  : "px-3 py-2 -mb-px border-b-2 border-transparent text-muted-foreground hover:text-foreground transition"
              }
              aria-current={active ? "page" : undefined}
            >
              {t.label}
            </Link>
          );
        })}
      </nav>

      <div>{children}</div>
    </div>
  );
}
