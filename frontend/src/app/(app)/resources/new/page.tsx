"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { PageHeader } from "@/components/common/PageHeader";
import { resourceApi } from "@/lib/api/resourceApi";
import type { CreateResourceRequest } from "@/lib/api/resourceApi";
import { getErrorMessage } from "@/lib/utils/error";

export default function NewResourcePage() {
  const router = useRouter();

  const [formData, setFormData] = useState<CreateResourceRequest>({
    code: "",
    name: "",
    type: "LABOR",
    maxUnits: 1,
  });

  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === "maxUnits" ? parseInt(value, 10) : value,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!formData.code || !formData.name) {
      setError("Code and Name are required");
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await resourceApi.createResource(formData);
      if (result.data) {
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
      <PageHeader
        title="New Resource"
        description="Create a new labor, nonlabor, or material resource"
      />

      <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
        <form onSubmit={handleSubmit} className="space-y-6">
          {error && (
            <div className="rounded-md bg-red-500/10 p-4 text-sm text-red-400">{error}</div>
          )}

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">Code *</label>
              <input
                type="text"
                name="code"
                value={formData.code}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., RES-001"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Name *</label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., Senior Developer"
              />
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
