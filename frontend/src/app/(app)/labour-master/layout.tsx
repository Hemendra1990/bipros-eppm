"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useAppStore } from "@/lib/state/store";
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
  const search = useSearchParams();
  const urlId = search?.get("projectId") || undefined;
  const storedId = useAppStore((s) => s.currentProjectId) || undefined;
  const projectId = urlId ?? storedId;
  const queryString = projectId ? `?projectId=${projectId}` : "";

  return (
    <div className="space-y-6 p-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-[28px] font-semibold leading-tight text-charcoal">Labour Master</h1>
          <p className="text-[13px] text-slate">Designation catalogue and per-project deployments.</p>
        </div>
        <Link
          href={`/labour-master/new`}
          className="inline-flex items-center gap-1.5 rounded-md bg-gold px-3.5 py-2 text-[13px] font-semibold text-charcoal hover:bg-gold-deep transition"
        >
          + Add Designation
        </Link>
      </header>

      <nav className="border-b border-hairline flex gap-1 text-[13px]" aria-label="Labour Master sections">
        {TABS.map((t) => {
          const active = isActive(pathname, t.href);
          // Reference + New routes don't need a project; keep the query out so the URL is clean.
          const carryProject = t.href !== "/labour-master/reference";
          const href = carryProject ? `${t.href}${queryString}` : t.href;
          return (
            <Link
              key={t.href}
              href={href}
              className={
                active
                  ? "px-3 py-2 -mb-px border-b-2 border-gold text-charcoal font-medium"
                  : "px-3 py-2 -mb-px border-b-2 border-transparent text-slate hover:text-charcoal transition"
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
