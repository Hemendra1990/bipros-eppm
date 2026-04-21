"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { contractApi } from "@/lib/api/contractApi";
import { TabTip } from "@/components/common/TabTip";
import type { ContractResponse, CreateContractRequest } from "@/lib/types";

export default function ContractsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const queryClient = useQueryClient();

  const { data: contractsData, isLoading } = useQuery({
    queryKey: ["contracts", projectId, page],
    queryFn: () => contractApi.listContracts(projectId, page, size),
    select: (response) => response.data || { content: [], pagination: { totalElements: 0, totalPages: 0, currentPage: 0, pageSize: 0 } },
  });

  const createMutation = useMutation({
    mutationFn: (data: CreateContractRequest) => contractApi.createContract(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contracts", projectId] });
      setShowCreateForm(false);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => contractApi.deleteContract(projectId, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contracts", projectId] });
    },
  });

  const handleCreateSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);

    const request: CreateContractRequest = {
      projectId,
      contractNumber: formData.get("contractNumber") as string,
      contractorName: formData.get("contractorName") as string,
      contractValue: parseFloat(formData.get("contractValue") as string),
      loaDate: formData.get("loaDate") as string,
      startDate: formData.get("startDate") as string,
      completionDate: formData.get("completionDate") as string,
      ldRate: parseFloat(formData.get("ldRate") as string),
      contractType: formData.get("contractType") as any,
      loaNumber: formData.get("loaNumber") as string || undefined,
      contractorCode: formData.get("contractorCode") as string || undefined,
      dlpMonths: formData.get("dlpMonths") ? parseInt(formData.get("dlpMonths") as string) : undefined,
    };

    createMutation.mutate(request);
  };

  if (isLoading) {
    return <div className="p-6 text-center text-slate-500">Loading contracts...</div>;
  }

  const contracts = contractsData?.content || [];
  const pagination = contractsData?.pagination;
  const totalPages = pagination?.totalPages || 0;

  return (
    <div className="space-y-6">
      <TabTip
        title="Contracts & Procurement"
        description="Track contractor agreements, LOA values, milestones, and variation orders. Create contracts to link with project activities and costs."
      />
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold text-white">Contracts</h2>
        <button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500 transition-colors"
        >
          {showCreateForm ? "Cancel" : "Create Contract"}
        </button>
      </div>

      {showCreateForm && (
        <div className="bg-slate-900/50 border border-slate-800 rounded-xl p-6 shadow-xl">
          <h3 className="text-lg font-medium text-white mb-4">New Contract</h3>
          <form onSubmit={handleCreateSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Contract Number</label>
                <input
                  type="text"
                  name="contractNumber"
                  required
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                  placeholder="CON-2024-001"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">LOA Number</label>
                <input
                  type="text"
                  name="loaNumber"
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                  placeholder="Optional"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Contractor Name</label>
                <input
                  type="text"
                  name="contractorName"
                  required
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                  placeholder="Company Name"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Contractor Code</label>
                <input
                  type="text"
                  name="contractorCode"
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                  placeholder="Optional"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Contract Value</label>
                <input
                  type="number"
                  name="contractValue"
                  required
                  step="0.01"
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                  placeholder="0.00"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">LD Rate (%)</label>
                <input
                  type="number"
                  name="ldRate"
                  required
                  step="0.01"
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                  placeholder="0.00"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">LOA Date</label>
                <input
                  type="date"
                  name="loaDate"
                  required
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Start Date</label>
                <input
                  type="date"
                  name="startDate"
                  required
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Completion Date</label>
                <input
                  type="date"
                  name="completionDate"
                  required
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">DLP Months</label>
                <input
                  type="number"
                  name="dlpMonths"
                  step="1"
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                  placeholder="12"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Contract Type</label>
                <select
                  name="contractType"
                  required
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                >
                  <option value="">Select Type</option>
                  <option value="EPC_LUMP_SUM_FIDIC_YELLOW">EPC Lump-Sum (FIDIC Yellow)</option>
                  <option value="EPC_LUMP_SUM_FIDIC_RED">EPC Lump-Sum (FIDIC Red)</option>
                  <option value="EPC_LUMP_SUM_FIDIC_SILVER">EPC Lump-Sum (FIDIC Silver)</option>
                  <option value="ITEM_RATE_FIDIC_RED">Item Rate (FIDIC Red)</option>
                  <option value="PERCENTAGE_BASED_PMC">Percentage-Based PMC</option>
                  <option value="LUMP_SUM_UNIT_RATE">Lump-Sum / Unit-Rate</option>
                </select>
              </div>
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-600 disabled:bg-slate-600 transition-colors"
              >
                {createMutation.isPending ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => setShowCreateForm(false)}
                className="px-4 py-2 bg-slate-700/50 text-slate-300 rounded-lg hover:bg-slate-600 transition-colors"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="grid gap-4">
        {contracts.length === 0 ? (
          <div className="text-center py-8 text-slate-500">No contracts found</div>
        ) : (
          contracts.map((contract: ContractResponse) => {
            const statusBadge =
              contract.status === "ACTIVE"
                ? "bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20"
                : contract.status === "COMPLETED"
                  ? "bg-blue-500/10 text-blue-400 ring-1 ring-blue-500/20"
                  : contract.status === "ACTIVE_AT_RISK"
                    ? "bg-amber-500/10 text-amber-400 ring-1 ring-amber-500/20"
                    : contract.status === "ACTIVE_DELAYED" || contract.status === "DELAYED"
                      ? "bg-red-500/10 text-red-400 ring-1 ring-red-500/20"
                      : contract.status === "MOBILISATION"
                        ? "bg-indigo-500/10 text-indigo-400 ring-1 ring-indigo-500/20"
                        : "bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50";
            const spiBadge = (v: number | null | undefined) =>
              v == null
                ? ""
                : v >= 0.95
                  ? "bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20"
                  : v >= 0.85
                    ? "bg-amber-500/10 text-amber-400 ring-1 ring-amber-500/20"
                    : "bg-red-500/10 text-red-400 ring-1 ring-red-500/20";
            const bgDays = contract.bgExpiry
              ? Math.round(
                  (new Date(contract.bgExpiry).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
                )
              : null;
            const bgExpiryClass =
              bgDays == null
                ? "bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50"
                : bgDays < 30
                  ? "bg-red-500/10 text-red-400 ring-1 ring-red-500/20"
                  : bgDays < 90
                    ? "bg-amber-500/10 text-amber-400 ring-1 ring-amber-500/20"
                    : "bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20";
            return (
            <div key={contract.id} className="bg-slate-900/50 border border-slate-800 rounded-xl p-4 hover:bg-slate-900/70 transition-colors shadow-xl">
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-white">{contract.contractNumber}</h3>
                  {contract.packageDescription && (
                    <p className="text-sm text-slate-300 mt-0.5">{contract.packageDescription}</p>
                  )}
                  <p className="text-sm text-slate-400 mt-1">
                    <span className="font-medium">Contractor:</span> {contract.contractorName}
                    {contract.wbsPackageCode && (
                      <> · <span className="font-medium">WBS:</span> {contract.wbsPackageCode}</>
                    )}
                  </p>
                  <p className="text-sm text-slate-400">
                    <span className="font-medium">Value:</span> ₹{contract.contractValue.toLocaleString()} cr | <span className="font-medium">Type:</span> {contract.contractType.replace(/_/g, " ")}
                  </p>
                  <p className="text-sm text-slate-400">
                    <span className="font-medium">Dates:</span> {new Date(contract.loaDate).toLocaleDateString()} - {new Date(contract.completionDate).toLocaleDateString()}
                  </p>
                  <div className="mt-2 flex flex-wrap gap-2 items-center">
                    <span className={`inline-block px-2 py-1 text-xs font-semibold rounded-full ${statusBadge}`}>
                      {contract.status}
                    </span>
                    {contract.spi != null && (
                      <span className={`inline-block px-2 py-1 text-xs font-semibold rounded-full ${spiBadge(contract.spi)}`}>
                        SPI {contract.spi.toFixed(2)}
                      </span>
                    )}
                    {contract.cpi != null && (
                      <span className={`inline-block px-2 py-1 text-xs font-semibold rounded-full ${spiBadge(contract.cpi)}`}>
                        CPI {contract.cpi.toFixed(2)}
                      </span>
                    )}
                    {contract.physicalProgressAi != null && (
                      <span className="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-sky-500/10 text-sky-400 ring-1 ring-sky-500/20">
                        Progress {contract.physicalProgressAi.toFixed(1)}%
                      </span>
                    )}
                    {contract.voNumbersIssued != null && contract.voNumbersIssued > 0 && (
                      <span className="inline-block px-2 py-1 text-xs font-semibold rounded-full bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50">
                        VOs {contract.voNumbersIssued}
                        {contract.voValueCrores != null && ` · ₹${contract.voValueCrores.toFixed(1)}cr`}
                      </span>
                    )}
                    {contract.bgExpiry && (
                      <span className={`inline-block px-2 py-1 text-xs font-semibold rounded-full ${bgExpiryClass}`}>
                        BG {new Date(contract.bgExpiry).toLocaleDateString()}
                        {bgDays != null && bgDays < 90 && ` (${bgDays}d)`}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex gap-2">
                  <a
                    href={`/projects/${projectId}/contracts/${contract.id}`}
                    className="px-3 py-2 text-sm font-medium text-blue-400 hover:text-blue-300"
                  >
                    View
                  </a>
                  <button
                    onClick={() => deleteMutation.mutate(contract.id)}
                    disabled={deleteMutation.isPending}
                    className="px-3 py-2 text-sm font-medium text-red-400 hover:text-red-300 disabled:text-slate-500"
                  >
                    Delete
                  </button>
                </div>
              </div>
            </div>
            );
          })
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-6">
          <button
            onClick={() => setPage(Math.max(0, page - 1))}
            disabled={page === 0}
            className="px-4 py-2 bg-slate-700/50 text-slate-300 rounded-lg disabled:bg-slate-800 disabled:text-slate-600 hover:bg-slate-600 transition-colors"
          >
            Previous
          </button>
          <span className="px-4 py-2 text-slate-300">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
            disabled={page >= totalPages - 1}
            className="px-4 py-2 bg-slate-700/50 text-slate-300 rounded-lg disabled:bg-slate-800 disabled:text-slate-600 hover:bg-slate-600 transition-colors"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
