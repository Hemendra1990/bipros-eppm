"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { activityApi, type RelationshipResponse, type RelationshipType } from "@/lib/api/activityApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { PageHeader } from "@/components/common/PageHeader";
import { getErrorMessage } from "@/lib/utils/error";
import { Plus, Pencil, Trash2, ArrowRight, X } from "lucide-react";
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

export default function RelationshipsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();

  const [showAddModal, setShowAddModal] = useState(false);
  const [editingRel, setEditingRel] = useState<RelationshipResponse | null>(null);

  const { data: relationshipsData, isLoading: isLoadingRels } = useQuery({
    queryKey: ["relationships", projectId],
    queryFn: () => activityApi.getRelationships(projectId),
  });

  const { data: activitiesData, isLoading: isLoadingActs } = useQuery({
    queryKey: ["activities", projectId, "all"],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
  });

  const relationships = relationshipsData?.data ?? [];
  const activities = activitiesData?.data?.content ?? [];

  const activityMap = new Map(activities.map((a) => [a.id, a]));

  const getActivityLabel = (id: string) => {
    const act = activityMap.get(id);
    return act ? `${act.code} — ${act.name}` : id;
  };

  const deleteMutation = useMutation({
    mutationFn: (relationshipId: string) =>
      activityApi.deleteRelationship(projectId, relationshipId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["relationships", projectId] });
      toast.success("Relationship deleted");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to delete relationship"));
    },
  });

  const handleDelete = (rel: RelationshipResponse) => {
    if (confirm(`Delete relationship: ${getActivityLabel(rel.predecessorActivityId)} → ${getActivityLabel(rel.successorActivityId)}?`)) {
      deleteMutation.mutate(rel.id);
    }
  };

  const columns: ColumnDef<RelationshipResponse>[] = [
    {
      key: "predecessorActivityId",
      label: "Predecessor",
      sortable: true,
      render: (_v, row) => (
        <span className="text-text-primary font-medium">{getActivityLabel(row.predecessorActivityId)}</span>
      ),
    },
    {
      key: "relationshipType",
      label: "Type",
      sortable: true,
      render: (_v, row) => (
        <span className="inline-flex items-center rounded-md bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent">
          {RELATIONSHIP_TYPE_SHORT[row.relationshipType] ?? row.relationshipType}
        </span>
      ),
    },
    {
      key: "lag",
      label: "Lag",
      sortable: true,
      render: (v) => <span className="font-mono text-text-secondary">{String(v)}d</span>,
    },
    {
      key: "successorActivityId",
      label: "Successor",
      sortable: true,
      render: (_v, row) => (
        <span className="text-text-primary font-medium">{getActivityLabel(row.successorActivityId)}</span>
      ),
    },
    {
      key: "actions",
      label: "",
      render: (_v, row) => (
        <div className="flex items-center gap-2">
          <button
            onClick={(e) => {
              e.stopPropagation();
              setEditingRel(row);
            }}
            className="text-text-secondary hover:text-accent transition-colors"
            title="Edit"
          >
            <Pencil size={14} />
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
              handleDelete(row);
            }}
            disabled={deleteMutation.isPending}
            className="text-text-secondary hover:text-danger transition-colors disabled:opacity-50"
            title="Delete"
          >
            <Trash2 size={14} />
          </button>
        </div>
      ),
    },
  ];

  const isLoading = isLoadingRels || isLoadingActs;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Activity Relationships"
        description={`Manage predecessor and successor dependencies for this project. ${relationships.length} relationship(s) defined.`}
        actions={
          <button
            onClick={() => setShowAddModal(true)}
            className="flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover transition-colors"
          >
            <Plus size={16} />
            Add Relationship
          </button>
        }
      />

      {isLoading ? (
        <div className="text-center text-text-muted py-12">Loading relationships...</div>
      ) : (
        <DataTable
          columns={columns}
          data={relationships}
          rowKey="id"
          pageSize={25}
          searchable
          searchPlaceholder="Search by activity code or name..."
        />
      )}

      {/* Add Modal */}
      {showAddModal && (
        <RelationshipModal
          projectId={projectId}
          activities={activities}
          onClose={() => setShowAddModal(false)}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ["relationships", projectId] });
            setShowAddModal(false);
          }}
        />
      )}

      {/* Edit Modal */}
      {editingRel && (
        <EditRelationshipModal
          projectId={projectId}
          relationship={editingRel}
          activities={activities}
          onClose={() => setEditingRel(null)}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ["relationships", projectId] });
            setEditingRel(null);
          }}
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Add Relationship Modal                                              */
/* ------------------------------------------------------------------ */

