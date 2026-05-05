"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { Plus } from "lucide-react";
import { permitApi, type PermitStatus } from "@/lib/api/permitApi";
import { PermitStatusBadge, PermitTypeBadge, RiskBadge } from "@/components/permits";
import { VirtualDataTable, type ColumnDef } from "@/components/common/VirtualDataTable";

const TABS: Array<{ key: "ALL" | PermitStatus; label: string }> = [
  { key: "ALL", label: "All" },
  { key: "ISSUED", label: "Approved" },
  { key: "PENDING_SITE_ENGINEER", label: "Pending Review" },
  { key: "PENDING_HSE", label: "Pending Safety" },
  { key: "REJECTED", label: "Rejected" },
  { key: "CLOSED", label: "Closed" },
];

export default function PermitRegisterPage() {
  return (
    <Suspense fallback={<div className="p-6 text-sm text-slate">Loading…</div>}>
      <PermitRegisterPageInner />
    </Suspense>
  );
}

function PermitRegisterPageInner() {
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

  const columns: ColumnDef<(typeof rows)[number]>[] = [
    {
      accessorKey: "permitCode",
      header: "Permit ID",
      cell: ({ row }) => (
        <span className="font-mono text-xs font-semibold text-gold-deep">{row.original.permitCode}</span>
      ),
    },
    {
      accessorKey: "permitTypeCode",
      header: "Type",
      cell: ({ row }) => (
        <PermitTypeBadge
          code={row.original.permitTypeCode}
          name={row.original.permitTypeName}
          colorHex={row.original.permitTypeColorHex}
        />
      ),
    },
    {
      accessorKey: "workDescription",
      header: "Work Description",
      cell: ({ row }) => (
        <span className="max-w-[260px] truncate text-charcoal">{row.original.workDescription}</span>
      ),
    },
    {
      accessorKey: "principalWorkerName",
      header: "Worker",
      cell: ({ row }) => <span className="text-charcoal">{row.original.principalWorkerName}</span>,
    },
    {
      accessorKey: "principalWorkerNationality",
      header: "Nationality",
      cell: ({ row }) => <span className="text-slate">{row.original.principalWorkerNationality}</span>,
    },
    {
      accessorKey: "shift",
      header: "Shift",
      cell: ({ row }) => <span className="text-slate">{row.original.shift}</span>,
    },
    {
      accessorKey: "riskLevel",
      header: "Risk",
      cell: ({ row }) => <RiskBadge level={row.original.riskLevel} />,
    },
    {
      accessorKey: "startAt",
      header: "Date",
      cell: ({ row }) => (
        <span className="text-slate">{new Date(row.original.startAt).toLocaleDateString()}</span>
      ),
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => <PermitStatusBadge status={row.original.status} />,
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) => (
        <div className="text-right">
          <Link
            href={`/permits/${row.original.id}`}
            className="rounded-md border border-divider bg-paper px-3 py-1 text-xs font-semibold text-charcoal hover:bg-ivory"
          >
            View
          </Link>
        </div>
      ),
    },
  ];

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

      {isLoading ? (
        <div className="py-6 text-center text-slate">Loading…</div>
      ) : rows.length === 0 ? (
        <div className="py-6 text-center text-slate">No permits match this filter.</div>
      ) : (
        <VirtualDataTable columns={columns} data={rows} sortable resizable searchable={false} className="shadow-sm" />
      )}
    </div>
  );
}
