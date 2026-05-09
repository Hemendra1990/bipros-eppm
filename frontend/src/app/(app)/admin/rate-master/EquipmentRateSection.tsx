"use client";

import { VirtualDataTable, type ColumnDef } from "@/components/common/VirtualDataTable";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  equipmentRateMasterApi,
  type EquipmentRateMaster,
  type EquipmentRateMasterRequest,
} from "@/lib/api/equipmentRateMasterApi";
import { TabTip } from "@/components/common/TabTip";
import { rateUnitOptionsWithFallback } from "@/lib/constants/resourceUnits";
import { getErrorMessage } from "@/lib/utils/error";

interface DefForm {
  equipmentName: string;
  make: string;
  model: string;
  unit: string;
  rate: string;
  active: boolean;
}

const emptyForm = (): DefForm => ({
  equipmentName: "",
  make: "",
  model: "",
  unit: "Hour",
  rate: "",
  active: true,
});

const formFromDef = (d: EquipmentRateMaster): DefForm => ({
  equipmentName: d.equipmentName,
  make: d.make,
  model: d.model,
  unit: d.unit,
  rate: String(d.rate),
  active: d.active,
});

const toPayload = (form: DefForm): EquipmentRateMasterRequest => ({
  equipmentName: form.equipmentName.trim(),
  make: form.make.trim(),
  model: form.model.trim(),
  unit: form.unit.trim(),
  rate: Number(form.rate),
  active: form.active,
});

export function EquipmentRateSection() {
  const queryClient = useQueryClient();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<DefForm>(emptyForm());
  const [error, setError] = useState<string | null>(null);

  const { data, isLoading, isError, error: queryError } = useQuery({
    queryKey: ["equipment-rate-master"],
    queryFn: () => equipmentRateMasterApi.list(),
  });

  const rates: EquipmentRateMaster[] = useMemo(() => data?.data ?? [], [data]);
  const unitOptions = rateUnitOptionsWithFallback(form.unit);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
    setShowForm(true);
  };

  const openEdit = (def: EquipmentRateMaster) => {
    setEditingId(def.id);
    setForm(formFromDef(def));
    setError(null);
    setShowForm(true);
  };

  const closeForm = () => {
    setShowForm(false);
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (editingId) {
        await equipmentRateMasterApi.update(editingId, toPayload(form));
      } else {
        await equipmentRateMasterApi.create(toPayload(form));
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["equipment-rate-master"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to save equipment rate"));
    }
  };

  const handleDelete = async (def: EquipmentRateMaster) => {
    if (!window.confirm(
      `Delete rate for ${def.equipmentName} / ${def.make} ${def.model}? ` +
      `Resources currently linked to this row will keep their cached unit and rate but lose the link.`)) return;
    try {
      await equipmentRateMasterApi.delete(def.id);
      if (editingId === def.id) closeForm();
      queryClient.invalidateQueries({ queryKey: ["equipment-rate-master"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete equipment rate"));
    }
  };

  const columns = useMemo<ColumnDef<EquipmentRateMaster>[]>(() => [
    {
      accessorKey: "equipmentName",
      header: "Equipment",
      cell: ({ row }) => <span>{row.original.equipmentName}</span>,
    },
    {
      accessorKey: "make",
      header: "Make",
      cell: ({ row }) => <span>{row.original.make}</span>,
    },
    {
      accessorKey: "model",
      header: "Model",
      cell: ({ row }) => <span className="font-mono text-sm">{row.original.model}</span>,
    },
    {
      accessorKey: "unit",
      header: "Unit",
      cell: ({ row }) => <span>{row.original.unit}</span>,
    },
    {
      accessorKey: "rate",
      header: "Rate",
      cell: ({ row }) => <span className="text-right block font-mono">{row.original.rate}</span>,
    },
    {
      accessorKey: "active",
      header: "Status",
      cell: ({ row }) =>
        row.original.active ? (
          <span className="text-emerald-700">Active</span>
        ) : (
          <span className="text-text-muted">Inactive</span>
        ),
    },
    {
      id: "actions",
      header: "Actions",
      cell: ({ row }) => (
        <div className="text-sm">
          <button
            onClick={() => openEdit(row.original)}
            className="text-accent hover:underline mr-3"
          >
            Edit
          </button>
          <button
            onClick={() => handleDelete(row.original)}
            className="text-danger hover:underline"
          >
            Delete
          </button>
        </div>
      ),
    },
  ], []);

  return (
    <div>
      <TabTip
        title="Equipment Rate Master"
        description="Rate book for equipment — one row per (Equipment Name + Make + Model). Each row's Unit and Rate auto-fill onto every Equipment Resource that points at it. Revising a rate cascades to all linked resources."
      />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h2 className="text-2xl font-bold text-text-primary">Equipment Rate Master</h2>
        <div className="ml-auto">
          <button
            type="button"
            onClick={openCreate}
            className="px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover"
          >
            + New Rate
          </button>
        </div>
      </div>

      {error && <div className="text-danger mb-4">{error}</div>}

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
        >
          <h3 className="text-lg font-semibold text-text-primary mb-4">
            {editingId ? "Edit Equipment Rate" : "New Equipment Rate"}
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Equipment Name *</label>
              <input
                type="text"
                value={form.equipmentName}
                onChange={(e) => setForm({ ...form, equipmentName: e.target.value })}
                placeholder="e.g. Excavator"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Make *</label>
              <input
                type="text"
                value={form.make}
                onChange={(e) => setForm({ ...form, make: e.target.value })}
                placeholder="e.g. Caterpillar"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Model *</label>
              <input
                type="text"
                value={form.model}
                onChange={(e) => setForm({ ...form, model: e.target.value })}
                placeholder="e.g. B451"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Unit *</label>
              <select
                value={form.unit}
                onChange={(e) => setForm({ ...form, unit: e.target.value })}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              >
                {unitOptions.map((u) => (
                  <option key={u} value={u}>
                    {u}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Rate *</label>
              <input
                type="number"
                step="0.01"
                min="0"
                value={form.rate}
                onChange={(e) => setForm({ ...form, rate: e.target.value })}
                placeholder="e.g. 67"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div className="flex items-end">
              <label className="flex items-center gap-2 text-sm text-text-secondary">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                Active (shown in Resource form rate-master pickers)
              </label>
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <button
              type="submit"
              className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
            >
              {editingId ? "Save Changes" : "Create"}
            </button>
            <button
              type="button"
              onClick={closeForm}
              className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {isError && (
        <div className="text-danger mb-4">
          {getErrorMessage(queryError, "Failed to load equipment rates")}
        </div>
      )}

      <VirtualDataTable
        columns={columns}
        data={rates}
        sortable
        resizable
        searchable
        isLoading={isLoading}
        emptyMessage={isLoading ? "Loading…" : "No equipment rates defined."}
      />
    </div>
  );
}
