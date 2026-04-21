"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { labourApi, type LabourReturnResponse, type CreateLabourReturnRequest, type DeploymentSummary } from "@/lib/api/labourApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

// Spring's native Page<T> serialises with these fields at the root of the
// response body (no `pagination` sub-object). LabourReturnController returns
// ApiResponse<Page<LabourReturnResponse>>.
interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

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
        // Backend returns Spring Page<T>: { content, totalElements, ... } at the root.
        const pagedData = response.data as unknown as SpringPage<LabourReturnResponse>;
        setReturns(pagedData.content ?? []);
        setTotalElements(pagedData.totalElements ?? 0);
        setPage(pageNum);
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to load labour returns"));
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
    } catch (err: unknown) {
      console.error(getErrorMessage(err, "Failed to load deployment summary"));
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
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create labour return"));
    }
  };

  if (isLoading && returns.length === 0) {
    return <div className="p-6 text-slate-500">Loading labour returns...</div>;
  }

  return (
    <div className="p-6">
      <TabTip
        title="Labour Returns"
        description="Daily labour count by contractor and skill category. Track workforce deployment across different work sites."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-white">Labour Returns</h1>

        {/* Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <div className="bg-blue-500/10 p-4 rounded-lg border border-blue-500/20">
            <p className="text-sm text-slate-400 mb-1">Total Headcount</p>
            <p className="text-2xl font-bold text-blue-400">
              {summary.reduce((sum, s) => sum + s.totalHeadCount, 0)}
            </p>
          </div>
          <div className="bg-emerald-500/10 p-4 rounded-lg border border-emerald-500/20">
            <p className="text-sm text-slate-400 mb-1">Total Man-Days</p>
            <p className="text-2xl font-bold text-emerald-400">
              {summary.reduce((sum, s) => sum + s.totalManDays, 0).toFixed(1)}
            </p>
          </div>
          <div className="bg-purple-500/10 p-4 rounded-lg border border-purple-500/20">
            <p className="text-sm text-slate-400 mb-1">Returns Submitted</p>
            <p className="text-2xl font-bold text-purple-400">{totalElements}</p>
          </div>
        </div>

        {/* Skill Category Breakdown */}
        {summary.length > 0 && (
          <div className="bg-slate-900/50 p-4 rounded-lg border border-slate-800 mb-6 shadow-xl">
            <h3 className="font-bold text-lg mb-3 text-white">Deployment by Skill Category</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-3">
              {summary.map((s) => (
                <div key={s.skillCategory} className="bg-slate-800/50 p-3 rounded border border-slate-700">
                  <p className="text-sm text-slate-400">{skillCategoryLabel[s.skillCategory]}</p>
                  <p className="text-xl font-bold text-white">{s.totalHeadCount}</p>
                  <p className="text-xs text-slate-500">{s.totalManDays.toFixed(1)} man-days</p>
                </div>
              ))}
            </div>
          </div>
        )}

        <button
          onClick={() => setShowForm(!showForm)}
          className="mb-6 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500"
        >
          {showForm ? "Cancel" : "Add Labour Return"}
        </button>

        {error && <div className="text-red-400 mb-4">{error}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} className="bg-slate-900/50 p-4 rounded-lg border border-slate-800 mb-6 shadow-xl">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-slate-300">Contractor Name</label>
                <input
                  type="text"
                  value={formData.contractorName}
                  onChange={(e) => setFormData({ ...formData, contractorName: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-700 bg-slate-800 text-white rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-slate-300">Return Date</label>
                <input
                  type="date"
                  value={formData.returnDate}
                  onChange={(e) => setFormData({ ...formData, returnDate: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-700 bg-slate-800 text-white rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-slate-300">Skill Category</label>
                <select
                  value={formData.skillCategory}
                  onChange={(e) => setFormData({ ...formData, skillCategory: e.target.value as any })}
                  className="w-full px-3 py-2 border border-slate-700 bg-slate-800 text-white rounded-lg"
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
                <label className="block text-sm font-medium mb-1 text-slate-300">Headcount</label>
                <input
                  type="number"
                  min="1"
                  value={formData.headCount}
                  onChange={(e) => setFormData({ ...formData, headCount: parseInt(e.target.value) || 1 })}
                  className="w-full px-3 py-2 border border-slate-700 bg-slate-800 text-white rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-slate-300">Man-Days</label>
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  value={formData.manDays}
                  onChange={(e) => setFormData({ ...formData, manDays: parseFloat(e.target.value) || 1 })}
                  className="w-full px-3 py-2 border border-slate-700 bg-slate-800 text-white rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-slate-300">Site Location</label>
                <input
                  type="text"
                  value={formData.siteLocation}
                  onChange={(e) => setFormData({ ...formData, siteLocation: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-700 bg-slate-800 text-white rounded-lg"
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-slate-300">Remarks</label>
                <textarea
                  value={formData.remarks}
                  onChange={(e) => setFormData({ ...formData, remarks: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-700 bg-slate-800 text-white rounded-lg"
                  rows={3}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button type="submit" className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-600">
                Save Return
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="px-4 py-2 bg-slate-700/50 text-slate-300 rounded-lg hover:bg-slate-600"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {/* Returns Table */}
        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-slate-800">
            <thead>
              <tr className="bg-slate-900/80">
                <th className="border border-slate-800 px-4 py-2 text-left text-slate-400">Date</th>
                <th className="border border-slate-800 px-4 py-2 text-left text-slate-400">Contractor</th>
                <th className="border border-slate-800 px-4 py-2 text-left text-slate-400">Skill Category</th>
                <th className="border border-slate-800 px-4 py-2 text-right text-slate-400">Headcount</th>
                <th className="border border-slate-800 px-4 py-2 text-right text-slate-400">Man-Days</th>
                <th className="border border-slate-800 px-4 py-2 text-left text-slate-400">Site Location</th>
              </tr>
            </thead>
            <tbody>
              {returns.map((ret) => (
                <tr key={ret.id} className="hover:bg-slate-800/30 text-white">
                  <td className="border border-slate-800 px-4 py-2">{ret.returnDate}</td>
                  <td className="border border-slate-800 px-4 py-2">{ret.contractorName}</td>
                  <td className="border border-slate-800 px-4 py-2">
                    <span className="px-2 py-1 bg-blue-500/10 text-blue-400 ring-1 ring-blue-500/20 rounded text-sm">
                      {skillCategoryLabel[ret.skillCategory]}
                    </span>
                  </td>
                  <td className="border border-slate-800 px-4 py-2 text-right font-semibold">{ret.headCount}</td>
                  <td className="border border-slate-800 px-4 py-2 text-right">{ret.manDays.toFixed(1)}</td>
                  <td className="border border-slate-800 px-4 py-2">{ret.siteLocation || "-"}</td>
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
              className="px-4 py-2 bg-slate-700/50 text-slate-300 rounded disabled:opacity-50"
            >
              Previous
            </button>
            <span className="px-4 py-2 text-slate-300">{page + 1}</span>
            <button
              onClick={() => loadLabourReturns(page + 1)}
              disabled={(page + 1) * 20 >= totalElements}
              className="px-4 py-2 bg-slate-700/50 text-slate-300 rounded disabled:opacity-50"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
