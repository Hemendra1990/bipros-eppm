"use client";

import { useQuery } from "@tanstack/react-query";
import { organisationApi } from "@/lib/api/organisationApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import type { OrganisationResponse, OrganisationType } from "@/lib/types";

const ORG_TYPE_COLORS: Record<OrganisationType, string> = {
  EMPLOYER: "bg-indigo-500/20 text-indigo-300",
  SPV: "bg-blue-500/20 text-blue-300",
  PMC: "bg-emerald-500/20 text-emerald-300",
  EPC_CONTRACTOR: "bg-amber-500/20 text-amber-300",
  GOVERNMENT_AUDITOR: "bg-purple-500/20 text-purple-300",
};

export default function OrganisationsPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ["organisations"],
    queryFn: () => organisationApi.listAll(),
  });

  const orgs = data?.data ?? [];

  const columns: ColumnDef<OrganisationResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    { key: "name", label: "Name", sortable: true },
    { key: "shortName", label: "Short Name" },
    {
      key: "organisationType",
      label: "Type",
      sortable: true,
      render: (value) => {
        const t = value as OrganisationType;
        return (
          <span className={`rounded px-2 py-0.5 text-xs font-medium ${ORG_TYPE_COLORS[t]}`}>
            {t.replace(/_/g, " ")}
          </span>
        );
      },
    },
    {
      key: "active",
      label: "Status",
      render: (value) =>
        value ? (
          <span className="text-emerald-400">Active</span>
        ) : (
          <span className="text-slate-500">Inactive</span>
        ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Organisations"
        description="IC-PMS organisation master — NICDC, state SPVs, PMC firms, EPC contractors, auditors."
      />

      {isLoading && <div className="py-12 text-center text-slate-500">Loading organisations...</div>}

      {error && (
        <div className="rounded-md bg-red-500/10 p-4 text-sm text-red-400">
          Failed to load organisations. Is the backend running?
        </div>
      )}

      {!isLoading && orgs.length === 0 && (
        <EmptyState title="No organisations yet" description="IC-PMS seed data has not run." />
      )}

      {orgs.length > 0 && <DataTable data={orgs} columns={columns} rowKey="id" />}
    </div>
  );
}
