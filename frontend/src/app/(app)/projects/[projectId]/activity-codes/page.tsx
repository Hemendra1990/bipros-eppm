"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { activityCodeApi, type ActivityCodeResponse } from "@/lib/api/activityCodeApi";
import type { PagedResponse } from "@/lib/types";

interface ActivityCodeForm {
  name: string;
  description: string;
  scope: "GLOBAL" | "EPS" | "PROJECT";
  parentId: string;
}

const initialFormState: ActivityCodeForm = {
  name: "",
  description: "",
  scope: "PROJECT",
  parentId: "",
};

export default function ActivityCodesPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const [codes, setCodes] = useState<ActivityCodeResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<ActivityCodeForm>(initialFormState);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const loadActivityCodes = async (pageNum = 0) => {
    try {
      setIsLoading(true);
      const response = await activityCodeApi.listActivityCodes(projectId, pageNum, 20);
      if (response.data) {
        const pagedData = response.data as PagedResponse<ActivityCodeResponse>;
        setCodes(pagedData.content);
        setTotalElements(pagedData.pagination.totalElements);
        setPage(pageNum);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load activity codes");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadActivityCodes();
  }, [projectId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingId) {
        await activityCodeApi.updateActivityCode(projectId, editingId, {
          name: formData.name,
          description: formData.description,
          scope: formData.scope,
          parentId: formData.parentId || undefined,
        });
      } else {
        await activityCodeApi.createActivityCode(projectId, {
          name: formData.name,
          description: formData.description,
          scope: formData.scope,
          parentId: formData.parentId || undefined,
        });
      }
      setFormData(initialFormState);
      setEditingId(null);
      setShowForm(false);
      loadActivityCodes(page);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save activity code");
    }
  };

  const handleEdit = (code: ActivityCodeResponse) => {
    setFormData({
      name: code.name,
      description: code.description || "",
      scope: code.scope,
      parentId: code.parentId || "",
    });
    setEditingId(code.id);
    setShowForm(true);
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Are you sure you want to delete this activity code?")) return;
    try {
      await activityCodeApi.deleteActivityCode(projectId, id);
      loadActivityCodes(page);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete activity code");
    }
  };

  const handleCancel = () => {
    setFormData(initialFormState);
    setEditingId(null);
    setShowForm(false);
  };

  if (isLoading && codes.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center">
        <p className="text-gray-500">Loading activity codes...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold">Activity Codes</h1>
        {!showForm && (
          <button
            onClick={() => setShowForm(true)}
            className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
          >
            New Activity Code
          </button>
        )}
      </div>

      {error && <div className="rounded bg-red-100 p-4 text-red-700">{error}</div>}

      {showForm && (
        <div className="rounded border border-gray-300 bg-gray-50 p-6">
          <h2 className="mb-4 text-xl font-semibold">
            {editingId ? "Edit Activity Code" : "Create Activity Code"}
          </h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium">Name *</label>
              <input
                type="text"
                required
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>

            <div>
              <label className="block text-sm font-medium">Description</label>
              <textarea
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
                rows={3}
              />
            </div>

            <div>
              <label className="block text-sm font-medium">Scope *</label>
              <select
                required
                value={formData.scope}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    scope: e.target.value as "GLOBAL" | "EPS" | "PROJECT",
                  })
                }
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              >
                <option value="PROJECT">Project</option>
                <option value="EPS">EPS</option>
                <option value="GLOBAL">Global</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium">Parent Code ID</label>
              <input
                type="text"
                value={formData.parentId}
                onChange={(e) => setFormData({ ...formData, parentId: e.target.value })}
                placeholder="Leave empty for root"
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>

            <div className="flex gap-2">
              <button
                type="submit"
                className="rounded bg-green-600 px-4 py-2 text-white hover:bg-green-700"
              >
                {editingId ? "Update" : "Create"}
              </button>
              <button
                type="button"
                onClick={handleCancel}
                className="rounded bg-gray-400 px-4 py-2 text-white hover:bg-gray-500"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="rounded border border-gray-300 bg-white">
        <table className="w-full">
          <thead className="bg-gray-100">
            <tr>
              <th className="px-6 py-3 text-left text-sm font-semibold">Name</th>
              <th className="px-6 py-3 text-left text-sm font-semibold">Scope</th>
              <th className="px-6 py-3 text-left text-sm font-semibold">Description</th>
              <th className="px-6 py-3 text-right text-sm font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody>
            {codes.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-6 py-4 text-center text-gray-500">
                  No activity codes found
                </td>
              </tr>
            ) : (
              codes.map((code) => (
                <tr key={code.id} className="border-t border-gray-200 hover:bg-gray-50">
                  <td className="px-6 py-4 font-medium">{code.name}</td>
                  <td className="px-6 py-4">
                    <span className="inline-block rounded bg-blue-100 px-2 py-1 text-xs font-medium text-blue-800">
                      {code.scope}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {code.description || "—"}
                  </td>
                  <td className="space-x-2 px-6 py-4 text-right">
                    <button
                      onClick={() => handleEdit(code)}
                      className="text-blue-600 hover:underline"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => handleDelete(code.id)}
                      className="text-red-600 hover:underline"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {totalElements > 20 && (
        <div className="flex items-center justify-center gap-2">
          <button
            onClick={() => loadActivityCodes(Math.max(0, page - 1))}
            disabled={page === 0}
            className="rounded px-3 py-1 text-sm hover:bg-gray-200 disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm">
            Page {page + 1} of {Math.ceil(totalElements / 20)}
          </span>
          <button
            onClick={() => loadActivityCodes(page + 1)}
            disabled={(page + 1) * 20 >= totalElements}
            className="rounded px-3 py-1 text-sm hover:bg-gray-200 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
