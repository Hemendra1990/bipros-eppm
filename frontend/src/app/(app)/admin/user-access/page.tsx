"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { userApi } from "@/lib/api/userApi";
import { organisationApi } from "@/lib/api/organisationApi";
import { PageHeader } from "@/components/common/PageHeader";
import type {
  IcpmsModule,
  ModuleAccessLevel,
  OrganisationResponse,
  UserResponse,
} from "@/lib/types";

const MODULES: IcpmsModule[] = [
  "M1_WBS_GIS",
  "M2_SCHEDULE_EVM",
  "M3_SATELLITE_MONITORING",
  "M4_COST_RA_BILLS",
  "M5_CONTRACTS",
  "M6_DOCUMENTS",
  "M7_RISKS",
  "M8_RESOURCES",
  "M9_REPORTS",
];

const MODULE_LABEL: Record<IcpmsModule, string> = {
  M1_WBS_GIS: "M1",
  M2_SCHEDULE_EVM: "M2",
  M3_SATELLITE_MONITORING: "M3",
  M4_COST_RA_BILLS: "M4",
  M5_CONTRACTS: "M5",
  M6_DOCUMENTS: "M6",
  M7_RISKS: "M7",
  M8_RESOURCES: "M8",
  M9_REPORTS: "M9",
};

const LEVEL_STYLE: Record<ModuleAccessLevel, string> = {
  NONE: "bg-surface-hover text-text-muted",
  VIEW: "bg-border/40 text-text-primary",
  EDIT: "bg-accent/40 text-blue-200",
  CERTIFY: "bg-success/40 text-emerald-200",
  APPROVE: "bg-warning/40 text-amber-200",
  FULL: "bg-purple-500/40 text-purple-200",
};

const LEVEL_SHORT: Record<ModuleAccessLevel, string> = {
  NONE: "-",
  VIEW: "V",
  EDIT: "E",
  CERTIFY: "C",
  APPROVE: "A",
  FULL: "F",
};

export default function UserAccessPage() {
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);

  const { data: usersData, isLoading: usersLoading } = useQuery({
    queryKey: ["users"],
    queryFn: () => userApi.listUsers(0, 100),
  });

  const { data: orgsData } = useQuery({
    queryKey: ["organisations"],
    queryFn: () => organisationApi.listAll(),
  });

  const orgsById = useMemo(() => {
    const map = new Map<string, OrganisationResponse>();
    (orgsData?.data ?? []).forEach((o) => map.set(o.id, o));
    return map;
  }, [orgsData]);

  const users: UserResponse[] = usersData?.data?.content ?? [];

  const { data: accessData, isLoading: accessLoading } = useQuery({
    queryKey: ["user-access", selectedUserId],
    queryFn: () => userApi.getAccess(selectedUserId!),
    enabled: !!selectedUserId,
  });
  const access = accessData?.data;

  return (
    <div>
      <PageHeader
        title="IC-PMS User Access"
        description="Module access matrix (M1–M9) and corridor scope per user. 'All Corridors' = universal scope."
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
        <div className="overflow-hidden rounded-lg border border-border">
          <table className="w-full text-xs">
            <thead className="bg-surface-hover/60 text-text-secondary">
              <tr>
                <th className="px-3 py-2 text-left">User</th>
                <th className="px-3 py-2 text-left">Organisation</th>
                <th className="px-3 py-2 text-left">Designation</th>
                <th className="px-3 py-2 text-left">IC-PMS Role</th>
                <th className="px-3 py-2 text-left">Auth</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {usersLoading && (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-text-muted">
                    Loading users…
                  </td>
                </tr>
              )}
              {!usersLoading && users.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-text-muted">
                    No users seeded.
                  </td>
                </tr>
              )}
              {users.map((u) => {
                const org = u.organisationId ? orgsById.get(u.organisationId) : null;
                const isSelected = selectedUserId === u.id;
                return (
                  <tr
                    key={u.id}
                    onClick={() => setSelectedUserId(u.id)}
                    className={`cursor-pointer hover:bg-surface-hover/60 ${isSelected ? "bg-surface-hover/80" : ""}`}
                  >
                    <td className="px-3 py-2">
                      <div className="font-medium text-text-primary">{u.username}</div>
                      <div className="text-text-muted">{u.email}</div>
                    </td>
                    <td className="px-3 py-2 text-text-secondary">
                      {org ? org.shortName ?? org.code : "—"}
                    </td>
                    <td className="px-3 py-2 text-text-secondary">{u.designation ?? "—"}</td>
                    <td className="px-3 py-2 text-text-secondary">{u.primaryIcpmsRole ?? "—"}</td>
                    <td className="px-3 py-2">
                      <div className="flex flex-wrap gap-1">
                        {(u.authMethods ?? []).map((a) => (
                          <span
                            key={a}
                            className="rounded bg-surface-active/60 px-1.5 py-0.5 text-[10px] text-text-secondary"
                          >
                            {a.replace(/_/g, " ")}
                          </span>
                        ))}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <aside className="rounded-lg border border-border bg-surface/40 p-4">
          <h3 className="mb-3 text-sm font-semibold text-text-primary">
            {selectedUserId ? "Access Matrix" : "Select a user to see access matrix"}
          </h3>

          {selectedUserId && accessLoading && (
            <div className="text-sm text-text-muted">Loading access…</div>
          )}

          {access && (
            <>
              <div className="mb-4">
                <div className="mb-2 text-xs uppercase tracking-wide text-text-muted">
                  Modules (M1–M9)
                </div>
                <div className="grid grid-cols-3 gap-1.5">
                  {MODULES.map((m) => {
                    const lvl = (access.moduleAccess[m] ?? "NONE") as ModuleAccessLevel;
                    return (
                      <div
                        key={m}
                        className={`rounded px-2 py-1.5 text-center text-[11px] ${LEVEL_STYLE[lvl]}`}
                        title={`${m} → ${lvl}`}
                      >
                        <div className="font-semibold">{MODULE_LABEL[m]}</div>
                        <div>{LEVEL_SHORT[lvl]}</div>
                      </div>
                    );
                  })}
                </div>
                <div className="mt-2 text-[10px] text-text-muted">
                  V=View · E=Edit · C=Certify · A=Approve · F=Full
                </div>
              </div>

              <div>
                <div className="mb-2 text-xs uppercase tracking-wide text-text-muted">
                  Corridor Scope
                </div>
                {access.allCorridors ? (
                  <span className="rounded bg-success/30 px-2 py-1 text-xs text-emerald-200">
                    All Corridors
                  </span>
                ) : access.corridorScopes.length === 0 ? (
                  <span className="text-xs text-text-muted">No scope assigned</span>
                ) : (
                  <div className="flex flex-wrap gap-1">
                    {access.corridorScopes.map((id) => (
                      <span
                        key={id}
                        className="rounded bg-accent/30 px-2 py-1 text-[10px] text-blue-200"
                      >
                        {id.slice(0, 8)}…
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}
