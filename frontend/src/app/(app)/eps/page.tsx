"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, X, Trash2, Edit2, Check, FolderPlus } from "lucide-react";
import { useState } from "react";
import { projectApi } from "@/lib/api/projectApi";
import { TreeView } from "@/components/common/TreeView";
import { PageHeader } from "@/components/common/PageHeader";
import { TabTip } from "@/components/common/TabTip";
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
  const [parentLabel, setParentLabel] = useState<string | null>(null);

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
      setParentLabel(null);
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

  const handleAddChild = (parentId: string, parentCode: string, parentName: string) => {
    setFormData({ code: "", name: "", parentId });
    setParentLabel(`${parentCode} - ${parentName}`);
    setShowForm(true);
  };

  const handleAddRoot = () => {
    setFormData({ code: "", name: "", parentId: undefined });
    setParentLabel(null);
    setShowForm(!showForm);
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
            onClick={handleAddRoot}
            className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
          >
            <Plus size={16} />
            Add Root Node
          </button>
        }
      />

      <TabTip
        title="Enterprise Project Structure (EPS)"
        description="The EPS is the top-level organizational hierarchy for all projects. Think of it as departments or divisions. Each project must belong to an EPS node."
        steps={["Create root nodes for your organization structure", "Hover over a node and click the folder icon to add child nodes", "Projects are assigned to EPS nodes when created"]}
      />

      {showForm && (
        <div className="mb-6 rounded-xl border border-slate-800 bg-slate-900/50 p-4 shadow-lg">
          <form onSubmit={handleSubmit} className="space-y-3">
            {parentLabel && (
              <div className="text-sm text-slate-400">
                Adding child under: <span className="font-medium text-blue-400">{parentLabel}</span>
              </div>
            )}
            {!parentLabel && (
              <div className="text-sm text-slate-400">Adding root-level node</div>
            )}
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Code"
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                className="flex-1 rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm placeholder-slate-500 text-white focus:border-blue-500 focus:outline-none"
                required
                autoFocus
              />
              <input
                type="text"
                placeholder="Name"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="flex-1 rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm placeholder-slate-500 text-white focus:border-blue-500 focus:outline-none"
                required
              />
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-500"
              >
                {createMutation.isPending ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => { setShowForm(false); setParentLabel(null); setFormData({ code: "", name: "", parentId: undefined }); }}
                className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-800/50"
              >
                <X size={16} />
              </button>
            </div>
          </form>
        </div>
      )}

      {isLoading && (
        <div className="py-12 text-center text-slate-500">Loading EPS structure...</div>
      )}

      {error && (
        <div className="rounded-md bg-red-500/10 p-4 text-sm text-red-400">
          Failed to load EPS structure. Is the backend running?
        </div>
      )}

      {!isLoading && epsNodes.length === 0 && (
        <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
          <p className="text-slate-500">No EPS nodes yet. Create your first node to get started.</p>
        </div>
      )}

      {epsNodes.length > 0 && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
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
                        className="flex-1 rounded-md border border-slate-700 bg-slate-800 px-2 py-1 text-sm text-white focus:border-blue-500 focus:outline-none"
                        autoFocus
                        onKeyDown={(e) => { if (e.key === 'Enter') handleSaveEdit(node.id); if (e.key === 'Escape') setEditingNodeId(null); }}
                      />
                      <button
                        onClick={(e) => { e.stopPropagation(); handleSaveEdit(node.id); }}
                        disabled={updateMutation.isPending}
                        className="rounded p-1 text-emerald-400 hover:bg-emerald-500/10 disabled:text-slate-500"
                      >
                        <Check size={16} />
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); setEditingNodeId(null); }}
                        className="rounded p-1 text-slate-500 hover:bg-slate-700/50"
                      >
                        <X size={16} />
                      </button>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-blue-400">{node.code}</span>
                      <span className="text-slate-300">{node.name}</span>
                    </div>
                  )}
                </div>
                {editingNodeId !== node.id && (
                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={(e) => { e.stopPropagation(); handleAddChild(node.id, node.code, node.name); }}
                      className="rounded p-1 text-slate-500 hover:bg-emerald-500/10 hover:text-emerald-400"
                      title="Add child node"
                    >
                      <FolderPlus size={16} />
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleStartEdit(node.id, node.name); }}
                      className="rounded p-1 text-slate-500 hover:bg-blue-500/10 hover:text-blue-400"
                      title="Edit"
                    >
                      <Edit2 size={16} />
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDelete(node.id); }}
                      disabled={deleteMutation.isPending}
                      className="rounded p-1 text-slate-500 hover:bg-red-500/10 hover:text-red-400 disabled:text-slate-600"
                      title="Delete"
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
