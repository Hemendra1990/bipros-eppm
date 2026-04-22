"use client";

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

  const integrations = data ?? [];

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
        <div className="overflow-x-auto rounded-xl border border-border bg-surface/50 shadow-lg">
          <table className="w-full text-sm">
            <thead className="border-b border-border/60 bg-surface/80 text-left">
              <tr>
                <th className="px-4 py-3 font-semibold text-text-secondary">Code</th>
                <th className="px-4 py-3 font-semibold text-text-secondary">Name</th>
                <th className="px-4 py-3 font-semibold text-text-secondary">Auth</th>
                <th className="px-4 py-3 font-semibold text-text-secondary">Base URL</th>
                <th className="px-4 py-3 font-semibold text-text-secondary">Enabled</th>
                <th className="px-4 py-3 font-semibold text-text-secondary">Status</th>
                <th className="px-4 py-3 font-semibold text-text-secondary">Last Sync</th>
              </tr>
            </thead>
            <tbody>
              {integrations.map((ic) => (
                <tr
                  key={ic.id}
                  className="border-b border-border/60 last:border-b-0 hover:bg-surface-hover/30"
                >
                  <td className="px-4 py-3 font-mono text-xs text-text-secondary">{ic.systemCode}</td>
                  <td className="px-4 py-3 text-text-primary">{ic.systemName}</td>
                  <td className="px-4 py-3 text-xs text-text-secondary">{ic.authType}</td>
                  <td className="px-4 py-3 text-xs text-text-secondary break-all">{ic.baseUrl}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium ring-1 ${
                        ic.isEnabled
                          ? "bg-accent/10 text-blue-300 ring-accent/20"
                          : "bg-surface-active/50 text-text-secondary ring-border/40"
                      }`}
                    >
                      {ic.isEnabled ? "Enabled" : "Disabled"}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium ring-1 ${statusBadgeClass(ic.status)}`}
                    >
                      {ic.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-text-secondary">
                    {ic.lastSyncAt ? new Date(ic.lastSyncAt).toLocaleString() : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
