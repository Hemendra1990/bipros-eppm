"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { materialCatalogueApi, materialIssueApi } from "@/lib/api/materialCatalogueApi";
import { stretchApi } from "@/lib/api/stretchApi";
import { PageHeader } from "@/components/common/PageHeader";
import type { CreateMaterialIssueRequest } from "@/lib/types";

export default function NewIssuePage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const queryClient = useQueryClient();

  const [state, setState] = useState<CreateMaterialIssueRequest>({
    materialId: "",
    issueDate: new Date().toISOString().slice(0, 10),
    quantity: 0,
  });
  const [error, setError] = useState<string | null>(null);

  const { data: materialsData } = useQuery({
    queryKey: ["materials", projectId, "ALL"],
    queryFn: () => materialCatalogueApi.listByProject(projectId),
    enabled: !!projectId,
  });
  const { data: stretchesData } = useQuery({
    queryKey: ["stretches", projectId],
    queryFn: () => stretchApi.listByProject(projectId),
    enabled: !!projectId,
  });

  const materials = materialsData?.data ?? [];
  const stretches = stretchesData?.data ?? [];

  const mutation = useMutation({
    mutationFn: (body: CreateMaterialIssueRequest) => materialIssueApi.create(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["issues", projectId] });
      queryClient.invalidateQueries({ queryKey: ["stock-register", projectId] });
      router.push(`/projects/${projectId}/issues`);
    },
    onError: (err: { response?: { data?: { error?: { message?: string } } } }) => {
      setError(err.response?.data?.error?.message ?? "Failed to record issue");
    },
  });

  const set = <K extends keyof CreateMaterialIssueRequest>(
    k: K,
    v: CreateMaterialIssueRequest[K],
  ) => setState((s) => ({ ...s, [k]: v }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!state.materialId) return setError("Material is required");
    if (!state.quantity || state.quantity <= 0) return setError("Quantity must be greater than 0");
    mutation.mutate(state);
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader title="New Material Issue" description="Issue material from the store to a stretch or activity." />

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border border-border bg-surface p-6">
        {error && (
          <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {error}
          </div>
        )}

        <div>
          <label className="block text-sm font-medium text-text-secondary">Material *</label>
          <select
            value={state.materialId}
            onChange={(e) => set("materialId", e.target.value)}
            required
            className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
          >
            <option value="">— Select —</option>
            {materials.map((m) => (
              <option key={m.id} value={m.id}>
                {m.code} — {m.name}
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Issue Date *</label>
            <input
              type="date"
              value={state.issueDate}
              onChange={(e) => set("issueDate", e.target.value)}
              required
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Quantity *</label>
            <input
              type="number"
              step="0.001"
              value={state.quantity || ""}
              onChange={(e) => set("quantity", Number(e.target.value))}
              required
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Wastage</label>
            <input
              type="number"
              step="0.001"
              value={state.wastageQuantity ?? ""}
              onChange={(e) =>
                set("wastageQuantity", e.target.value ? Number(e.target.value) : null)
              }
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">Stretch (optional)</label>
          <select
            value={state.stretchId ?? ""}
            onChange={(e) => set("stretchId", e.target.value || null)}
            className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
          >
            <option value="">—</option>
            {stretches.map((s) => (
              <option key={s.id} value={s.id}>
                {s.stretchCode} — {s.name ?? ""}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">Remarks</label>
          <textarea
            value={state.remarks ?? ""}
            onChange={(e) => set("remarks", e.target.value)}
            rows={2}
            className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
          />
        </div>

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={() => router.push(`/projects/${projectId}/issues`)}
            className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
          >
            {mutation.isPending ? "Saving…" : "Record Issue"}
          </button>
        </div>
      </form>
    </div>
  );
}
