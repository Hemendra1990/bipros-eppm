"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { contractApi } from "@/lib/api/contractApi";
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
    return <div className="p-6 text-center text-gray-500">Loading contracts...</div>;
  }

  const contracts = contractsData?.content || [];
  const pagination = contractsData?.pagination;
  const totalPages = pagination?.totalPages || 0;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold text-gray-900">Contracts</h2>
        <button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          {showCreateForm ? "Cancel" : "Create Contract"}
        </button>
      </div>

      {showCreateForm && (
        <div className="bg-white border border-gray-200 rounded-lg p-6">
          <h3 className="text-lg font-medium text-gray-900 mb-4">New Contract</h3>
          <form onSubmit={handleCreateSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Contract Number</label>
                <input
                  type="text"
                  name="contractNumber"
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="CON-2024-001"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">LOA Number</label>
                <input
                  type="text"
                  name="loaNumber"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="Optional"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Contractor Name</label>
                <input
                  type="text"
                  name="contractorName"
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="Company Name"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Contractor Code</label>
                <input
                  type="text"
                  name="contractorCode"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="Optional"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Contract Value</label>
                <input
                  type="number"
                  name="contractValue"
                  required
                  step="0.01"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="0.00"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">LD Rate (%)</label>
                <input
                  type="number"
                  name="ldRate"
                  required
                  step="0.01"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="0.00"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">LOA Date</label>
                <input
                  type="date"
                  name="loaDate"
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Start Date</label>
                <input
                  type="date"
                  name="startDate"
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Completion Date</label>
                <input
                  type="date"
                  name="completionDate"
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">DLP Months</label>
                <input
                  type="number"
                  name="dlpMonths"
                  step="1"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="12"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Contract Type</label>
                <select
                  name="contractType"
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="">Select Type</option>
                  <option value="WORKS">Works</option>
                  <option value="SUPPLY">Supply</option>
                  <option value="CONSULTANCY">Consultancy</option>
                  <option value="EPC">EPC</option>
                  <option value="PPP">PPP</option>
                </select>
              </div>
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-400 transition-colors"
              >
                {createMutation.isPending ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => setShowCreateForm(false)}
                className="px-4 py-2 bg-gray-300 text-gray-900 rounded-lg hover:bg-gray-400 transition-colors"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="grid gap-4">
        {contracts.length === 0 ? (
          <div className="text-center py-8 text-gray-500">No contracts found</div>
        ) : (
          contracts.map((contract: ContractResponse) => (
            <div key={contract.id} className="bg-white border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow">
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-gray-900">{contract.contractNumber}</h3>
                  <p className="text-sm text-gray-600 mt-1">
                    <span className="font-medium">Contractor:</span> {contract.contractorName}
                  </p>
                  <p className="text-sm text-gray-600">
                    <span className="font-medium">Value:</span> {contract.contractValue.toLocaleString()} | <span className="font-medium">Type:</span> {contract.contractType}
                  </p>
                  <p className="text-sm text-gray-600">
                    <span className="font-medium">Dates:</span> {new Date(contract.loaDate).toLocaleDateString()} - {new Date(contract.completionDate).toLocaleDateString()}
                  </p>
                  <span className={`inline-block mt-2 px-2 py-1 text-xs font-semibold rounded-full ${
                    contract.status === "ACTIVE"
                      ? "bg-green-100 text-green-800"
                      : contract.status === "COMPLETED"
                        ? "bg-blue-100 text-blue-800"
                        : "bg-gray-100 text-gray-800"
                  }`}>
                    {contract.status}
                  </span>
                </div>
                <div className="flex gap-2">
                  <a
                    href={`/projects/${projectId}/contracts/${contract.id}`}
                    className="px-3 py-2 text-sm font-medium text-blue-600 hover:text-blue-800"
                  >
                    View
                  </a>
                  <button
                    onClick={() => deleteMutation.mutate(contract.id)}
                    disabled={deleteMutation.isPending}
                    className="px-3 py-2 text-sm font-medium text-red-600 hover:text-red-800 disabled:text-gray-400"
                  >
                    Delete
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-6">
          <button
            onClick={() => setPage(Math.max(0, page - 1))}
            disabled={page === 0}
            className="px-4 py-2 bg-gray-300 text-gray-900 rounded-lg disabled:bg-gray-100 hover:bg-gray-400 transition-colors"
          >
            Previous
          </button>
          <span className="px-4 py-2 text-gray-700">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
            disabled={page >= totalPages - 1}
            className="px-4 py-2 bg-gray-300 text-gray-900 rounded-lg disabled:bg-gray-100 hover:bg-gray-400 transition-colors"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
