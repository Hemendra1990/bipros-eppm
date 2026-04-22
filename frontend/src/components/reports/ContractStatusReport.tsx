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
          <h3 className="font-semibold text-lg text-text-primary">{data.projectName}</h3>
          <p className="text-sm text-text-secondary">Contract & Variation Order Status</p>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-surface/50 border border-border rounded-lg p-4">
          <p className="text-xs text-text-muted uppercase tracking-wider">Total Contracts</p>
          <p className="text-2xl font-bold text-text-primary">{data.totalContracts}</p>
          <p className="text-xs text-success mt-1">{data.activeContracts} active</p>
        </div>

        <div className="bg-surface/50 border border-border rounded-lg p-4">
          <p className="text-xs text-text-muted uppercase tracking-wider">Total Value</p>
          <p className="text-2xl font-bold text-accent">${data.totalContractValue.toFixed(0)}</p>
        </div>

        <div className="bg-surface/50 border border-border rounded-lg p-4">
          <p className="text-xs text-text-muted uppercase tracking-wider">VO Value</p>
          <p className="text-2xl font-bold text-orange-600">${data.totalVoValue.toFixed(0)}</p>
        </div>

        <div className="bg-surface/50 border border-border rounded-lg p-4">
          <p className="text-xs text-text-muted uppercase tracking-wider">Milestones</p>
          <p className="text-2xl font-bold text-text-primary">{data.achievedMilestones}/{data.achievedMilestones + data.pendingMilestones}</p>
          <p className="text-xs text-text-secondary mt-1">{milestoneCompletion.toFixed(0)}% complete</p>
        </div>
      </div>

      {/* Contracts Table */}
      <div className="bg-surface/50 border border-border rounded-lg p-4">
        <h4 className="font-semibold text-text-primary mb-4 flex items-center gap-2">
          <FileCheck size={20} />
          Contracts
        </h4>
        {data.contracts.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-3 px-2 text-text-secondary font-semibold">Contract #</th>
                  <th className="text-left py-3 px-2 text-text-secondary font-semibold">Contractor</th>
                  <th className="text-right py-3 px-2 text-text-secondary font-semibold">Value</th>
                  <th className="text-left py-3 px-2 text-text-secondary font-semibold">Status</th>
                  <th className="text-right py-3 px-2 text-text-secondary font-semibold">Pending Milestones</th>
                </tr>
              </thead>
              <tbody>
                {data.contracts.map((contract, idx) => (
                  <tr key={idx} className="border-b border-border/50 hover:bg-surface/80">
                    <td className="py-3 px-2 font-mono text-text-primary">{contract.contractNumber}</td>
                    <td className="py-3 px-2 text-text-secondary">{contract.contractor}</td>
                    <td className="py-3 px-2 text-right font-semibold">${contract.value.toFixed(2)}</td>
                    <td className="py-3 px-2">
                      <span className={`px-2 py-1 rounded text-xs font-semibold ${
                        contract.status === "ACTIVE" || contract.status === "ONGOING"
                          ? "bg-success/10 text-success"
                          : "bg-surface-hover/50 text-text-secondary"
                      }`}>
                        {contract.status}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-right">
                      {contract.milestonesPending > 0 ? (
                        <span className="px-2 py-1 bg-orange-500/10 text-orange-400 rounded text-xs font-semibold">
                          {contract.milestonesPending}
                        </span>
                      ) : (
                        <span className="text-text-muted">0</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-text-muted text-center py-4">No contracts found</p>
        )}
      </div>

      {/* Summary Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-surface/50 border border-border rounded-lg p-4">
          <h4 className="font-semibold text-text-primary mb-3">Contract Summary</h4>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-text-secondary">Active Contracts</span>
              <span className="font-semibold">{data.activeContracts}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-text-secondary">Inactive/Completed</span>
              <span className="font-semibold">{data.totalContracts - data.activeContracts}</span>
            </div>
            <div className="flex justify-between border-t pt-2 mt-2">
              <span className="text-text-secondary">Total Contract Value</span>
              <span className="font-semibold text-accent">${data.totalContractValue.toFixed(0)}</span>
            </div>
          </div>
        </div>

        <div className="bg-surface/50 border border-border rounded-lg p-4">
          <h4 className="font-semibold text-text-primary mb-3 flex items-center gap-2">
            <AlertCircle size={18} />
            Variations
          </h4>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-text-secondary">Total VO Value</span>
              <span className="font-semibold text-orange-600">${data.totalVoValue.toFixed(0)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-text-secondary">% of Contract Value</span>
              <span className="font-semibold">
                {data.totalContractValue > 0
                  ? ((data.totalVoValue / data.totalContractValue) * 100).toFixed(1)
                  : 0}%
              </span>
            </div>
            <div className="flex justify-between border-t pt-2 mt-2">
              <span className="text-text-secondary">Total Project Value</span>
              <span className="font-semibold text-text-primary">
                ${(data.totalContractValue + data.totalVoValue).toFixed(0)}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
