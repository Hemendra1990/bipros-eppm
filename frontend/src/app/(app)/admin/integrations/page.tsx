"use client";

import { VirtualDataTable, type ColumnDef } from "@/components/common/VirtualDataTable";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { integrationApi, type IntegrationConfig } from "@/lib/api/integrationApi";
import { getErrorMessage } from "@/lib/utils/error";

function statusBadgeClass(status: IntegrationConfig["status"]): string {
  switch (status) {
    case "ACTIVE":
      return "bg-success/10 text-success ring-success/20";
    case "INACTIVE":
      return "bg-surface-active/60 text-text-secondary ring-border/50";
    case "ERROR":
      return "bg-danger/10 text-danger ring-red-500/20";
    default:
      return "bg-surface-active/60 text-text-secondary ring-border/50";
  }
}

export default function IntegrationsPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ["integrations"],
    // Endpoint is /v1/integrations — protected by ROLE_ADMIN on the backend.
    queryFn: () => integrationApi.listIntegrations().then((r) => r.data),
    retry: 0,
  });

  const integrations = useMemo(() => data ?? [], [data]);

  const columns = useMemo<ColumnDef<IntegrationConfig>[]>(() => [
    {
      accessorKey: "systemCode",
      header: "Code",
      cell: ({ row }) => <span className="font-mono text-xs text-text-secondary">{row.original.systemCode}</span>,
    },
    {
      accessorKey: "systemName",
      header: "Name",
      cell: ({ row }) => <span className="text-text-primary">{row.original.systemName}</span>,
    },
    {
      accessorKey: "authType",
      header: "Auth",
      cell: ({ row }) => <span className="text-xs text-text-secondary">{row.original.authType}</span>,
    },
    {
      accessorKey: "baseUrl",
      header: "Base URL",
      cell: ({ row }) => <span className="text-xs text-text-secondary break-all">{row.original.baseUrl}</span>,
    },
    {
      accessorKey: "isEnabled",
      header: "Enabled",
      cell: ({ row }) => (
        <span
          className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium ring-1 ${
            row.original.isEnabled
              ? "bg-accent/10 text-blue-300 ring-accent/20"
              : "bg-surface-active/50 text-text-secondary ring-border/40"
          }`}
        >
          {row.original.isEnabled ? "Enabled" : "Disabled"}
        </span>
      ),
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => (
        <span
          className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium ring-1 ${statusBadgeClass(row.original.status)}`}
        >
          {row.original.status}
        </span>
      ),
    },
    {
      accessorKey: "lastSyncAt",
      header: "Last Sync",
      cell: ({ row }) => (
        <span className="text-xs text-text-secondary">
          {row.original.lastSyncAt ? new Date(row.original.lastSyncAt).toLocaleString() : "—"}
        </span>
      ),
    },
  ], []);

  return (
    <div className="space-y-4 p-4">
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Government Integrations</h1>
        <p className="mt-1 text-sm text-text-secondary">
          External systems wired into IC-PMS (PFMS, GeM, CPGRAMS, NIC SSO, and the like).
        </p>
      </div>

      {isLoading ? (
        <div className="rounded-lg border border-border bg-surface/50 p-6 text-center text-text-secondary">
          Loading integrations…
        </div>
      ) : error ? (
        <div className="rounded-lg border border-danger/30 bg-danger/10 p-4 text-sm text-danger">
          Failed to load integrations: {getErrorMessage(error, "Unknown error")}
          <div className="mt-2 text-xs text-danger/70">
            This endpoint requires the ADMIN role. If you are logged in as a non-admin user you will see a 401/403.
          </div>
        </div>
      ) : integrations.length === 0 ? (
        <div className="rounded-lg border border-dashed border-border p-8 text-center text-text-secondary">
          No integrations configured yet.
        </div>
      ) : (

        <VirtualDataTable
          columns={columns}
          data={integrations}
          sortable
          resizable
          searchable={false}
          className="rounded-xl border border-border bg-surface/50 shadow-lg"
        />
      )}
    </div>
  );
}
