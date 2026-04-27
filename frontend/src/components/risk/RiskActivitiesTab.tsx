"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Plus, Trash2, Link as LinkIcon } from "lucide-react";
import { riskApi, type RiskResponse, type RiskActivityAssignment } from "@/lib/api/riskApi";
import { getErrorMessage } from "@/lib/utils/error";
import { ActivityAssignmentModal } from "./ActivityAssignmentModal";

interface Props {
  risk: RiskResponse;
  projectId: string;
  riskId: string;
}

export function RiskActivitiesTab({ risk, projectId, riskId }: Props) {
  const queryClient = useQueryClient();
  const [showAssignModal, setShowAssignModal] = useState(false);

  const { data: assignmentsData, isLoading } = useQuery({
    queryKey: ["risk-activities", projectId, riskId],
    queryFn: () => riskApi.getAssignedActivities(projectId, riskId),
  });

  const removeMutation = useMutation({
    mutationFn: (activityId: string) => riskApi.removeActivityFromRisk(projectId, riskId, activityId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-activities", projectId, riskId] });
      queryClient.invalidateQueries({ queryKey: ["risk", projectId, riskId] });
      toast.success("Activity removed from risk");
    },
    onError: (err: unknown) => toast.error(getErrorMessage(err, "Failed to remove activity")),
  });

  const assignments: RiskActivityAssignment[] = assignmentsData?.data || [];

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return "—";
    return new Date(dateStr).toLocaleDateString();
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold text-text-primary">Assigned Activities</h3>
          <p className="text-sm text-text-secondary mt-1">
            Assign schedule activities to this risk. Exposure dates will be auto-calculated from the assigned activities.
          </p>
        </div>
        <button
          onClick={() => setShowAssignModal(true)}
          className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={16} />
          Assign Activity
        </button>
      </div>

      {/* Exposure Info */}
      <div className="grid grid-cols-2 gap-4">
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <label className="text-xs font-medium text-text-secondary uppercase tracking-wide">Exposure Start</label>
          <p className="text-lg font-semibold text-text-primary mt-1">
            {formatDate(risk.exposureStartDate)}
          </p>
          <p className="text-[10px] text-text-muted">Earliest activity start date</p>
        </div>
        <div className="rounded-lg border border-border bg-surface/50 p-4">
          <label className="text-xs font-medium text-text-secondary uppercase tracking-wide">Exposure Finish</label>
          <p className="text-lg font-semibold text-text-primary mt-1">
            {formatDate(risk.exposureFinishDate)}
          </p>
          <p className="text-[10px] text-text-muted">Latest activity finish date</p>
        </div>
      </div>

      {/* Activities Table */}
      {isLoading ? (
        <div className="text-center py-8 text-text-muted">Loading activities...</div>
      ) : assignments.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-surface/30 p-12 text-center">
          <LinkIcon className="mx-auto h-12 w-12 text-text-muted mb-4" />
          <h3 className="text-lg font-semibold text-text-primary mb-2">No Activities Assigned</h3>
          <p className="text-sm text-text-secondary mb-4">
            Assign activities to this risk to auto-calculate exposure dates and costs.
          </p>
          <button
            onClick={() => setShowAssignModal(true)}
            className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
          >
            <Plus size={16} />
            Assign First Activity
          </button>
        </div>
      ) : (
        <div className="rounded-xl border border-border bg-surface/50 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-surface-hover/50">
                <th className="px-4 py-3 text-left font-medium text-text-secondary">Code</th>
                <th className="px-4 py-3 text-left font-medium text-text-secondary">Name</th>
                <th className="px-4 py-3 text-left font-medium text-text-secondary">Start Date</th>
                <th className="px-4 py-3 text-left font-medium text-text-secondary">Finish Date</th>
                <th className="px-4 py-3 text-right font-medium text-text-secondary">Actions</th>
              </tr>
            </thead>
            <tbody>
              {assignments.map((assignment) => (
                <tr key={assignment.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 font-mono text-xs">{assignment.activityCode || "—"}</td>
                  <td className="px-4 py-3">{assignment.activityName || "—"}</td>
                  <td className="px-4 py-3">{formatDate(assignment.activityStartDate)}</td>
                  <td className="px-4 py-3">{formatDate(assignment.activityFinishDate)}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => {
                        if (window.confirm("Remove this activity from the risk?")) {
                          removeMutation.mutate(assignment.activityId);
                        }
                      }}
                      disabled={removeMutation.isPending}
                      className="text-danger hover:text-danger disabled:text-text-muted"
                    >
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Assignment Modal */}
      {showAssignModal && (
        <ActivityAssignmentModal
          projectId={projectId}
          riskId={riskId}
          assignedActivityIds={assignments.map((a) => a.activityId)}
          onClose={() => setShowAssignModal(false)}
        />
      )}
    </div>
  );
}
