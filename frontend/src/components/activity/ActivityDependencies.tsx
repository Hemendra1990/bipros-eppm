"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { activityApi } from "@/lib/api/activityApi";
import type { RelationshipResponse, RelationshipType, ActivityResponse } from "@/lib/api/activityApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";
import { Plus, Trash2, ArrowRight, ArrowLeft } from "lucide-react";
import toast from "react-hot-toast";

const RELATIONSHIP_TYPE_LABELS: Record<RelationshipType, string> = {
  FINISH_TO_START: "Finish to Start (FS)",
  FINISH_TO_FINISH: "Finish to Finish (FF)",
  START_TO_START: "Start to Start (SS)",
  START_TO_FINISH: "Start to Finish (SF)",
};

const RELATIONSHIP_TYPE_SHORT: Record<string, string> = {
  FINISH_TO_START: "FS",
  FINISH_TO_FINISH: "FF",
  START_TO_START: "SS",
  START_TO_FINISH: "SF",
};

interface ActivityDependenciesProps {
  projectId: string;
  activityId: string;
  activityName: string;
}

export function ActivityDependencies({ projectId, activityId, activityName }: ActivityDependenciesProps) {
  const queryClient = useQueryClient();
  const [showAddForm, setShowAddForm] = useState(false);
  const [addDirection, setAddDirection] = useState<"predecessor" | "successor">("predecessor");

  const { data: predecessorsData, isLoading: isLoadingPred } = useQuery({
    queryKey: ["predecessors", projectId, activityId],
    queryFn: () => activityApi.getPredecessors(projectId, activityId),
  });

  const { data: successorsData, isLoading: isLoadingSucc } = useQuery({
    queryKey: ["successors", projectId, activityId],
    queryFn: () => activityApi.getSuccessors(projectId, activityId),
  });

  const { data: allActivitiesData } = useQuery({
    queryKey: ["activities", projectId, "all"],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
  });

  const allActivities = allActivitiesData?.data?.content ?? [];
  const predecessors = predecessorsData?.data ?? [];
  const successors = successorsData?.data ?? [];

  const deleteMutation = useMutation({
    mutationFn: (relationshipId: string) =>
      activityApi.deleteRelationship(projectId, relationshipId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["predecessors", projectId, activityId] });
      queryClient.invalidateQueries({ queryKey: ["successors", projectId, activityId] });
      queryClient.invalidateQueries({ queryKey: ["relationships", projectId] });
      toast.success("Dependency removed");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to remove dependency"));
    },
  });

  const getActivityName = (id: string) => {
    const act = allActivities.find((a) => a.id === id);
    return act ? `${act.code} - ${act.name}` : id;
  };

  const handleOpenAdd = (direction: "predecessor" | "successor") => {
    setAddDirection(direction);
    setShowAddForm(true);
  };

  return (
    <div className="space-y-6">
      <h3 className="text-lg font-semibold text-white">Dependencies</h3>

      {/* Predecessors */}
      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-4">
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ArrowLeft className="h-4 w-4 text-blue-400" />
            <h4 className="text-sm font-medium text-slate-300">
              Predecessors ({predecessors.length})
            </h4>
          </div>
          <button
            onClick={() => handleOpenAdd("predecessor")}
            className="flex items-center gap-1 rounded-md bg-blue-600/20 px-3 py-1.5 text-xs font-medium text-blue-400 hover:bg-blue-600/30"
          >
            <Plus className="h-3 w-3" />
            Add
          </button>
        </div>

        {isLoadingPred ? (
          <div className="text-sm text-slate-500">Loading...</div>
        ) : predecessors.length === 0 ? (
          <div className="text-sm text-slate-500">No predecessors. This activity can start independently.</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-2">Activity</th>
                <th className="pb-2">Type</th>
                <th className="pb-2">Lag</th>
                <th className="pb-2 w-16"></th>
              </tr>
            </thead>
            <tbody>
              {predecessors.map((rel) => (
                <tr key={rel.id} className="border-b border-slate-800/50">
                  <td className="py-2 text-white">{getActivityName(rel.predecessorActivityId)}</td>
                  <td className="py-2 text-slate-300">{RELATIONSHIP_TYPE_SHORT[rel.relationshipType] ?? rel.relationshipType}</td>
                  <td className="py-2 text-slate-300">{rel.lag ?? 0}d</td>
                  <td className="py-2">
                    <button
                      onClick={() => deleteMutation.mutate(rel.id)}
                      disabled={deleteMutation.isPending}
                      className="text-red-400 hover:text-red-300"
                      title="Remove dependency"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Successors */}
      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-4">
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ArrowRight className="h-4 w-4 text-green-400" />
            <h4 className="text-sm font-medium text-slate-300">
              Successors ({successors.length})
            </h4>
          </div>
          <button
            onClick={() => handleOpenAdd("successor")}
            className="flex items-center gap-1 rounded-md bg-green-600/20 px-3 py-1.5 text-xs font-medium text-green-400 hover:bg-green-600/30"
          >
            <Plus className="h-3 w-3" />
            Add
          </button>
        </div>

        {isLoadingSucc ? (
          <div className="text-sm text-slate-500">Loading...</div>
        ) : successors.length === 0 ? (
          <div className="text-sm text-slate-500">No successors. No activities depend on this one.</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-2">Activity</th>
                <th className="pb-2">Type</th>
                <th className="pb-2">Lag</th>
                <th className="pb-2 w-16"></th>
              </tr>
            </thead>
            <tbody>
              {successors.map((rel) => (
                <tr key={rel.id} className="border-b border-slate-800/50">
                  <td className="py-2 text-white">{getActivityName(rel.successorActivityId)}</td>
                  <td className="py-2 text-slate-300">{RELATIONSHIP_TYPE_SHORT[rel.relationshipType] ?? rel.relationshipType}</td>
                  <td className="py-2 text-slate-300">{rel.lag ?? 0}d</td>
                  <td className="py-2">
                    <button
                      onClick={() => deleteMutation.mutate(rel.id)}
                      disabled={deleteMutation.isPending}
                      className="text-red-400 hover:text-red-300"
                      title="Remove dependency"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Add Dependency Form */}
      {showAddForm && (
        <AddDependencyForm
          projectId={projectId}
          activityId={activityId}
          activityName={activityName}
          direction={addDirection}
          allActivities={allActivities}
          existingPredecessorIds={predecessors.map((r) => r.predecessorActivityId)}
          existingSuccessorIds={successors.map((r) => r.successorActivityId)}
          onClose={() => setShowAddForm(false)}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ["predecessors", projectId, activityId] });
            queryClient.invalidateQueries({ queryKey: ["successors", projectId, activityId] });
            queryClient.invalidateQueries({ queryKey: ["relationships", projectId] });
            setShowAddForm(false);
          }}
        />
      )}
    </div>
  );
}

interface AddDependencyFormProps {
  projectId: string;
  activityId: string;
  activityName: string;
  direction: "predecessor" | "successor";
  allActivities: ActivityResponse[];
  existingPredecessorIds: string[];
  existingSuccessorIds: string[];
  onClose: () => void;
  onSuccess: () => void;
}

function AddDependencyForm({
  projectId,
  activityId,
  direction,
  allActivities,
  existingPredecessorIds,
  existingSuccessorIds,
  onClose,
  onSuccess,
}: AddDependencyFormProps) {
  const [selectedActivityId, setSelectedActivityId] = useState("");
  const [relationshipType, setRelationshipType] = useState<RelationshipType>("FINISH_TO_START");
  const [lag, setLag] = useState(0);
  const [error, setError] = useState("");

  // Filter out current activity and already-linked activities
  const excludeIds = new Set([
    activityId,
    ...(direction === "predecessor" ? existingPredecessorIds : existingSuccessorIds),
  ]);

  const availableActivities = allActivities
    .filter((a) => !excludeIds.has(a.id))
    .map((a) => ({ value: a.id, label: `${a.code} - ${a.name}` }));

  const createMutation = useMutation({
    mutationFn: () => {
      const data = direction === "predecessor"
        ? { predecessorActivityId: selectedActivityId, successorActivityId: activityId, relationshipType, lag }
        : { predecessorActivityId: activityId, successorActivityId: selectedActivityId, relationshipType, lag };
      return activityApi.createRelationship(projectId, data);
    },
    onSuccess: () => {
      toast.success("Dependency added");
      onSuccess();
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to add dependency");
      setError(msg);
      toast.error(msg);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!selectedActivityId) {
      setError("Please select an activity");
      return;
    }
    createMutation.mutate();
  };

  return (
    <div className="rounded-lg border border-blue-800/50 bg-blue-900/10 p-4">
      <h4 className="mb-3 text-sm font-medium text-white">
        Add {direction === "predecessor" ? "Predecessor" : "Successor"}
      </h4>

      {error && (
        <div className="mb-3 rounded-md bg-red-500/10 p-2 text-xs text-red-400">{error}</div>
      )}

      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className="mb-1 block text-xs text-slate-400">Activity</label>
          <SearchableSelect
            options={availableActivities}
            value={selectedActivityId}
            onChange={(val) => {
              setSelectedActivityId(val);
              if (error) setError("");
            }}
            placeholder="Search activities..."
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs text-slate-400">Relationship Type</label>
            <select
              value={relationshipType}
              onChange={(e) => setRelationshipType(e.target.value as RelationshipType)}
              className="w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none"
            >
              {(Object.entries(RELATIONSHIP_TYPE_LABELS) as [RelationshipType, string][]).map(
                ([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                )
              )}
            </select>
          </div>

          <div>
            <label className="mb-1 block text-xs text-slate-400">Lag (days)</label>
            <input
              type="number"
              value={lag}
              onChange={(e) => setLag(parseFloat(e.target.value) || 0)}
              min="0"
              step="1"
              className="w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none"
            />
          </div>
        </div>

        <div className="flex gap-2 pt-1">
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="rounded-md bg-blue-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-blue-500 disabled:bg-slate-600"
          >
            {createMutation.isPending ? "Adding..." : "Add Dependency"}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md bg-slate-700/50 px-4 py-1.5 text-xs font-medium text-slate-300 hover:bg-slate-700"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
