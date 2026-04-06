"use client";

import { useEffect, useState } from "react";
import { obsApi } from "@/lib/api/obsApi";
import type { ObsNodeResponse } from "@/lib/types";
import { TreeView } from "@/components/common/TreeView";
import { Plus, Trash2, FolderPlus } from "lucide-react";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

export default function ObsPage() {
  const [obsTree, setObsTree] = useState<ObsNodeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showNewForm, setShowNewForm] = useState(false);
  const [formData, setFormData] = useState({
    code: "",
    name: "",
    description: "",
    parentId: "",
  });
  const [parentLabel, setParentLabel] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [selectedNode, setSelectedNode] = useState<ObsNodeResponse | null>(null);

  useEffect(() => {
    loadObsTree();
  }, []);

  const loadObsTree = async () => {
    try {
      setLoading(true);
      const result = await obsApi.getObsTree();
      setObsTree(result.data ?? []);
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to load OBS hierarchy"));
    } finally {
      setLoading(false);
    }
  };

  const handleCreateNode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.code || !formData.name) {
      setError("Code and name are required");
      return;
    }

    try {
      setSubmitting(true);
      const result = await obsApi.createObsNode({
        code: formData.code,
        name: formData.name,
        description: formData.description || undefined,
        parentId: formData.parentId || undefined,
      });

      if (result.data) {
        setFormData({ code: "", name: "", description: "", parentId: "" });
        setParentLabel(null);
        setShowNewForm(false);
        setError("");
        loadObsTree();
      } else if (result.error) {
        setError(result.error.message);
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create OBS node"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteNode = async (id: string) => {
    if (!confirm("Are you sure you want to delete this OBS node? This may affect associated projects.")) {
      return;
    }

    try {
      await obsApi.deleteObsNode(id);
      loadObsTree();
      setSelectedNode(null);
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete OBS node"));
    }
  };

  const handleAddChild = (parentId: string, parentCode: string, parentName: string) => {
    setFormData({ code: "", name: "", description: "", parentId });
    setParentLabel(`${parentCode} - ${parentName}`);
    setShowNewForm(true);
  };

  const handleAddRoot = () => {
    setFormData({ code: "", name: "", description: "", parentId: "" });
    setParentLabel(null);
    setShowNewForm(!showNewForm);
  };

  const handleNodeSelect = (node: ObsNodeResponse) => {
    setSelectedNode(node);
  };

  if (loading) {
    return <div className="text-center text-slate-500">Loading OBS hierarchy...</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-white">OBS Management</h1>
        <button
          onClick={handleAddRoot}
          className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
        >
          <Plus size={18} />
          Add Root Node
        </button>
      </div>

      <TabTip
        title="Organization Breakdown Structure (OBS)"
        description="Define your organizational hierarchy — departments, teams, and roles. Link OBS nodes to projects for responsibility assignment."
      />

      {error && (
        <div className="rounded-md bg-red-500/10 p-3 text-sm text-red-400">
          {error}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Tree View */}
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h2 className="mb-4 text-lg font-semibold text-white">OBS Hierarchy</h2>
          {obsTree.length === 0 ? (
            <p className="text-sm text-slate-500">No OBS nodes yet. Create one to get started.</p>
          ) : (
            <TreeView
              nodes={obsTree}
              onNodeClick={handleNodeSelect}
              renderNode={(node) => (
                <div className="group flex items-center justify-between gap-3 flex-1">
                  <span>
                    <span className="font-medium text-blue-400">{node.code}</span>
                    <span className="ml-2 text-slate-300">{node.name}</span>
                  </span>
                  <button
                    onClick={(e) => { e.stopPropagation(); handleAddChild(node.id, node.code, node.name); }}
                    className="rounded p-1 text-slate-500 opacity-0 group-hover:opacity-100 hover:bg-emerald-500/10 hover:text-emerald-400 transition-opacity"
                    title="Add child node"
                  >
                    <FolderPlus size={16} />
                  </button>
                </div>
              )}
            />
          )}
        </div>

        {/* Form and Details */}
        <div className="space-y-4">
          {showNewForm && (
            <form
              onSubmit={handleCreateNode}
              className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg"
            >
              <h3 className="mb-4 text-lg font-semibold text-white">Create New OBS Node</h3>
              {parentLabel && (
                <div className="mb-4 text-sm text-slate-400">
                  Adding child under: <span className="font-medium text-blue-400">{parentLabel}</span>
                </div>
              )}
              {!parentLabel && (
                <div className="mb-4 text-sm text-slate-400">Adding root-level node</div>
              )}
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-slate-300">Code</label>
                  <input
                    type="text"
                    required
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="e.g., OBS-001"
                    autoFocus
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-slate-300">Name</label>
                  <input
                    type="text"
                    required
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="Node name"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-slate-300">
                    Description
                  </label>
                  <textarea
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="Node description (optional)"
                    rows={2}
                  />
                </div>

                <div className="flex gap-2">
                  <button
                    type="submit"
                    disabled={submitting}
                    className="flex-1 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-50"
                  >
                    {submitting ? "Creating..." : "Create"}
                  </button>
                  <button
                    type="button"
                    onClick={() => { setShowNewForm(false); setParentLabel(null); setFormData({ code: "", name: "", description: "", parentId: "" }); }}
                    className="flex-1 rounded-md border border-slate-700 bg-slate-900/50 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/50"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            </form>
          )}

          {selectedNode && (
            <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold text-white">Node Details</h3>
                <button
                  onClick={() => handleAddChild(selectedNode.id, selectedNode.code, selectedNode.name)}
                  className="flex items-center gap-1 rounded-md bg-emerald-500/10 px-3 py-1.5 text-sm font-medium text-emerald-400 hover:bg-emerald-500/20"
                >
                  <FolderPlus size={14} />
                  Add Child
                </button>
              </div>
              <div className="space-y-3">
                <div>
                  <p className="text-xs font-medium text-slate-500">CODE</p>
                  <p className="text-sm text-white">{selectedNode.code}</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-slate-500">NAME</p>
                  <p className="text-sm text-white">{selectedNode.name}</p>
                </div>
                {selectedNode.description && (
                  <div>
                    <p className="text-xs font-medium text-slate-500">DESCRIPTION</p>
                    <p className="text-sm text-white">{selectedNode.description}</p>
                  </div>
                )}
                {selectedNode.children && selectedNode.children.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-slate-500">CHILDREN</p>
                    <p className="text-sm text-white">{selectedNode.children.length}</p>
                  </div>
                )}
                <button
                  onClick={() => handleDeleteNode(selectedNode.id)}
                  className="mt-4 flex items-center gap-2 rounded-md bg-red-500/10 px-3 py-2 text-sm font-medium text-red-400 hover:bg-red-500/20"
                >
                  <Trash2 size={16} />
                  Delete Node
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
