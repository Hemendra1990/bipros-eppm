"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, X, Trash2, Edit2, Check } from "lucide-react";
import { useState } from "react";
import { projectApi } from "@/lib/api/projectApi";
import { TreeView } from "@/components/common/TreeView";
import { PageHeader } from "@/components/common/PageHeader";
import { apiClient } from "@/lib/api/client";
import type { EpsNodeResponse, ApiResponse } from "@/lib/types";

interface EpsNodeCreateRequest {
  code: string;
  name: string;
  parentId?: string;
}

export default function EpsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingNodeId, setEditingNodeId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState("");
  const [formData, setFormData] = useState<EpsNodeCreateRequest>({ code: "", name: "", parentId: undefined });

  const { data: epsData, isLoading, error } = useQuery({
    queryKey: ["eps"],
    queryFn: () => projectApi.getEpsTree(),
  });

  const createMutation = useMutation({
    mutationFn: async (data: EpsNodeCreateRequest) => {
      const response = await apiClient.post<ApiResponse<EpsNodeResponse>>("/v1/eps", data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["eps"] });
      setFormData({ code: "", name: "", parentId: undefined });
      setShowForm(false);
    },
  });

  const updateMutation = useMutation({
    mutationFn: async ({ nodeId, name }: { nodeId: string; name: string }) => {
      const response = await apiClient.put<ApiResponse<EpsNodeResponse>>(`/v1/eps/${nodeId}`, { name });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["eps"] });
      setEditingNodeId(null);
      setEditingName("");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (nodeId: string) => {
      return apiClient.delete(`/v1/eps/${nodeId}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["eps"] });
    },
  });

  const epsNodes = epsData?.data ?? [];

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (formData.code && formData.name) {
      createMutation.mutate(formData);
    }
  };

  const handleStartEdit = (nodeId: string, currentName: string) => {
    setEditingNodeId(nodeId);
    setEditingName(currentName);
  };

  const handleSaveEdit = (nodeId: string) => {
    if (editingName.trim()) {
      updateMutation.mutate({ nodeId, name: editingName });
    }
  };

  const handleDelete = (nodeId: string) => {
    if (window.confirm("Are you sure you want to delete this EPS node?")) {
      deleteMutation.mutate(nodeId);
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
          <form onSubmit={handleSubmit} className="space-y-3">
            <div className="flex gap-2">
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
            </div>
            <div className="flex gap-2">
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
            </div>
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
          <TreeView
            nodes={epsNodes}
            renderNode={(node: EpsNodeResponse) => (
              <div className="group flex items-center justify-between gap-3 flex-1">
                <div className="flex-1">
                  {editingNodeId === node.id ? (
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        className="flex-1 rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-blue-500 focus:outline-none"
                        autoFocus
                      />
                      <button
                        onClick={() => handleSaveEdit(node.id)}
                        disabled={updateMutation.isPending}
                        className="rounded p-1 text-green-600 hover:bg-green-50 disabled:text-gray-400"
                      >
                        <Check size={16} />
                      </button>
                      <button
                        onClick={() => setEditingNodeId(null)}
                        className="rounded p-1 text-gray-400 hover:bg-gray-100"
                      >
                        <X size={16} />
                      </button>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-blue-600">{node.code}</span>
                      <span className="text-gray-700">{node.name}</span>
                    </div>
                  )}
                </div>
                {editingNodeId !== node.id && (
                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={() => handleStartEdit(node.id, node.name)}
                      className="rounded p-1 text-gray-400 hover:bg-blue-50 hover:text-blue-600"
                    >
                      <Edit2 size={16} />
                    </button>
                    <button
                      onClick={() => handleDelete(node.id)}
                      disabled={deleteMutation.isPending}
                      className="rounded p-1 text-gray-400 hover:bg-red-50 hover:text-red-600 disabled:text-gray-300"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                )}
              </div>
            )}
          />
        </div>
      )}
    </div>
  );
}
