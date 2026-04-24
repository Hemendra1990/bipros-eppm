"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { materialCatalogueApi } from "@/lib/api/materialCatalogueApi";
import { organisationApi } from "@/lib/api/organisationApi";
import { PageHeader } from "@/components/common/PageHeader";
import type {
  CreateMaterialRequest,
  MaterialCategory,
  MaterialStatus,
  ResourceUnit,
} from "@/lib/types";

const CATEGORY_OPTIONS: MaterialCategory[] = [
  "BITUMINOUS",
  "AGGREGATE",
  "CEMENT",
  "STEEL",
  "GRANULAR",
  "SAND",
  "PRECAST",
  "ROAD_MARKING",
];

const UNIT_OPTIONS: ResourceUnit[] = ["PER_DAY", "MT", "CU_M", "RMT", "NOS", "KG", "LITRE"];
const STATUS_OPTIONS: MaterialStatus[] = ["ACTIVE", "INACTIVE", "DISCONTINUED"];

export default function NewMaterialPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const queryClient = useQueryClient();

  const [state, setState] = useState<CreateMaterialRequest>({
    name: "",
    category: "CEMENT",
    status: "ACTIVE",
  });
  const [error, setError] = useState<string | null>(null);

  const { data: suppliersData } = useQuery({
    queryKey: ["organisations", "suppliers"],
    queryFn: () => organisationApi.listByType("SUPPLIER"),
  });
  const suppliers = suppliersData?.data ?? [];

  const mutation = useMutation({
    mutationFn: (body: CreateMaterialRequest) => materialCatalogueApi.create(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["materials", projectId] });
      router.push(`/projects/${projectId}/materials`);
    },
    onError: (err: { response?: { data?: { error?: { message?: string } } } }) => {
      setError(err.response?.data?.error?.message ?? "Failed to create material");
    },
  });

  const set = <K extends keyof CreateMaterialRequest>(
    key: K,
    value: CreateMaterialRequest[K],
  ) => setState((s) => ({ ...s, [key]: value }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!state.name.trim()) {
      setError("Name is required");
      return;
    }
    mutation.mutate(state);
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader title="New Material" description="Add a material to the project catalogue." />

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border border-border bg-surface p-6">
        {error && (
          <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Material Code (optional)</label>
            <input
              value={state.code ?? ""}
              onChange={(e) => set("code", e.target.value)}
              placeholder="Auto MAT-NNN"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Material Name *</label>
            <input
              value={state.name}
              onChange={(e) => set("name", e.target.value)}
              placeholder="Bitumen VG-30"
              required
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Category *</label>
            <select
              value={state.category}
              onChange={(e) => set("category", e.target.value as MaterialCategory)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              {CATEGORY_OPTIONS.map((c) => (
                <option key={c} value={c}>
                  {c.replace("_", " ")}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Unit</label>
            <select
              value={state.unit ?? ""}
              onChange={(e) => set("unit", (e.target.value || null) as ResourceUnit | null)}
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
          <div>
            <label className="block text-sm font-medium text-text-secondary">Status</label>
            <select
              value={state.status ?? "ACTIVE"}
              onChange={(e) => set("status", e.target.value as MaterialStatus)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">Specification / Grade</label>
          <input
            value={state.specificationGrade ?? ""}
            onChange={(e) => set("specificationGrade", e.target.value)}
            placeholder="IS 73:2013, OPC 43 Grade, Fe500D"
            className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Min Stock Level</label>
            <input
              type="number"
              step="0.001"
              value={state.minStockLevel ?? ""}
              onChange={(e) =>
                set("minStockLevel", e.target.value ? Number(e.target.value) : null)
              }
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Reorder Quantity</label>
            <input
              type="number"
              step="0.001"
              value={state.reorderQuantity ?? ""}
              onChange={(e) =>
                set("reorderQuantity", e.target.value ? Number(e.target.value) : null)
              }
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Lead Time (days)</label>
            <input
              type="number"
              value={state.leadTimeDays ?? ""}
              onChange={(e) =>
                set("leadTimeDays", e.target.value ? Number(e.target.value) : null)
              }
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Storage Location</label>
            <input
              value={state.storageLocation ?? ""}
              onChange={(e) => set("storageLocation", e.target.value)}
              placeholder="Bitumen Store"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Approved Supplier</label>
            <select
              value={state.approvedSupplierId ?? ""}
              onChange={(e) => set("approvedSupplierId", e.target.value || null)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              <option value="">— None —</option>
              {suppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={() => router.push(`/projects/${projectId}/materials`)}
            className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
          >
            {mutation.isPending ? "Saving…" : "Create Material"}
          </button>
        </div>
      </form>
    </div>
  );
}
