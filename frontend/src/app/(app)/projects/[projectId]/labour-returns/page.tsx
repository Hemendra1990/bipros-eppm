"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { labourApi, type LabourReturnResponse, type CreateLabourReturnRequest, type DeploymentSummary } from "@/lib/api/labourApi";
import type { PagedResponse } from "@/lib/types";

interface LabourReturnForm {
  contractorName: string;
  returnDate: string;
  skillCategory: "SKILLED" | "SEMI_SKILLED" | "UNSKILLED" | "SUPERVISOR" | "ENGINEER";
  headCount: number;
  manDays: number;
  siteLocation: string;
  remarks: string;
}

const initialFormState: LabourReturnForm = {
  contractorName: "",
  returnDate: new Date().toISOString().split("T")[0],
  skillCategory: "UNSKILLED",
  headCount: 1,
  manDays: 1,
  siteLocation: "",
  remarks: "",
};

const skillCategoryLabel = {
  SKILLED: "Skilled",
  SEMI_SKILLED: "Semi-Skilled",
  UNSKILLED: "Unskilled",
  SUPERVISOR: "Supervisor",
  ENGINEER: "Engineer",
};

export default function LabourReturnsPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const [returns, setReturns] = useState<LabourReturnResponse[]>([]);
  const [summary, setSummary] = useState<DeploymentSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<LabourReturnForm>(initialFormState);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const loadLabourReturns = async (pageNum = 0) => {
    try {
      setIsLoading(true);
      const response = await labourApi.getReturnsByProject(projectId, pageNum, 20);
      if (response.data) {
        const pagedData = response.data as PagedResponse<LabourReturnResponse>;
        setReturns(pagedData.content);
        setTotalElements(pagedData.pagination.totalElements);
        setPage(pageNum);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load labour returns");
    } finally {
      setIsLoading(false);
    }
  };

  const loadDeploymentSummary = async () => {
    try {
      const response = await labourApi.getDeploymentSummary(projectId);
      if (response.data) {
        setSummary(response.data);
      }
    } catch (err) {
      console.error("Failed to load deployment summary:", err);
    }
  };

  useEffect(() => {
    loadLabourReturns();
    loadDeploymentSummary();
  }, [projectId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const request: CreateLabourReturnRequest = {
        projectId,
        contractorName: formData.contractorName,
        returnDate: formData.returnDate,
        skillCategory: formData.skillCategory,
        headCount: formData.headCount,
        manDays: formData.manDays,
        siteLocation: formData.siteLocation || undefined,
        remarks: formData.remarks || undefined,
      };

      await labourApi.createReturn(projectId, request);
      setFormData(initialFormState);
      setShowForm(false);
      loadLabourReturns();
      loadDeploymentSummary();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create labour return");
    }
  };

  if (isLoading && returns.length === 0) {
    return <div className="p-6">Loading labour returns...</div>;
  }

  return (
    <div className="p-6">
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4">Labour Returns</h1>

        {/* Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <div className="bg-blue-50 p-4 rounded-lg border border-blue-200">
            <p className="text-sm text-gray-600 mb-1">Total Headcount</p>
            <p className="text-2xl font-bold text-blue-600">
              {summary.reduce((sum, s) => sum + s.totalHeadCount, 0)}
            </p>
          </div>
          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <p className="text-sm text-gray-600 mb-1">Total Man-Days</p>
            <p className="text-2xl font-bold text-green-600">
              {summary.reduce((sum, s) => sum + s.totalManDays, 0).toFixed(1)}
            </p>
          </div>
          <div className="bg-purple-50 p-4 rounded-lg border border-purple-200">
            <p className="text-sm text-gray-600 mb-1">Returns Submitted</p>
            <p className="text-2xl font-bold text-purple-600">{totalElements}</p>
          </div>
        </div>

        {/* Skill Category Breakdown */}
        {summary.length > 0 && (
          <div className="bg-white p-4 rounded-lg border border-gray-300 mb-6">
            <h3 className="font-bold text-lg mb-3">Deployment by Skill Category</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-3">
              {summary.map((s) => (
                <div key={s.skillCategory} className="bg-gray-50 p-3 rounded border border-gray-200">
                  <p className="text-sm text-gray-600">{skillCategoryLabel[s.skillCategory]}</p>
                  <p className="text-xl font-bold text-gray-800">{s.totalHeadCount}</p>
                  <p className="text-xs text-gray-500">{s.totalManDays.toFixed(1)} man-days</p>
                </div>
              ))}
            </div>
          </div>
        )}

        <button
          onClick={() => setShowForm(!showForm)}
          className="mb-6 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          {showForm ? "Cancel" : "Add Labour Return"}
        </button>

        {error && <div className="text-red-600 mb-4">{error}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} className="bg-white p-4 rounded-lg border border-gray-300 mb-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Contractor Name</label>
                <input
                  type="text"
                  value={formData.contractorName}
                  onChange={(e) => setFormData({ ...formData, contractorName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Return Date</label>
                <input
                  type="date"
                  value={formData.returnDate}
                  onChange={(e) => setFormData({ ...formData, returnDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Skill Category</label>
                <select
                  value={formData.skillCategory}
                  onChange={(e) => setFormData({ ...formData, skillCategory: e.target.value as any })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  required
                >
                  <option value="UNSKILLED">Unskilled</option>
                  <option value="SEMI_SKILLED">Semi-Skilled</option>
                  <option value="SKILLED">Skilled</option>
                  <option value="SUPERVISOR">Supervisor</option>
                  <option value="ENGINEER">Engineer</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Headcount</label>
                <input
                  type="number"
                  min="1"
                  value={formData.headCount}
                  onChange={(e) => setFormData({ ...formData, headCount: parseInt(e.target.value) || 1 })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Man-Days</label>
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  value={formData.manDays}
                  onChange={(e) => setFormData({ ...formData, manDays: parseFloat(e.target.value) || 1 })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Site Location</label>
                <input
                  type="text"
                  value={formData.siteLocation}
                  onChange={(e) => setFormData({ ...formData, siteLocation: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1">Remarks</label>
                <textarea
                  value={formData.remarks}
                  onChange={(e) => setFormData({ ...formData, remarks: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  rows={3}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button type="submit" className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700">
                Save Return
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="px-4 py-2 bg-gray-400 text-white rounded-lg hover:bg-gray-500"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {/* Returns Table */}
        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-gray-300">
            <thead>
              <tr className="bg-gray-100">
                <th className="border border-gray-300 px-4 py-2 text-left">Date</th>
                <th className="border border-gray-300 px-4 py-2 text-left">Contractor</th>
                <th className="border border-gray-300 px-4 py-2 text-left">Skill Category</th>
                <th className="border border-gray-300 px-4 py-2 text-right">Headcount</th>
                <th className="border border-gray-300 px-4 py-2 text-right">Man-Days</th>
                <th className="border border-gray-300 px-4 py-2 text-left">Site Location</th>
              </tr>
            </thead>
            <tbody>
              {returns.map((ret) => (
                <tr key={ret.id} className="hover:bg-gray-50">
                  <td className="border border-gray-300 px-4 py-2">{ret.returnDate}</td>
                  <td className="border border-gray-300 px-4 py-2">{ret.contractorName}</td>
                  <td className="border border-gray-300 px-4 py-2">
                    <span className="px-2 py-1 bg-blue-100 text-blue-700 rounded text-sm">
                      {skillCategoryLabel[ret.skillCategory]}
                    </span>
                  </td>
                  <td className="border border-gray-300 px-4 py-2 text-right font-semibold">{ret.headCount}</td>
                  <td className="border border-gray-300 px-4 py-2 text-right">{ret.manDays.toFixed(1)}</td>
                  <td className="border border-gray-300 px-4 py-2">{ret.siteLocation || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalElements > 20 && (
          <div className="flex gap-2 mt-4">
            <button
              onClick={() => loadLabourReturns(Math.max(0, page - 1))}
              disabled={page === 0}
              className="px-4 py-2 bg-gray-300 text-gray-700 rounded disabled:opacity-50"
            >
              Previous
            </button>
            <span className="px-4 py-2">{page + 1}</span>
            <button
              onClick={() => loadLabourReturns(page + 1)}
              disabled={(page + 1) * 20 >= totalElements}
              className="px-4 py-2 bg-gray-300 text-gray-700 rounded disabled:opacity-50"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