function RelationshipModal({
  projectId,
  activities,
  onClose,
  onSuccess,
}: {
  projectId: string;
  activities: Array<{ id: string; code: string; name: string }>;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [predecessorId, setPredecessorId] = useState("");
  const [successorId, setSuccessorId] = useState("");
  const [relationshipType, setRelationshipType] = useState<RelationshipType>("FINISH_TO_START");
  const [lag, setLag] = useState<number | "">(0);
  const [error, setError] = useState("");

  const activityOptions = activities.map((a) => ({
    value: a.id,
    label: `${a.code} — ${a.name}`,
  }));

  const createMutation = useMutation({
    mutationFn: () =>
      activityApi.createRelationship(projectId, {
        predecessorActivityId: predecessorId,
        successorActivityId: successorId,
        relationshipType,
        lag: lag === "" ? 0 : lag,
      }),
    onSuccess: () => {
      toast.success("Relationship created");
      onSuccess();
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to create relationship");
      setError(msg);
      toast.error(msg);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!predecessorId || !successorId) {
      setError("Please select both predecessor and successor activities");
      return;
    }
    if (predecessorId === successorId) {
      setError("An activity cannot be related to itself");
      return;
    }
    createMutation.mutate();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-full max-w-lg rounded-lg border border-border bg-surface p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-text-primary">Add Relationship</h3>
          <button onClick={onClose} className="text-text-secondary hover:text-text-primary">
            <X size={18} />
          </button>
        </div>

        {error && (
          <div className="mb-4 rounded-md bg-danger/10 p-3 text-sm text-danger">{error}</div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-text-secondary">Predecessor</label>
            <SearchableSelect
              options={activityOptions}
              value={predecessorId}
              onChange={(val) => {
                setPredecessorId(val);
                if (error) setError("");
              }}
              placeholder="Search activities..."
            />
          </div>

          <div className="flex items-center justify-center text-text-muted">
            <ArrowRight size={20} />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-text-secondary">Successor</label>
            <SearchableSelect
              options={activityOptions}
              value={successorId}
              onChange={(val) => {
                setSuccessorId(val);
                if (error) setError("");
              }}
              placeholder="Search activities..."
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-text-secondary">Relationship Type</label>
              <select
                value={relationshipType}
                onChange={(e) => setRelationshipType(e.target.value as RelationshipType)}
                className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
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
              <label className="mb-1 block text-sm font-medium text-text-secondary">Lag (days)</label>
              <input
                type="number"
                value={lag}
                onChange={(e) => setLag(e.target.value === "" ? "" : parseFloat(e.target.value))}
                min="0"
                step="1"
                className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
              />
            </div>
          </div>

          <div className="flex gap-3 pt-2">
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border transition-colors"
            >
              {createMutation.isPending ? "Creating..." : "Create Relationship"}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="rounded-md bg-surface-active/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-active transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Edit Relationship Modal                                             */
/* ------------------------------------------------------------------ */

function EditRelationshipModal({
  projectId,
  relationship,
  activities,
  onClose,
  onSuccess,
}: {
  projectId: string;
  relationship: RelationshipResponse;
  activities: Array<{ id: string; code: string; name: string }>;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [relationshipType, setRelationshipType] = useState<RelationshipType>(relationship.relationshipType);
  const [lag, setLag] = useState<number | "">(relationship.lag ?? 0);
  const [error, setError] = useState("");

  const activityMap = new Map(activities.map((a) => [a.id, a]));
  const getActivityLabel = (id: string) => {
    const act = activityMap.get(id);
    return act ? `${act.code} — ${act.name}` : id;
  };

  const updateMutation = useMutation({
    mutationFn: () =>
      activityApi.updateRelationship(projectId, relationship.id, {
        relationshipType,
        lag: lag === "" ? 0 : lag,
      }),
    onSuccess: () => {
      toast.success("Relationship updated");
      onSuccess();
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to update relationship");
      setError(msg);
      toast.error(msg);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    updateMutation.mutate();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-full max-w-md rounded-lg border border-border bg-surface p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-text-primary">Edit Relationship</h3>
          <button onClick={onClose} className="text-text-secondary hover:text-text-primary">
            <X size={18} />
          </button>
        </div>

        {error && (
          <div className="mb-4 rounded-md bg-danger/10 p-3 text-sm text-danger">{error}</div>
        )}

        <div className="mb-4 rounded-md bg-surface-hover/50 p-3 text-sm text-text-secondary">
          <span className="text-text-primary font-medium">{getActivityLabel(relationship.predecessorActivityId)}</span>
          {" → "}
          <span className="text-text-primary font-medium">{getActivityLabel(relationship.successorActivityId)}</span>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-text-secondary">Relationship Type</label>
            <select
              value={relationshipType}
              onChange={(e) => setRelationshipType(e.target.value as RelationshipType)}
              className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
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
            <label className="mb-1 block text-sm font-medium text-text-secondary">Lag (days)</label>
            <input
              type="number"
              value={lag}
              onChange={(e) => setLag(e.target.value === "" ? "" : parseFloat(e.target.value))}
              min="0"
              step="1"
              className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
            />
          </div>

          <div className="flex gap-3 pt-2">
            <button
              type="submit"
              disabled={updateMutation.isPending}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border transition-colors"
            >
              {updateMutation.isPending ? "Saving..." : "Save Changes"}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="rounded-md bg-surface-active/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-active transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
