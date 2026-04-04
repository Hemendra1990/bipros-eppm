"use client";

import { useEffect, useState } from "react";
import { obsApi } from "@/lib/api/obsApi";
import type { ObsNodeResponse } from "@/lib/types";
import { TreeView } from "@/components/common/TreeView";
import { Plus, Trash2 } from "lucide-react";

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
    } catch {
      setError("Failed to load OBS hierarchy");
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
        setShowNewForm(false);
        setError("");
        loadObsTree();
      } else if (result.error) {
        setError(result.error.message);
      }
    } catch {
      setError("Failed to create OBS node");
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
    } catch {
      setError("Failed to delete OBS node");
    }
  };

  const handleNodeSelect = (node: ObsNodeResponse) => {
    setSelectedNode(node);
  };

  if (loading) {
    return <div className="text-center text-gray-500">Loading OBS hierarchy...</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-gray-900">OBS Management</h1>
        <button
          onClick={() => setShowNewForm(!showNewForm)}
          className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          <Plus size={18} />
          Add Node
        </button>
      </div>

      {error && (
        <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Tree View */}
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-gray-900">OBS Hierarchy</h2>
          {obsTree.length === 0 ? (
            <p className="text-sm text-gray-500">No OBS nodes yet. Create one to get started.</p>
          ) : (
            <TreeView
              nodes={obsTree}
              onNodeClick={handleNodeSelect}
              renderNode={(node) => (
                <span>
                  <span className="font-medium text-blue-600">{node.code}</span>
                  <span className="ml-2 text-gray-700">{node.name}</span>
                </span>
              )}
            />
          )}
        </div>

        {/* Form and Details */}
        <div className="space-y-4">
          {showNewForm && (
            <form
              onSubmit={handleCreateNode}
              className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
            >
              <h3 className="mb-4 text-lg font-semibold text-gray-900">Create New OBS Node</h3>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700">Code</label>
                  <input
                    type="text"
                    required
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="e.g., OBS-001"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">Name</label>
                  <input
                    type="text"
                    required
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="Node name"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Description
                  </label>
                  <textarea
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="Node description (optional)"
                    rows={2}
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">
                    Parent Node (optional)
                  </label>
                  <input
                    type="text"
                    value={formData.parentId}
                    onChange={(e) => setFormData({ ...formData, parentId: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="Parent node ID"
                  />
                </div>

                <div className="flex gap-2">
                  <button
                    type="submit"
                    disabled={submitting}
                    className="flex-1 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                  >
                    {submitting ? "Creating..." : "Create"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowNewForm(false)}
                    className="flex-1 rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            </form>
          )}

          {selectedNode && (
            <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
              <h3 className="mb-4 text-lg font-semibold text-gray-900">Node Details</h3>
              <div className="space-y-3">
                <div>
                  <p className="text-xs font-medium text-gray-500">CODE</p>
                  <p className="text-sm text-gray-900">{selectedNode.code}</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-gray-500">NAME</p>
                  <p className="text-sm text-gray-900">{selectedNode.name}</p>
                </div>
                {selectedNode.description && (
                  <div>
                    <p className="text-xs font-medium text-gray-500">DESCRIPTION</p>
                    <p className="text-sm text-gray-900">{selectedNode.description}</p>
                  </div>
                )}
                {selectedNode.children && selectedNode.children.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-gray-500">CHILDREN</p>
                    <p className="text-sm text-gray-900">{selectedNode.children.length}</p>
                  </div>
                )}
                <button
                  onClick={() => handleDeleteNode(selectedNode.id)}
                  className="mt-4 flex items-center gap-2 rounded-md bg-red-50 px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-100"
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
