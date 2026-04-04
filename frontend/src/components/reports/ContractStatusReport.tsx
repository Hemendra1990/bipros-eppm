"use client";

import { useMemo } from "react";
import { FileCheck, AlertCircle } from "lucide-react";
import type { ContractStatusData } from "@/lib/api/reportDataApi";

interface ContractStatusReportProps {
  data: ContractStatusData;
}

export function ContractStatusReport({ data }: ContractStatusReportProps) {
  const milestoneCompletion = useMemo(() => {
    const total = data.pendingMilestones + data.achievedMilestones;
    return total > 0 ? (data.achievedMilestones / total) * 100 : 0;
  }, [data.pendingMilestones, data.achievedMilestones]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-gradient-to-r from-blue-50 to-cyan-50 p-4 rounded-lg border border-blue-200">
        <div>
          <h3 className="font-semibold text-lg text-gray-900">{data.projectName}</h3>
          <p className="text-sm text-gray-600">Contract & Variation Order Status</p>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider">Total Contracts</p>
          <p className="text-2xl font-bold text-gray-900">{data.totalContracts}</p>
          <p className="text-xs text-green-600 mt-1">{data.activeContracts} active</p>
        </div>

        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider">Total Value</p>
          <p className="text-2xl font-bold text-blue-600">${data.totalContractValue.toFixed(0)}</p>
        </div>

        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider">VO Value</p>
          <p className="text-2xl font-bold text-orange-600">${data.totalVoValue.toFixed(0)}</p>
        </div>

        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 uppercase tracking-wider">Milestones</p>
          <p className="text-2xl font-bold text-gray-900">{data.achievedMilestones}/{data.achievedMilestones + data.pendingMilestones}</p>
          <p className="text-xs text-gray-600 mt-1">{milestoneCompletion.toFixed(0)}% complete</p>
        </div>
      </div>

      {/* Contracts Table */}
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <h4 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
          <FileCheck size={20} />
          Contracts
        </h4>
        {data.contracts.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="text-left py-3 px-2 text-gray-600 font-semibold">Contract #</th>
                  <th className="text-left py-3 px-2 text-gray-600 font-semibold">Contractor</th>
                  <th className="text-right py-3 px-2 text-gray-600 font-semibold">Value</th>
                  <th className="text-left py-3 px-2 text-gray-600 font-semibold">Status</th>
                  <th className="text-right py-3 px-2 text-gray-600 font-semibold">Pending Milestones</th>
                </tr>
              </thead>
              <tbody>
                {data.contracts.map((contract, idx) => (
                  <tr key={idx} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="py-3 px-2 font-mono text-gray-900">{contract.contractNumber}</td>
                    <td className="py-3 px-2 text-gray-700">{contract.contractor}</td>
                    <td className="py-3 px-2 text-right font-semibold">${contract.value.toFixed(2)}</td>
                    <td className="py-3 px-2">
                      <span className={`px-2 py-1 rounded text-xs font-semibold ${
                        contract.status === "ACTIVE" || contract.status === "ONGOING"
                          ? "bg-green-100 text-green-700"
                          : "bg-gray-100 text-gray-700"
                      }`}>
                        {contract.status}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-right">
                      {contract.milestonesPending > 0 ? (
                        <span className="px-2 py-1 bg-orange-100 text-orange-700 rounded text-xs font-semibold">
                          {contract.milestonesPending}
                        </span>
                      ) : (
                        <span className="text-gray-500">0</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-gray-500 text-center py-4">No contracts found</p>
        )}
      </div>

      {/* Summary Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h4 className="font-semibold text-gray-900 mb-3">Contract Summary</h4>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">Active Contracts</span>
              <span className="font-semibold">{data.activeContracts}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Inactive/Completed</span>
              <span className="font-semibold">{data.totalContracts - data.activeContracts}</span>
            </div>
            <div className="flex justify-between border-t pt-2 mt-2">
              <span className="text-gray-600">Total Contract Value</span>
              <span className="font-semibold text-blue-600">${data.totalContractValue.toFixed(0)}</span>
            </div>
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h4 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
            <AlertCircle size={18} />
            Variations
          </h4>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">Total VO Value</span>
              <span className="font-semibold text-orange-600">${data.totalVoValue.toFixed(0)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">% of Contract Value</span>
              <span className="font-semibold">
                {data.totalContractValue > 0
                  ? ((data.totalVoValue / data.totalContractValue) * 100).toFixed(1)
                  : 0}%
              </span>
            </div>
            <div className="flex justify-between border-t pt-2 mt-2">
              <span className="text-gray-600">Total Project Value</span>
              <span className="font-semibold text-gray-900">
                ${(data.totalContractValue + data.totalVoValue).toFixed(0)}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
