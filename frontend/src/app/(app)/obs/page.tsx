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
    return <div className="text-center text-text-muted">Loading OBS hierarchy...</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-text-primary">OBS Management</h1>
        <button
          onClick={handleAddRoot}
          className="flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
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
        <div className="rounded-md bg-danger/10 p-3 text-sm text-danger">
          {error}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Tree View */}
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h2 className="mb-4 text-lg font-semibold text-text-primary">OBS Hierarchy</h2>
          {obsTree.length === 0 ? (
            <p className="text-sm text-text-muted">No OBS nodes yet. Create one to get started.</p>
          ) : (
            <TreeView
              nodes={obsTree}
              onNodeClick={handleNodeSelect}
              renderNode={(node) => (
                <div className="group flex items-center justify-between gap-3 flex-1">
                  <span>
                    <span className="font-medium text-accent">{node.code}</span>
                    <span className="ml-2 text-text-secondary">{node.name}</span>
                  </span>
                  <button
                    onClick={(e) => { e.stopPropagation(); handleAddChild(node.id, node.code, node.name); }}
                    className="rounded p-1 text-text-muted opacity-0 group-hover:opacity-100 hover:bg-success/10 hover:text-success transition-opacity"
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
              className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg"
            >
              <h3 className="mb-4 text-lg font-semibold text-text-primary">Create New OBS Node</h3>
              {parentLabel && (
                <div className="mb-4 text-sm text-text-secondary">
                  Adding child under: <span className="font-medium text-accent">{parentLabel}</span>
                </div>
              )}
              {!parentLabel && (
                <div className="mb-4 text-sm text-text-secondary">Adding root-level node</div>
              )}
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-text-secondary">Code</label>
                  <input
                    type="text"
                    required
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted shadow-sm focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                    placeholder="e.g., OBS-001"
                    autoFocus
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-text-secondary">Name</label>
                  <input
                    type="text"
                    required
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted shadow-sm focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                    placeholder="Node name"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-text-secondary">
                    Description
                  </label>
                  <textarea
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted shadow-sm focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                    placeholder="Node description (optional)"
                    rows={2}
                  />
                </div>

                <div className="flex gap-2">
                  <button
                    type="submit"
                    disabled={submitting}
                    className="flex-1 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
                  >
                    {submitting ? "Creating..." : "Create"}
                  </button>
                  <button
                    type="button"
                    onClick={() => { setShowNewForm(false); setParentLabel(null); setFormData({ code: "", name: "", description: "", parentId: "" }); }}
                    className="flex-1 rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            </form>
          )}

          {selectedNode && (
            <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold text-text-primary">Node Details</h3>
                <button
                  onClick={() => handleAddChild(selectedNode.id, selectedNode.code, selectedNode.name)}
                  className="flex items-center gap-1 rounded-md bg-success/10 px-3 py-1.5 text-sm font-medium text-success hover:bg-success/80/20"
                >
                  <FolderPlus size={14} />
                  Add Child
                </button>
              </div>
              <div className="space-y-3">
                <div>
                  <p className="text-xs font-medium text-text-muted">CODE</p>
                  <p className="text-sm text-text-primary">{selectedNode.code}</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-text-muted">NAME</p>
                  <p className="text-sm text-text-primary">{selectedNode.name}</p>
                </div>
                {selectedNode.description && (
                  <div>
                    <p className="text-xs font-medium text-text-muted">DESCRIPTION</p>
                    <p className="text-sm text-text-primary">{selectedNode.description}</p>
                  </div>
                )}
                {selectedNode.children && selectedNode.children.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-text-muted">CHILDREN</p>
                    <p className="text-sm text-text-primary">{selectedNode.children.length}</p>
                  </div>
                )}
                <button
                  onClick={() => handleDeleteNode(selectedNode.id)}
                  className="mt-4 flex items-center gap-2 rounded-md bg-danger/10 px-3 py-2 text-sm font-medium text-danger hover:bg-danger/20"
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
