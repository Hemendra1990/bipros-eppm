"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { materialApi, type MaterialReconciliationResponse, type CreateMaterialReconciliationRequest } from "@/lib/api/materialApi";
import { resourceApi, type ResourceResponse } from "@/lib/api/resourceApi";
import { TabTip } from "@/components/common/TabTip";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";
import type { PagedResponse } from "@/lib/types";

interface MaterialReconciliationForm {
  resourceId: string;
  period: string;
  openingBalance: number;
  received: number;
  consumed: number;
  wastage: number;
  unit: string;
  remarks: string;
}

const initialFormState: MaterialReconciliationForm = {
  resourceId: "",
  period: new Date().toISOString().slice(0, 7),
  openingBalance: 0,
  received: 0,
  consumed: 0,
  wastage: 0,
  unit: "MT",
  remarks: "",
};

export default function MaterialReconciliationPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const [reconciliations, setReconciliations] = useState<MaterialReconciliationResponse[]>([]);
  const [resources, setResources] = useState<ResourceResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<MaterialReconciliationForm>(initialFormState);
  const [filterPeriod, setFilterPeriod] = useState<string>("");

  const loadReconciliations = async (period?: string) => {
    try {
      setIsLoading(true);
      const response = await materialApi.getReconciliations(projectId, period);
      if (response.data && Array.isArray(response.data)) {
        setReconciliations(response.data);
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to load material reconciliations"));
    } finally {
      setIsLoading(false);
    }
  };

  const loadResources = async () => {
    try {
      const response = await resourceApi.listResources(0, 100);
      // Backend returns a flat List<ResourceResponse>; be defensive in case
      // it ever switches back to a paged envelope.
      const data = response.data as unknown;
      if (Array.isArray(data)) {
        setResources(data as ResourceResponse[]);
      } else if (data && typeof data === "object" && "content" in data) {
        setResources((data as PagedResponse<ResourceResponse>).content);
      }
    } catch (err: unknown) {
      console.error(getErrorMessage(err, "Failed to load resources"));
    }
  };

  useEffect(() => {
    loadReconciliations();
    loadResources();
  }, [projectId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const request: CreateMaterialReconciliationRequest = {
        resourceId: formData.resourceId,
        projectId,
        period: formData.period,
        openingBalance: formData.openingBalance,
        received: formData.received,
        consumed: formData.consumed,
        wastage: formData.wastage || undefined,
        unit: formData.unit || undefined,
        remarks: formData.remarks || undefined,
      };

      await materialApi.createReconciliation(projectId, request);
      setFormData(initialFormState);
      setShowForm(false);
      loadReconciliations();
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create material reconciliation"));
    }
  };

  const getResourceName = (resourceId: string) => {
    const resource = resources.find((r) => r.id === resourceId);
    return resource ? resource.name : resourceId.substring(0, 8);
  };

  if (isLoading && reconciliations.length === 0) {
    return <div className="p-6 text-text-muted">Loading material reconciliations...</div>;
  }

  const columns: ColumnDef<MaterialReconciliationResponse>[] = [
    { accessorKey: "period", header: "Period" },
    {
      accessorKey: "resourceId",
      header: "Material",
      cell: ({ row }) => getResourceName(row.original.resourceId),
    },
    {
      accessorKey: "openingBalance",
      header: "Opening",
      cell: ({ row }) => row.original.openingBalance.toFixed(2),
    },
    {
      accessorKey: "received",
      header: "Received",
      cell: ({ row }) => (
        <span className="text-success font-semibold">{row.original.received.toFixed(2)}</span>
      ),
    },
    {
      accessorKey: "consumed",
      header: "Consumed",
      cell: ({ row }) => (
        <span className="text-danger font-semibold">{row.original.consumed.toFixed(2)}</span>
      ),
    },
    {
      accessorKey: "wastage",
      header: "Wastage",
      cell: ({ row }) => (
        <span className="text-orange-600">{row.original.wastage.toFixed(2)}</span>
      ),
    },
    {
      accessorKey: "closingBalance",
      header: "Closing",
      cell: ({ row }) => (
        <span className="font-bold">{row.original.closingBalance.toFixed(2)}</span>
      ),
    },
    { accessorKey: "unit", header: "Unit" },
  ];

  return (
    <div className="p-6">
      <TabTip
        title="Material Reconciliation"
        description="Compare materials issued vs consumed vs on-site balance. Helps identify wastage and ensure materials are properly accounted for."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Material Reconciliation</h1>

        {/* Summary Stats */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <div className="bg-accent/10 p-4 rounded-lg border border-accent/20">
            <p className="text-sm text-text-secondary mb-1">Total Reconciliations</p>
            <p className="text-2xl font-bold text-accent">{reconciliations.length}</p>
          </div>
          <div className="bg-success/10 p-4 rounded-lg border border-success/20">
            <p className="text-sm text-text-secondary mb-1">Total Consumed</p>
            <p className="text-2xl font-bold text-success">
              {reconciliations.reduce((sum, r) => sum + r.consumed, 0).toFixed(2)}
            </p>
          </div>
          <div className="bg-danger/10 p-4 rounded-lg border border-danger/20">
            <p className="text-sm text-text-secondary mb-1">Total Wastage</p>
            <p className="text-2xl font-bold text-danger">
              {reconciliations.reduce((sum, r) => sum + r.wastage, 0).toFixed(2)}
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowForm(!showForm)}
          className="mb-6 px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover"
        >
          {showForm ? "Cancel" : "Add Reconciliation"}
        </button>

        {error && <div className="text-danger mb-4">{error}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} className="bg-surface/50 p-4 rounded-lg border border-border mb-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Material/Resource</label>
                <SearchableSelect
                  value={formData.resourceId}
                  onChange={(val) => setFormData({ ...formData, resourceId: val })}
                  placeholder="Search materials..."
                  options={resources.map((r) => ({
                    value: r.id,
                    label: `${r.code} - ${r.name}`,
                  }))}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Period (YYYY-MM)</label>
                <input
                  type="month"
                  value={formData.period}
                  onChange={(e) => setFormData({ ...formData, period: e.target.value })}
                  className="w-full px-3 py-2 border border-border rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Opening Balance</label>
                <input
                  type="number"
                  step="0.01"
                  value={formData.openingBalance}
                  onChange={(e) => setFormData({ ...formData, openingBalance: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-border rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Received</label>
                <input
                  type="number"
                  step="0.01"
                  value={formData.received}
                  onChange={(e) => setFormData({ ...formData, received: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-border rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Consumed</label>
                <input
                  type="number"
                  step="0.01"
                  value={formData.consumed}
                  onChange={(e) => setFormData({ ...formData, consumed: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-border rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Wastage</label>
                <input
                  type="number"
                  step="0.01"
                  value={formData.wastage}
                  onChange={(e) => setFormData({ ...formData, wastage: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-border rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Unit</label>
                <input
                  type="text"
                  value={formData.unit}
                  onChange={(e) => setFormData({ ...formData, unit: e.target.value })}
                  placeholder="e.g., MT, m3, nos"
                  className="w-full px-3 py-2 border border-border rounded-lg"
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1">Remarks</label>
                <textarea
                  value={formData.remarks}
                  onChange={(e) => setFormData({ ...formData, remarks: e.target.value })}
                  className="w-full px-3 py-2 border border-border rounded-lg"
                  rows={3}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button type="submit" className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600">
                Save Reconciliation
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="px-4 py-2 bg-border text-text-primary rounded-lg hover:bg-surface-active"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        <VirtualDataTable
          columns={columns}
          data={reconciliations}
          sortable
          resizable
          isLoading={isLoading}
          emptyMessage="No material reconciliations found."
        />
      </div>
    </div>
  );
}
