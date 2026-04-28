"use client";

import { useQuery } from "@tanstack/react-query";
import { permitApi } from "@/lib/api/permitApi";
import { PermitTypeBadge } from "@/components/permits";

const STEPS = [
  { no: 1, label: "Application Submitted", role: "Worker / Foreman", color: "#FB923C" },
  { no: 2, label: "Site Engineer Review", role: "Site Engineer", color: "#3B82F6" },
  { no: 3, label: "Safety Officer Clearance", role: "HSE Officer", color: "#0EA5A4" },
  { no: 4, label: "PM Final Approval", role: "Project Manager", color: "#1E3A8A" },
  { no: 5, label: "Permit Issued", role: "System", color: "#10B981" },
  { no: 6, label: "Work in Progress", role: "Site Supervisor", color: "#6B7280" },
  { no: 7, label: "Permit Closed", role: "HSE Officer", color: "#E11D48" },
];

export default function WorkflowReferencePage() {
  const { data: types = [] } = useQuery({
    queryKey: ["permit-types-all"],
    queryFn: async () => {
      const packs = await permitApi.listPacks();
      const all = await Promise.all(packs.map((p) => permitApi.listTypesForPack(p.code)));
      const flat = all.flat();
      const seen = new Set<string>();
      return flat.filter((t) => (seen.has(t.code) ? false : (seen.add(t.code), true)));
    },
    staleTime: 5 * 60_000,
  });

  return (
    <div className="space-y-6 p-6">
      <header>
        <p className="text-xs uppercase tracking-widest text-slate">Process Architecture</p>
        <h1 className="mt-1 text-2xl font-bold text-charcoal">Work Permit Approval Workflow</h1>
        <p className="text-sm text-slate">
          Reference: HSE-PTW-SOP — 7-Step PTW Process &amp; Risk &amp; Approval Matrix
        </p>
      </header>

      <section className="rounded-xl border border-hairline bg-paper p-5 shadow-sm">
        <h2 className="text-sm font-semibold text-charcoal">7-Step Process Flow</h2>
        <ol className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-7">
          {STEPS.map((s) => (
            <li
              key={s.no}
              className="rounded-lg border-2 bg-paper p-4 text-center"
              style={{ borderColor: s.color }}
            >
              <div
                className="mx-auto flex h-8 w-8 items-center justify-center rounded-full font-bold text-white"
                style={{ backgroundColor: s.color }}
              >
                {s.no}
              </div>
              <div className="mt-2 text-sm font-semibold text-charcoal">{s.label}</div>
              <div className="text-[11px] text-slate">{s.role}</div>
            </li>
          ))}
        </ol>
      </section>

      <section className="rounded-xl border border-hairline bg-paper p-5 shadow-sm">
        <h2 className="text-sm font-semibold text-charcoal">Permit Type — Risk &amp; Approval Matrix</h2>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs font-semibold uppercase tracking-wider text-slate">
              <tr>
                <th className="py-2">Permit Type</th>
                <th className="py-2">Default Risk</th>
                <th className="py-2">JSA</th>
                <th className="py-2">Gas Test</th>
                <th className="py-2">Isolation</th>
                <th className="py-2">Night Work</th>
                <th className="py-2">Max Duration</th>
                <th className="py-2">Min. Approval</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-hairline">
              {types.map((t) => (
                <tr key={t.id}>
                  <td className="py-2">
                    <PermitTypeBadge code={t.code} name={t.name} colorHex={t.colorHex} />
                  </td>
                  <td className="py-2 text-charcoal">{t.defaultRiskLevel}</td>
                  <td className="py-2 text-slate">{t.jsaRequired ? "YES" : "—"}</td>
                  <td className="py-2 text-slate">{t.gasTestRequired ? "YES" : "—"}</td>
                  <td className="py-2 text-slate">{t.isolationRequired ? "YES" : "—"}</td>
                  <td className="py-2 text-slate">{t.nightWorkPolicy}</td>
                  <td className="py-2 text-slate">{t.maxDurationHours}h</td>
                  <td className="py-2 text-slate">
                    {(t.minApprovalRole || "").replace("ROLE_", "").replace(/_/g, " ")}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
