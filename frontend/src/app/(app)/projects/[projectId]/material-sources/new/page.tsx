"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { materialSourceApi } from "@/lib/api/materialSourceApi";
import { PageHeader } from "@/components/common/PageHeader";
import type {
  CreateMaterialSourceRequest,
  MaterialSourceType,
  ResourceUnit,
} from "@/lib/types";

const TYPE_OPTIONS: { value: MaterialSourceType; label: string }[] = [
  { value: "BORROW_AREA", label: "Borrow Area (Earth)" },
  { value: "QUARRY", label: "Quarry (Aggregate)" },
  { value: "BITUMEN_DEPOT", label: "Bitumen Depot" },
  { value: "CEMENT_SOURCE", label: "Cement Source" },
];

const UNIT_OPTIONS: ResourceUnit[] = ["PER_DAY", "MT", "CU_M", "RMT", "NOS", "KG", "LITRE"];

export default function NewMaterialSourcePage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const queryClient = useQueryClient();

  const [state, setState] = useState<CreateMaterialSourceRequest>({
    sourceType: "BORROW_AREA",
    approvedQuantityUnit: "CU_M",
  });
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (body: CreateMaterialSourceRequest) =>
      materialSourceApi.create(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["material-sources", projectId] });
      router.push(`/projects/${projectId}/material-sources`);
    },
    onError: (err: { response?: { data?: { error?: { message?: string } } } }) => {
      setError(err.response?.data?.error?.message ?? "Failed to create source");
    },
  });

  const set = <K extends keyof CreateMaterialSourceRequest>(
    key: K,
    value: CreateMaterialSourceRequest[K],
  ) => setState((s) => ({ ...s, [key]: value }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    mutation.mutate(state);
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="New Material Source"
        description="Register an approved borrow area, quarry, depot or cement source."
      />

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border border-border bg-surface p-6">
        {error && (
          <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Source Type *</label>
            <select
              value={state.sourceType}
              onChange={(e) => set("sourceType", e.target.value as MaterialSourceType)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              {TYPE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Source ID (optional)</label>
            <input
              value={state.sourceCode ?? ""}
              onChange={(e) => set("sourceCode", e.target.value)}
              placeholder="Auto BA/QRY/BD/CEM-NNN"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">Name</label>
          <input
            value={state.name ?? ""}
            onChange={(e) => set("name", e.target.value)}
            className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Village / Location</label>
            <input
              value={state.village ?? ""}
              onChange={(e) => set("village", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Taluk</label>
            <input
              value={state.taluk ?? ""}
              onChange={(e) => set("taluk", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">District</label>
            <input
              value={state.district ?? ""}
              onChange={(e) => set("district", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Distance (km)</label>
            <input
              type="number"
              step="0.1"
              value={state.distanceKm ?? ""}
              onChange={(e) =>
                set("distanceKm", e.target.value ? Number(e.target.value) : null)
              }
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Approved Quantity</label>
            <input
              type="number"
              step="0.001"
              value={state.approvedQuantity ?? ""}
              onChange={(e) =>
                set("approvedQuantity", e.target.value ? Number(e.target.value) : null)
              }
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Unit</label>
            <select
              value={state.approvedQuantityUnit ?? ""}
              onChange={(e) =>
                set("approvedQuantityUnit", (e.target.value || null) as ResourceUnit | null)
              }
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              <option value="">—</option>
              {UNIT_OPTIONS.map((u) => (
                <option key={u} value={u}>
                  {u}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Approval Reference</label>
            <input
              value={state.approvalReference ?? ""}
              onChange={(e) => set("approvalReference", e.target.value)}
              placeholder="REV/NHAI/…"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Approval Authority</label>
            <input
              value={state.approvalAuthority ?? ""}
              onChange={(e) => set("approvalAuthority", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">CBR % (Average)</label>
            <input
              type="number"
              step="0.01"
              value={state.cbrAveragePercent ?? ""}
              onChange={(e) =>
                set("cbrAveragePercent", e.target.value ? Number(e.target.value) : null)
              }
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">MDD (g/cc)</label>
            <input
              type="number"
              step="0.001"
              value={state.mddGcc ?? ""}
              onChange={(e) => set("mddGcc", e.target.value ? Number(e.target.value) : null)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={() => router.push(`/projects/${projectId}/material-sources`)}
            className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
          >
            {mutation.isPending ? "Saving…" : "Create Source"}
          </button>
        </div>
      </form>
    </div>
  );
}
