"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { PageHeader } from "@/components/common/PageHeader";
import { resourceApi } from "@/lib/api/resourceApi";
import type { CreateResourceRequest } from "@/lib/api/resourceApi";
import { getErrorMessage } from "@/lib/utils/error";
import { Breadcrumb } from "@/components/common/Breadcrumb";
import { useCurrency } from "@/lib/hooks/useCurrency";
import toast from "react-hot-toast";

export default function NewResourcePage() {
  const router = useRouter();
  const { baseCurrency } = useCurrency();

  const [formData, setFormData] = useState<CreateResourceRequest & { hourlyRate: number; costPerUse: number; overtimeRate: number }>({
    code: "",
    name: "",
    type: "LABOR",
    maxUnits: 1,
    hourlyRate: 0,
    costPerUse: 0,
    overtimeRate: 0,
  });

  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    if (error) setError("");
    if (fieldErrors[name]) setFieldErrors((prev) => { const next = { ...prev }; delete next[name]; return next; });
    const numericFields = ["maxUnits", "hourlyRate", "costPerUse", "overtimeRate"];
    setFormData((prev) => ({
      ...prev,
      [name]: numericFields.includes(name) ? parseFloat(value) || 0 : value,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    const errors: Record<string, string> = {};
    if (!formData.code.trim()) errors.code = "Resource code is required";
    if (!formData.name.trim()) errors.name = "Resource name is required";
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setError("Please fix the highlighted fields");
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await resourceApi.createResource(formData);
      if (result.data) {
        toast.success("Resource created successfully");
        router.push("/resources");
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create resource"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div>
      <div className="mb-4">
        <Breadcrumb items={[
          { label: "Resources", href: "/resources" },
          { label: "New Resource", href: "/resources/new", active: true },
        ]} />
      </div>
      <PageHeader
        title="New Resource"
        description="Create a new labor, nonlabor, or material resource"
      />

      <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
        <form onSubmit={handleSubmit} className="space-y-6">
          {error && (
            <div className="flex items-center justify-between rounded-md bg-red-500/10 p-4 text-sm text-red-400">
              <span>{error}</span>
              <button type="button" onClick={() => setError("")} className="ml-3 text-red-400 hover:text-red-300">&times;</button>
            </div>
          )}

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">Code *</label>
              <input
                type="text"
                name="code"
                value={formData.code}
                onChange={handleChange}
                className={`mt-1 block w-full rounded-md border ${fieldErrors.code ? "border-red-500" : "border-slate-700"} bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500`}
                placeholder="e.g., RES-001"
              />
              {fieldErrors.code && <p className="mt-1 text-xs text-red-400">{fieldErrors.code}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Name *</label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                className={`mt-1 block w-full rounded-md border ${fieldErrors.name ? "border-red-500" : "border-slate-700"} bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500`}
                placeholder="e.g., Senior Developer"
              />
              {fieldErrors.name && <p className="mt-1 text-xs text-red-400">{fieldErrors.name}</p>}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">Resource Type *</label>
              <select
                name="type"
                value={formData.type}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="LABOR">Labor</option>
                <option value="NONLABOR">Nonlabor</option>
                <option value="MATERIAL">Material</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">
                Max Units Per Day
              </label>
              <input
                type="number"
                name="maxUnits"
                value={formData.maxUnits || 1}
                onChange={handleChange}
                min="0"
                step="0.1"
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., 1"
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">Hourly Rate ({baseCurrency.symbol})</label>
              <input
                type="number"
                name="hourlyRate"
                value={formData.hourlyRate || 0}
                onChange={handleChange}
                min="0"
                step="0.01"
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., 75.00"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Cost Per Use ({baseCurrency.symbol})</label>
              <input
                type="number"
                name="costPerUse"
                value={formData.costPerUse || 0}
                onChange={handleChange}
                min="0"
                step="0.01"
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., 50.00"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Overtime Rate ({baseCurrency.symbol})</label>
              <input
                type="number"
                name="overtimeRate"
                value={formData.overtimeRate || 0}
                onChange={handleChange}
                min="0"
                step="0.01"
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., 112.50"
              />
            </div>
          </div>

          <div className="flex gap-3 pt-6">
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-500"
            >
              {isSubmitting ? "Creating..." : "Create Resource"}
            </button>
            <button
              type="button"
              onClick={() => router.back()}
              className="rounded-md border border-slate-700 bg-slate-900/50 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/50"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
