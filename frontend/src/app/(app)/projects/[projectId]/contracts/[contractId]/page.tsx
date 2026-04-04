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
    return <div className="p-6 text-center text-gray-500">Loading contract...</div>;
  }

  if (!contractData) {
    return <div className="p-6 text-center text-red-500">Contract not found</div>;
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
        <h1 className="text-2xl font-bold text-gray-900">{contractData.contractNumber}</h1>
        <p className="text-sm text-gray-600 mt-1">Contractor: {contractData.contractorName}</p>
        <div className="mt-4 grid grid-cols-4 gap-4">
          <div className="bg-gray-50 rounded-lg p-3">
            <p className="text-xs text-gray-600">Contract Value</p>
            <p className="text-lg font-semibold text-gray-900">{contractData.contractValue.toLocaleString()}</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-3">
            <p className="text-xs text-gray-600">Status</p>
            <p className="text-lg font-semibold text-gray-900">{contractData.status}</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-3">
            <p className="text-xs text-gray-600">Type</p>
            <p className="text-lg font-semibold text-gray-900">{contractData.contractType}</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-3">
            <p className="text-xs text-gray-600">Duration</p>
            <p className="text-lg font-semibold text-gray-900">{contractData.dlpMonths} months</p>
          </div>
        </div>
      </div>

      <div className="border-b border-gray-200">
        <nav className="flex gap-8" aria-label="Tabs">
          {tabs.map((t) => (
            <a
              key={t.id}
              href={`?tab=${t.id}`}
              className={`px-1 py-4 text-sm font-medium border-b-2 transition-colors ${
                activeTab === t.id
                  ? "border-blue-500 text-blue-600"
                  : "border-transparent text-gray-600 hover:text-gray-900 hover:border-gray-300"
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
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
      >
        {showForm ? "Cancel" : "Add Milestone"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <input type="text" name="milestoneCode" placeholder="Code" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="text" name="milestoneName" placeholder="Name" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="date" name="targetDate" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="date" name="actualDate" placeholder="Actual Date (optional)" className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="number" name="paymentPercentage" placeholder="Payment %" step="0.01" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="number" name="amount" placeholder="Amount" step="0.01" required className="px-3 py-2 border border-gray-300 rounded-lg" />
          </div>
          <button type="submit" disabled={isCreating} className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-400">
            Create
          </button>
        </form>
      )}

      <div className="space-y-2">
        {milestones.length === 0 ? (
          <p className="text-gray-500 text-center py-4">No milestones</p>
        ) : (
          milestones.map((m) => (
            <div key={m.id} className="bg-white border border-gray-200 rounded-lg p-4">
              <div className="flex justify-between items-start">
                <div>
                  <p className="font-semibold text-gray-900">{m.milestoneName}</p>
                  <p className="text-sm text-gray-600">Target: {new Date(m.targetDate).toLocaleDateString()}</p>
                  <p className="text-sm text-gray-600">Payment: {m.paymentPercentage}% | Amount: {m.amount.toLocaleString()}</p>
                  <span className="inline-block mt-2 px-2 py-1 text-xs font-semibold rounded-full bg-gray-100 text-gray-800">{m.status}</span>
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
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
      >
        {showForm ? "Cancel" : "Add Variation Order"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <input type="text" name="voNumber" placeholder="VO Number" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="number" name="voValue" placeholder="Value" step="0.01" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <textarea name="description" placeholder="Description" required className="px-3 py-2 border border-gray-300 rounded-lg col-span-2"></textarea>
            <textarea name="justification" placeholder="Justification" required className="px-3 py-2 border border-gray-300 rounded-lg col-span-2"></textarea>
            <input type="number" name="impactOnBudget" placeholder="Budget Impact" step="0.01" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="number" name="impactOnScheduleDays" placeholder="Schedule Impact (days)" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="text" name="approvedBy" placeholder="Approved By (optional)" className="px-3 py-2 border border-gray-300 rounded-lg col-span-2" />
          </div>
          <button type="submit" disabled={isCreating} className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-400">
            Create
          </button>
        </form>
      )}

      <div className="space-y-2">
        {variationOrders.length === 0 ? (
          <p className="text-gray-500 text-center py-4">No variation orders</p>
        ) : (
          variationOrders.map((vo) => (
            <div key={vo.id} className="bg-white border border-gray-200 rounded-lg p-4">
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  <p className="font-semibold text-gray-900">{vo.voNumber}</p>
                  <p className="text-sm text-gray-600">{vo.description}</p>
                  <p className="text-sm text-gray-600">Value: {vo.voValue.toLocaleString()} | Budget Impact: {vo.impactOnBudget.toLocaleString()}</p>
                  <p className="text-sm text-gray-600">Schedule Impact: {vo.impactOnScheduleDays} days</p>
                  <span className="inline-block mt-2 px-2 py-1 text-xs font-semibold rounded-full bg-gray-100 text-gray-800">{vo.status}</span>
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
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
      >
        {showForm ? "Cancel" : "Add Bond"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <select name="bondType" required className="px-3 py-2 border border-gray-300 rounded-lg">
              <option value="">Select Type</option>
              <option value="PERFORMANCE_GUARANTEE">Performance Guarantee</option>
              <option value="EMD">EMD</option>
              <option value="ADVANCE_GUARANTEE">Advance Guarantee</option>
              <option value="RETENTION">Retention</option>
            </select>
            <input type="number" name="bondValue" placeholder="Value" step="0.01" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="text" name="bankName" placeholder="Bank Name" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="date" name="issueDate" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="date" name="expiryDate" required className="px-3 py-2 border border-gray-300 rounded-lg" />
          </div>
          <button type="submit" disabled={isCreating} className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-400">
            Create
          </button>
        </form>
      )}

      <div className="space-y-2">
        {bonds.length === 0 ? (
          <p className="text-gray-500 text-center py-4">No bonds</p>
        ) : (
          bonds.map((bond) => (
            <div key={bond.id} className="bg-white border border-gray-200 rounded-lg p-4">
              <div className="flex justify-between items-start">
                <div>
                  <p className="font-semibold text-gray-900">{bond.bondType}</p>
                  <p className="text-sm text-gray-600">Bank: {bond.bankName}</p>
                  <p className="text-sm text-gray-600">Value: {bond.bondValue.toLocaleString()} | Valid: {new Date(bond.issueDate).toLocaleDateString()} - {new Date(bond.expiryDate).toLocaleDateString()}</p>
                  <span className="inline-block mt-2 px-2 py-1 text-xs font-semibold rounded-full bg-gray-100 text-gray-800">{bond.status}</span>
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
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
      >
        {showForm ? "Cancel" : "Add Scorecard"}
      </button>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <input type="text" name="period" placeholder="Period (e.g., Q1-2024)" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="number" name="qualityScore" placeholder="Quality Score" step="0.1" min="0" max="10" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="number" name="safetyScore" placeholder="Safety Score" step="0.1" min="0" max="10" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="number" name="progressScore" placeholder="Progress Score" step="0.1" min="0" max="10" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="number" name="paymentComplianceScore" placeholder="Payment Compliance Score" step="0.1" min="0" max="10" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <input type="number" name="overallScore" placeholder="Overall Score" step="0.1" min="0" max="10" required className="px-3 py-2 border border-gray-300 rounded-lg" />
            <textarea name="remarks" placeholder="Remarks (optional)" className="px-3 py-2 border border-gray-300 rounded-lg col-span-2"></textarea>
          </div>
          <button type="submit" disabled={isCreating} className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-400">
            Create
          </button>
        </form>
      )}

      <div className="space-y-2">
        {scorecards.length === 0 ? (
          <p className="text-gray-500 text-center py-4">No scorecards</p>
        ) : (
          scorecards.map((sc) => (
            <div key={sc.id} className="bg-white border border-gray-200 rounded-lg p-4">
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  <p className="font-semibold text-gray-900">{sc.period}</p>
                  <div className="mt-2 grid grid-cols-5 gap-2 text-sm">
                    <div>
                      <p className="text-gray-600">Quality</p>
                      <p className="font-semibold text-gray-900">{sc.qualityScore}</p>
                    </div>
                    <div>
                      <p className="text-gray-600">Safety</p>
                      <p className="font-semibold text-gray-900">{sc.safetyScore}</p>
                    </div>
                    <div>
                      <p className="text-gray-600">Progress</p>
                      <p className="font-semibold text-gray-900">{sc.progressScore}</p>
                    </div>
                    <div>
                      <p className="text-gray-600">Compliance</p>
                      <p className="font-semibold text-gray-900">{sc.paymentComplianceScore}</p>
                    </div>
                    <div>
                      <p className="text-gray-600">Overall</p>
                      <p className="font-semibold text-gray-900">{sc.overallScore}</p>
                    </div>
                  </div>
                  {sc.remarks && <p className="text-sm text-gray-600 mt-2">{sc.remarks}</p>}
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
