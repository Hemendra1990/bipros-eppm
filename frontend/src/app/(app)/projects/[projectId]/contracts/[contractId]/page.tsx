"use client";

import { useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { contractApi } from "@/lib/api/contractApi";
import type {
  ContractMilestoneResponse,
  CreateContractMilestoneRequest,
  VariationOrderResponse,
  CreateVariationOrderRequest,
  PerformanceBondResponse,
  CreatePerformanceBondRequest,
  ContractorScorecardResponse,
  CreateContractorScorecardRequest,
} from "@/lib/types";

export default function ContractDetailPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const projectId = params.projectId as string;
  const contractId = params.contractId as string;
  const activeTab = searchParams.get("tab") || "milestones";
  const queryClient = useQueryClient();

  const { data: contractData, isLoading } = useQuery({
    queryKey: ["contract", projectId, contractId],
    queryFn: () => contractApi.getContract(projectId, contractId),
    select: (response) => response.data,
  });

  const { data: milestones = [] } = useQuery({
    queryKey: ["contract-milestones", contractId],
    queryFn: () => contractApi.listContractMilestones(contractId),
    select: (response) => response.data || [],
  });

  const { data: variationOrders = [] } = useQuery({
    queryKey: ["variation-orders", contractId],
    queryFn: () => contractApi.listVariationOrders(contractId),
    select: (response) => response.data || [],
  });

  const { data: bonds = [] } = useQuery({
    queryKey: ["performance-bonds", contractId],
    queryFn: () => contractApi.listPerformanceBonds(contractId),
    select: (response) => response.data || [],
  });

  const { data: scorecards = [] } = useQuery({
    queryKey: ["contractor-scorecards", contractId],
    queryFn: () => contractApi.listContractorScorecards(contractId),
    select: (response) => response.data || [],
  });

  const createMilestoneMutation = useMutation({
    mutationFn: (data: CreateContractMilestoneRequest) => contractApi.createContractMilestone(contractId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contract-milestones", contractId] });
    },
  });

  const createVoMutation = useMutation({
    mutationFn: (data: CreateVariationOrderRequest) => contractApi.createVariationOrder(contractId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variation-orders", contractId] });
    },
  });

  const createBondMutation = useMutation({
    mutationFn: (data: CreatePerformanceBondRequest) => contractApi.createPerformanceBond(contractId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["performance-bonds", contractId] });
    },
  });

  const createScorecardMutation = useMutation({
    mutationFn: (data: CreateContractorScorecardRequest) => contractApi.createContractorScorecard(contractId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contractor-scorecards", contractId] });
    },
  });

  if (isLoading) {
    return <div className="p-6 text-center text-slate-500">Loading contract...</div>;
  }

  if (!contractData) {
    return <div className="p-6 text-center text-red-400">Contract not found</div>;
  }

  const tabs = [
    { id: "milestones", label: "Milestones" },
    { id: "variation-orders", label: "Variation Orders" },
    { id: "bonds", label: "Performance Bonds" },
    { id: "scorecard", label: "Scorecard" },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">{contractData.contractNumber}</h1>
        <p className="text-sm text-slate-400 mt-1">Contractor: {contractData.contractorName}</p>
        <div className="mt-4 grid grid-cols-4 gap-4">
          <div className="bg-slate-900/80 rounded-xl p-3 border border-slate-800">
            <p className="text-xs text-slate-400">Contract Value</p>
            <p className="text-lg font-semibold text-white">{contractData.contractValue.toLocaleString()}</p>
          </div>
          <div className="bg-slate-900/80 rounded-xl p-3 border border-slate-800">
            <p className="text-xs text-slate-400">Status</p>
            <p className="text-lg font-semibold text-white">{contractData.status}</p>
          </div>
          <div className="bg-slate-900/80 rounded-xl p-3 border border-slate-800">
            <p className="text-xs text-slate-400">Type</p>
            <p className="text-lg font-semibold text-white">{contractData.contractType}</p>
          </div>
          <div className="bg-slate-900/80 rounded-xl p-3 border border-slate-800">
            <p className="text-xs text-slate-400">Duration</p>
            <p className="text-lg font-semibold text-white">{contractData.dlpMonths} months</p>
          </div>
        </div>
      </div>

      <div className="border-b border-slate-800">
        <nav className="flex gap-8" aria-label="Tabs">
          {tabs.map((t) => (
            <a
              key={t.id}
              href={`?tab=${t.id}`}
              className={`px-1 py-4 text-sm font-medium border-b-2 transition-colors ${
                activeTab === t.id
                  ? "border-blue-500 text-blue-400"
                  : "border-transparent text-slate-400 hover:text-white hover:border-slate-700"
              }`}
            >
              {t.label}
            </a>
          ))}
        </nav>
      </div>

      <div>
        {activeTab === "milestones" && (
          <MilestonesTab
            contractId={contractId}
            milestones={milestones}
            isCreating={createMilestoneMutation.isPending}
            onCreate={(data) => createMilestoneMutation.mutate(data)}
          />
        )}

        {activeTab === "variation-orders" && (
          <VariationOrdersTab
            contractId={contractId}
            variationOrders={variationOrders}
            isCreating={createVoMutation.isPending}
            onCreate={(data) => createVoMutation.mutate(data)}
          />
        )}

        {activeTab === "bonds" && (
          <PerformanceBondsTab
            contractId={contractId}
            bonds={bonds}
            isCreating={createBondMutation.isPending}
            onCreate={(data) => createBondMutation.mutate(data)}
          />
        )}

        {activeTab === "scorecard" && (
          <ScorecardTab
            contractId={contractId}
            scorecards={scorecards}
            isCreating={createScorecardMutation.isPending}
            onCreate={(data) => createScorecardMutation.mutate(data)}
          />
        )}
      </div>
    </div>
  );
}

