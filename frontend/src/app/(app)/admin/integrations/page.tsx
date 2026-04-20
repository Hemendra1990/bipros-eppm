"use client";

import { useQuery } from "@tanstack/react-query";
import { integrationApi, type IntegrationConfig } from "@/lib/api/integrationApi";
import { getErrorMessage } from "@/lib/utils/error";

function statusBadgeClass(status: IntegrationConfig["status"]): string {
  switch (status) {
    case "ACTIVE":
      return "bg-emerald-500/10 text-emerald-300 ring-emerald-500/20";
    case "INACTIVE":
      return "bg-slate-700/60 text-slate-300 ring-slate-600/50";
    case "ERROR":
      return "bg-red-500/10 text-red-300 ring-red-500/20";
    default:
      return "bg-slate-700/60 text-slate-300 ring-slate-600/50";
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
        <h1 className="text-2xl font-bold text-white">Government Integrations</h1>
        <p className="mt-1 text-sm text-slate-400">
          External systems wired into IC-PMS (PFMS, GeM, CPGRAMS, NIC SSO, and the like).
        </p>
      </div>

      {isLoading ? (
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 text-center text-slate-400">
          Loading integrations…
        </div>
      ) : error ? (
        <div className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">
          Failed to load integrations: {getErrorMessage(error, "Unknown error")}
          <div className="mt-2 text-xs text-red-300/70">
            This endpoint requires the ADMIN role. If you are logged in as a non-admin user you will see a 401/403.
          </div>
        </div>
      ) : integrations.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-700 p-8 text-center text-slate-400">
          No integrations configured yet.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900/50 shadow-lg">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-700/60 bg-slate-900/80 text-left">
              <tr>
                <th className="px-4 py-3 font-semibold text-slate-400">Code</th>
                <th className="px-4 py-3 font-semibold text-slate-400">Name</th>
                <th className="px-4 py-3 font-semibold text-slate-400">Auth</th>
                <th className="px-4 py-3 font-semibold text-slate-400">Base URL</th>
                <th className="px-4 py-3 font-semibold text-slate-400">Enabled</th>
                <th className="px-4 py-3 font-semibold text-slate-400">Status</th>
                <th className="px-4 py-3 font-semibold text-slate-400">Last Sync</th>
              </tr>
            </thead>
            <tbody>
              {integrations.map((ic) => (
                <tr
                  key={ic.id}
                  className="border-b border-slate-800/60 last:border-b-0 hover:bg-slate-800/30"
                >
                  <td className="px-4 py-3 font-mono text-xs text-slate-300">{ic.systemCode}</td>
                  <td className="px-4 py-3 text-slate-200">{ic.systemName}</td>
                  <td className="px-4 py-3 text-xs text-slate-400">{ic.authType}</td>
                  <td className="px-4 py-3 text-xs text-slate-400 break-all">{ic.baseUrl}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium ring-1 ${
                        ic.isEnabled
                          ? "bg-blue-500/10 text-blue-300 ring-blue-500/20"
                          : "bg-slate-700/50 text-slate-400 ring-slate-600/40"
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
                  <td className="px-4 py-3 text-xs text-slate-400">
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
