"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, X } from "lucide-react";
import { useState } from "react";
import { projectApi } from "@/lib/api/projectApi";
import { TreeView } from "@/components/common/TreeView";
import { PageHeader } from "@/components/common/PageHeader";
import type { EpsNodeResponse } from "@/lib/types";

export default function EpsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({ code: "", name: "" });

  const { data: epsData, isLoading, error } = useQuery({
    queryKey: ["eps"],
    queryFn: () => projectApi.getEpsTree(),
  });

  const createMutation = useMutation({
    mutationFn: async (data: { code: string; name: string }) => {
      const response = await fetch("/v1/eps", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      if (!response.ok) throw new Error("Failed to create EPS node");
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["eps"] });
      setFormData({ code: "", name: "" });
      setShowForm(false);
    },
  });

  const epsNodes = epsData?.data ?? [];

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (formData.code && formData.name) {
      createMutation.mutate(formData);
    }
  };

  return (
    <div>
      <PageHeader
        title="Enterprise Project Structure"
        description="Manage the EPS hierarchy and related projects"
        actions={
          <button
            onClick={() => setShowForm(!showForm)}
            className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Plus size={16} />
            Add Node
          </button>
        }
      />

      {showForm && (
        <div className="mb-6 rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
          <form onSubmit={handleSubmit} className="flex gap-2">
            <input
              type="text"
              placeholder="Code"
              value={formData.code}
              onChange={(e) => setFormData({ ...formData, code: e.target.value })}
              className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm placeholder-gray-400 focus:border-blue-500 focus:outline-none"
              required
            />
            <input
              type="text"
              placeholder="Name"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm placeholder-gray-400 focus:border-blue-500 focus:outline-none"
              required
            />
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
            >
              {createMutation.isPending ? "Creating..." : "Create"}
            </button>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className="rounded-md border border-gray-300 px-3 py-2 text-sm hover:bg-gray-50"
            >
              <X size={16} />
            </button>
          </form>
        </div>
      )}

      {isLoading && (
        <div className="py-12 text-center text-gray-500">Loading EPS structure...</div>
      )}

      {error && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          Failed to load EPS structure. Is the backend running?
        </div>
      )}

      {!isLoading && epsNodes.length === 0 && (
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <p className="text-gray-500">No EPS nodes yet. Create your first node to get started.</p>
        </div>
      )}

      {epsNodes.length > 0 && (
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <TreeView nodes={epsNodes} />
        </div>
      )}
    </div>
  );
}
