"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { Plus } from "lucide-react";
import { permitApi, type PermitStatus } from "@/lib/api/permitApi";
import { PermitStatusBadge, PermitTypeBadge, RiskBadge } from "@/components/permits";

const TABS: Array<{ key: "ALL" | PermitStatus; label: string }> = [
  { key: "ALL", label: "All" },
  { key: "ISSUED", label: "Approved" },
  { key: "PENDING_SITE_ENGINEER", label: "Pending Review" },
  { key: "PENDING_HSE", label: "Pending Safety" },
  { key: "REJECTED", label: "Rejected" },
  { key: "CLOSED", label: "Closed" },
];

export default function PermitRegisterPage() {
  const search = useSearchParams();
  const projectId = search.get("projectId") || undefined;
  const [active, setActive] = useState<"ALL" | PermitStatus>("ALL");

  const { data, isLoading } = useQuery({
    queryKey: ["permits-list", projectId, active],
    queryFn: () =>
      permitApi.list({
        projectId,
        status: active === "ALL" ? undefined : active,
        size: 50,
      }),
  });

  const rows = data?.content ?? [];

  return (
    <div className="space-y-6 p-6">
      <header className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-slate">Permit Register</p>
          <h1 className="mt-1 text-2xl font-bold text-charcoal">All Work Permits</h1>
        </div>
        <Link
          href="/permits/new"
          className="inline-flex items-center gap-1.5 rounded-md bg-gold px-4 py-2 text-sm font-semibold text-charcoal shadow-sm transition hover:bg-gold-deep"
        >
          <Plus size={16} /> New Permit
        </Link>
      </header>

      <nav className="flex flex-wrap gap-2">
        {TABS.map((t) => {
          const isActive = active === t.key;
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => setActive(t.key)}
              className={`rounded-full border px-4 py-1.5 text-xs font-semibold transition ${
                isActive
                  ? "border-gold bg-gold-tint text-gold-ink"
                  : "border-divider bg-paper text-slate hover:border-gold/30"
              }`}
            >
              {t.label}
            </button>
          );
        })}
      </nav>

      <div className="overflow-hidden rounded-xl border border-hairline bg-paper shadow-sm">
        <table className="w-full text-sm">
          <thead className="bg-ivory text-left text-xs font-semibold uppercase tracking-wider text-slate">
            <tr>
              <th className="px-4 py-3">Permit ID</th>
              <th className="px-4 py-3">Type</th>
              <th className="px-4 py-3">Work Description</th>
              <th className="px-4 py-3">Worker</th>
              <th className="px-4 py-3">Nationality</th>
              <th className="px-4 py-3">Shift</th>
              <th className="px-4 py-3">Risk</th>
              <th className="px-4 py-3">Date</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-hairline">
            {isLoading && (
              <tr>
                <td colSpan={10} className="px-4 py-6 text-center text-slate">
                  Loading…
                </td>
              </tr>
            )}
            {!isLoading && rows.length === 0 && (
              <tr>
                <td colSpan={10} className="px-4 py-6 text-center text-slate">
                  No permits match this filter.
                </td>
              </tr>
            )}
            {rows.map((p) => (
              <tr key={p.id} className="hover:bg-ivory">
                <td className="px-4 py-3 font-mono text-xs font-semibold text-gold-deep">
                  {p.permitCode}
                </td>
                <td className="px-4 py-3">
                  <PermitTypeBadge
                    code={p.permitTypeCode}
                    name={p.permitTypeName}
                    colorHex={p.permitTypeColorHex}
                  />
                </td>
                <td className="px-4 py-3 max-w-[260px] truncate text-charcoal">
                  {p.workDescription}
                </td>
                <td className="px-4 py-3 text-charcoal">{p.principalWorkerName}</td>
                <td className="px-4 py-3 text-slate">{p.principalWorkerNationality}</td>
                <td className="px-4 py-3 text-slate">{p.shift}</td>
                <td className="px-4 py-3">
                  <RiskBadge level={p.riskLevel} />
                </td>
                <td className="px-4 py-3 text-slate">
                  {new Date(p.startAt).toLocaleDateString()}
                </td>
                <td className="px-4 py-3">
                  <PermitStatusBadge status={p.status} />
                </td>
                <td className="px-4 py-3 text-right">
                  <Link
                    href={`/permits/${p.id}`}
                    className="rounded-md border border-divider bg-paper px-3 py-1 text-xs font-semibold text-charcoal hover:bg-ivory"
                  >
                    View
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