interface MilestonesTabProps {
  contractId: string;
  milestones: ContractMilestoneResponse[];
  isCreating: boolean;
  onCreate: (data: CreateContractMilestoneRequest) => void;
}

function MilestonesTab({ contractId, milestones, isCreating, onCreate }: MilestonesTabProps) {
  const [showForm, setShowForm] = useState(false);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    onCreate({
      contractId,
      milestoneCode: formData.get("milestoneCode") as string,
      milestoneName: formData.get("milestoneName") as string,
      targetDate: formData.get("targetDate") as string,
      actualDate: (formData.get("actualDate") as string) || undefined,
      paymentPercentage: parseFloat(formData.get("paymentPercentage") as string),
      amount: parseFloat(formData.get("amount") as string),
    });
    setShowForm(false);
  };

  return (
    <div className="space-y-4">
      <button
        onClick={() => setShowForm(!showForm)}
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500 transition-colors"
      >
        {showForm ? "Cancel" : "Add Milestone"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-slate-900/50 border border-slate-800 rounded-xl p-4 space-y-3 shadow-xl">
          <div className="grid grid-cols-2 gap-3">
            <input type="text" name="milestoneCode" placeholder="Code" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="text" name="milestoneName" placeholder="Name" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="date" name="targetDate" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white" />
            <input type="date" name="actualDate" placeholder="Actual Date (optional)" className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="number" name="paymentPercentage" placeholder="Payment %" step="0.01" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="number" name="amount" placeholder="Amount" step="0.01" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
          </div>
          <button type="submit" disabled={isCreating} className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-600 disabled:bg-slate-600">
            Create
          </button>
        </form>
      )}

      <div className="space-y-2">
        {milestones.length === 0 ? (
          <p className="text-slate-500 text-center py-4">No milestones</p>
        ) : (
          milestones.map((m) => (
            <div key={m.id} className="bg-slate-900/50 border border-slate-800 rounded-xl p-4 shadow-xl">
              <div className="flex justify-between items-start">
                <div>
                  <p className="font-semibold text-white">{m.milestoneName}</p>
                  <p className="text-sm text-slate-400">Target: {new Date(m.targetDate).toLocaleDateString()}</p>
                  <p className="text-sm text-slate-400">Payment: {m.paymentPercentage}% | Amount: {m.amount.toLocaleString()}</p>
                  <span className="inline-block mt-2 px-2 py-1 text-xs font-semibold rounded-full bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50">{m.status}</span>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

interface VariationOrdersTabProps {
  contractId: string;
  variationOrders: VariationOrderResponse[];
  isCreating: boolean;
  onCreate: (data: CreateVariationOrderRequest) => void;
}

function VariationOrdersTab({ contractId, variationOrders, isCreating, onCreate }: VariationOrdersTabProps) {
  const [showForm, setShowForm] = useState(false);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    onCreate({
      contractId,
      voNumber: formData.get("voNumber") as string,
      description: formData.get("description") as string,
      voValue: parseFloat(formData.get("voValue") as string),
      justification: formData.get("justification") as string,
      impactOnBudget: parseFloat(formData.get("impactOnBudget") as string),
      impactOnScheduleDays: parseInt(formData.get("impactOnScheduleDays") as string),
      approvedBy: (formData.get("approvedBy") as string) || undefined,
    });
    setShowForm(false);
  };

  return (
    <div className="space-y-4">
      <button
        onClick={() => setShowForm(!showForm)}
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500 transition-colors"
      >
        {showForm ? "Cancel" : "Add Variation Order"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-slate-900/50 border border-slate-800 rounded-xl p-4 space-y-3 shadow-xl">
          <div className="grid grid-cols-2 gap-3">
            <input type="text" name="voNumber" placeholder="VO Number" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="number" name="voValue" placeholder="Value" step="0.01" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <textarea name="description" placeholder="Description" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg col-span-2 text-white placeholder-slate-500"></textarea>
            <textarea name="justification" placeholder="Justification" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg col-span-2 text-white placeholder-slate-500"></textarea>
            <input type="number" name="impactOnBudget" placeholder="Budget Impact" step="0.01" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="number" name="impactOnScheduleDays" placeholder="Schedule Impact (days)" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="text" name="approvedBy" placeholder="Approved By (optional)" className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg col-span-2 text-white placeholder-slate-500" />
          </div>
          <button type="submit" disabled={isCreating} className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-600 disabled:bg-slate-600">
            Create
          </button>
        </form>
      )}

      <div className="space-y-2">
        {variationOrders.length === 0 ? (
          <p className="text-slate-500 text-center py-4">No variation orders</p>
        ) : (
          variationOrders.map((vo) => (
            <div key={vo.id} className="bg-slate-900/50 border border-slate-800 rounded-xl p-4 shadow-xl">
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  <p className="font-semibold text-white">{vo.voNumber}</p>
                  <p className="text-sm text-slate-400">{vo.description}</p>
                  <p className="text-sm text-slate-400">Value: {vo.voValue.toLocaleString()} | Budget Impact: {vo.impactOnBudget.toLocaleString()}</p>
                  <p className="text-sm text-slate-400">Schedule Impact: {vo.impactOnScheduleDays} days</p>
                  <span className="inline-block mt-2 px-2 py-1 text-xs font-semibold rounded-full bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50">{vo.status}</span>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

interface PerformanceBondsTabProps {
  contractId: string;
  bonds: PerformanceBondResponse[];
  isCreating: boolean;
  onCreate: (data: CreatePerformanceBondRequest) => void;
}

function PerformanceBondsTab({ contractId, bonds, isCreating, onCreate }: PerformanceBondsTabProps) {
  const [showForm, setShowForm] = useState(false);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    onCreate({
      contractId,
      bondType: formData.get("bondType") as any,
      bondValue: parseFloat(formData.get("bondValue") as string),
      bankName: formData.get("bankName") as string,
      issueDate: formData.get("issueDate") as string,
      expiryDate: formData.get("expiryDate") as string,
    });
    setShowForm(false);
  };

  return (
    <div className="space-y-4">
      <button
        onClick={() => setShowForm(!showForm)}
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500 transition-colors"
      >
        {showForm ? "Cancel" : "Add Bond"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-slate-900/50 border border-slate-800 rounded-xl p-4 space-y-3 shadow-xl">
          <div className="grid grid-cols-2 gap-3">
            <select name="bondType" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white">
              <option value="">Select Type</option>
              <option value="PERFORMANCE_GUARANTEE">Performance Guarantee</option>
              <option value="EMD">EMD</option>
              <option value="ADVANCE_GUARANTEE">Advance Guarantee</option>
              <option value="RETENTION">Retention</option>
            </select>
            <input type="number" name="bondValue" placeholder="Value" step="0.01" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="text" name="bankName" placeholder="Bank Name" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="date" name="issueDate" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white" />
            <input type="date" name="expiryDate" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white" />
          </div>
          <button type="submit" disabled={isCreating} className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-600 disabled:bg-slate-600">
            Create
          </button>
        </form>
      )}

      <div className="space-y-2">
        {bonds.length === 0 ? (
          <p className="text-slate-500 text-center py-4">No bonds</p>
        ) : (
          bonds.map((bond) => (
            <div key={bond.id} className="bg-slate-900/50 border border-slate-800 rounded-xl p-4 shadow-xl">
              <div className="flex justify-between items-start">
                <div>
                  <p className="font-semibold text-white">{bond.bondType}</p>
                  <p className="text-sm text-slate-400">Bank: {bond.bankName}</p>
                  <p className="text-sm text-slate-400">Value: {bond.bondValue.toLocaleString()} | Valid: {new Date(bond.issueDate).toLocaleDateString()} - {new Date(bond.expiryDate).toLocaleDateString()}</p>
                  <span className="inline-block mt-2 px-2 py-1 text-xs font-semibold rounded-full bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50">{bond.status}</span>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

interface ScorecardTabProps {
  contractId: string;
  scorecards: ContractorScorecardResponse[];
  isCreating: boolean;
  onCreate: (data: CreateContractorScorecardRequest) => void;
}

function ScorecardTab({ contractId, scorecards, isCreating, onCreate }: ScorecardTabProps) {
  const [showForm, setShowForm] = useState(false);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    onCreate({
      contractId,
      period: formData.get("period") as string,
      qualityScore: parseFloat(formData.get("qualityScore") as string),
      safetyScore: parseFloat(formData.get("safetyScore") as string),
      progressScore: parseFloat(formData.get("progressScore") as string),
      paymentComplianceScore: parseFloat(formData.get("paymentComplianceScore") as string),
      overallScore: parseFloat(formData.get("overallScore") as string),
      remarks: (formData.get("remarks") as string) || undefined,
    });
    setShowForm(false);
  };

  return (
    <div className="space-y-4">
      <button
        onClick={() => setShowForm(!showForm)}
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500 transition-colors"
      >
        {showForm ? "Cancel" : "Add Scorecard"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-slate-900/50 border border-slate-800 rounded-xl p-4 space-y-3 shadow-xl">
          <div className="grid grid-cols-2 gap-3">
            <input type="text" name="period" placeholder="Period (e.g., Q1-2024)" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="number" name="qualityScore" placeholder="Quality Score" step="0.1" min="0" max="10" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="number" name="safetyScore" placeholder="Safety Score" step="0.1" min="0" max="10" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="number" name="progressScore" placeholder="Progress Score" step="0.1" min="0" max="10" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="number" name="paymentComplianceScore" placeholder="Payment Compliance Score" step="0.1" min="0" max="10" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <input type="number" name="overallScore" placeholder="Overall Score" step="0.1" min="0" max="10" required className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500" />
            <textarea name="remarks" placeholder="Remarks (optional)" className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg col-span-2 text-white placeholder-slate-500"></textarea>
          </div>
          <button type="submit" disabled={isCreating} className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-600 disabled:bg-slate-600">
            Create
          </button>
        </form>
      )}

      <div className="space-y-2">
        {scorecards.length === 0 ? (
          <p className="text-slate-500 text-center py-4">No scorecards</p>
        ) : (
          scorecards.map((sc) => (
            <div key={sc.id} className="bg-slate-900/50 border border-slate-800 rounded-xl p-4 shadow-xl">
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  <p className="font-semibold text-white">{sc.period}</p>
                  <div className="mt-2 grid grid-cols-5 gap-2 text-sm">
                    <div>
                      <p className="text-slate-400">Quality</p>
                      <p className="font-semibold text-white">{sc.qualityScore}</p>
                    </div>
                    <div>
                      <p className="text-slate-400">Safety</p>
                      <p className="font-semibold text-white">{sc.safetyScore}</p>
                    </div>
                    <div>
                      <p className="text-slate-400">Progress</p>
                      <p className="font-semibold text-white">{sc.progressScore}</p>
                    </div>
                    <div>
                      <p className="text-slate-400">Compliance</p>
                      <p className="font-semibold text-white">{sc.paymentComplianceScore}</p>
                    </div>
                    <div>
                      <p className="text-slate-400">Overall</p>
                      <p className="font-semibold text-white">{sc.overallScore}</p>
                    </div>
                  </div>
                  {sc.remarks && <p className="text-sm text-slate-400 mt-2">{sc.remarks}</p>}
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
