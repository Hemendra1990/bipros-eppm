"use client";

import { useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import toast from "react-hot-toast";
import { ArrowLeft, Trash2 } from "lucide-react";
import { Breadcrumb } from "@/components/common/Breadcrumb";
import { StatusBadge } from "@/components/common/StatusBadge";
import { SupervisorPicker } from "@/components/resources/SupervisorPicker";
import { SimpleTable } from "@/components/common/SimpleTable";
import type { ColumnDef } from "@tanstack/react-table";
import {
  crewApi,
  type CrewMemberRequest,
  type CrewMemberResponse,
  type CrewRequest,
} from "@/lib/api/crewApi";
import { getErrorMessage } from "@/lib/utils/error";

const inputCls =
  "w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-accent focus:outline-none";
const labelCls = "block text-sm font-medium text-text-secondary mb-1";

export default function CrewDetailPage() {
  const params = useParams<{ crewId: string }>();
  const crewId = params.crewId;
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ["crew", crewId],
    queryFn: () => crewApi.get(crewId),
    enabled: !!crewId,
  });
  const crew = data?.data;
  const members: CrewMemberResponse[] = useMemo(
    () => crew?.members ?? [],
    [crew],
  );

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ["crew", crewId] });

  // ─── Crew details (inline edit) ──────────────────────────────────────────
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [crewLeadResourceId, setCrewLeadResourceId] = useState<string | null>(null);

  const startEdit = () => {
    if (!crew) return;
    setName(crew.name ?? "");
    setDescription(crew.description ?? "");
    setCrewLeadResourceId(crew.crewLeadResourceId);
    setEditing(true);
  };

  const updateMutation = useMutation({
    mutationFn: (req: CrewRequest) => crewApi.update(crewId, req),
    onSuccess: () => {
      toast.success("Crew updated");
      setEditing(false);
      invalidate();
    },
    onError: (err) => toast.error(getErrorMessage(err, "Failed to update crew")),
  });

  const removeMutation = useMutation({
    mutationFn: () => crewApi.remove(crewId),
    onSuccess: () => {
      toast.success("Crew deleted");
      router.push("/resources/crews");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Failed to delete crew")),
  });

  const saveEdit = () => {
    if (!crew || !crewLeadResourceId || !name.trim()) return;
    updateMutation.mutate({
      code: crew.code,
      name: name.trim(),
      description: description.trim() || null,
      crewLeadResourceId,
      projectId: crew.projectId,
      status: crew.status,
      sortOrder: crew.sortOrder,
    });
  };

  // ─── Add member ──────────────────────────────────────────────────────────
  const [newMemberResourceId, setNewMemberResourceId] = useState<string | null>(null);
  const [newMemberRole, setNewMemberRole] = useState("");

  const addMemberMutation = useMutation({
    mutationFn: (req: CrewMemberRequest) => crewApi.addMember(crewId, req),
    onSuccess: () => {
      toast.success("Member added");
      setNewMemberResourceId(null);
      setNewMemberRole("");
      invalidate();
    },
    onError: (err) => toast.error(getErrorMessage(err, "Failed to add member")),
  });

  const removeMemberMutation = useMutation({
    mutationFn: (memberId: string) => crewApi.removeMember(crewId, memberId),
    onSuccess: () => {
      toast.success("Member removed");
      invalidate();
    },
    onError: (err) => toast.error(getErrorMessage(err, "Failed to remove member")),
  });

  if (isLoading) {
    return <p className="p-6 text-sm text-text-muted">Loading crew…</p>;
  }
  if (error || !crew) {
    return (
      <div className="p-6">
        <p className="text-sm text-danger">Crew not found.</p>
        <button
          type="button"
          onClick={() => router.push("/resources/crews")}
          className="mt-4 rounded-md bg-accent px-4 py-2 text-sm text-text-primary hover:bg-accent-hover"
        >
          ← Back to Crews
        </button>
      </div>
    );
  }

  const memberResourceIds = members.map((m) => m.resourceId);

  const memberColumns = useMemo<ColumnDef<CrewMemberResponse>[]>(
    () => [
      {
        accessorKey: "resourceCode",
        header: "Code",
        cell: (info) => {
          const row = info.row.original;
          return (
            <Link
              href={`/resources/${row.resourceId}`}
              className="font-mono text-accent hover:underline"
            >
              {row.resourceCode ?? row.resourceId.slice(0, 8)}
            </Link>
          );
        },
      },
      {
        accessorKey: "resourceName",
        header: "Name",
        cell: (info) => (
          <span className="text-text-primary">
            {(info.getValue() as string) ?? "—"}
          </span>
        ),
      },
      {
        accessorKey: "resourceTypeCode",
        header: "Type",
        cell: (info) => (
          <span className="text-text-secondary">
            {(info.getValue() as string) ?? "—"}
          </span>
        ),
      },
      {
        accessorKey: "roleInCrew",
        header: "Role",
        cell: (info) => {
          const row = info.row.original;
          const isLead = row.resourceId === crew?.crewLeadResourceId;
          return (
            <span className="text-text-secondary">
              {row.roleInCrew ?? (isLead ? "LEAD" : "—")}
            </span>
          );
        },
      },
      {
        accessorKey: "startDate",
        header: "Start",
        cell: (info) => (
          <span className="text-text-secondary">
            {(info.getValue() as string) ?? "—"}
          </span>
        ),
      },
      {
        accessorKey: "endDate",
        header: "End",
        cell: (info) => (
          <span className="text-text-secondary">
            {(info.getValue() as string) ?? "—"}
          </span>
        ),
      },
      {
        id: "actions",
        header: "",
        cell: (info) => {
          const row = info.row.original;
          const isLead = row.resourceId === crew?.crewLeadResourceId;
          if (isLead) return null;
          return (
            <button
              type="button"
              onClick={() => {
                if (window.confirm("Remove this member?"))
                  removeMemberMutation.mutate(row.id);
              }}
              disabled={removeMemberMutation.isPending}
              className="text-text-secondary hover:text-danger disabled:text-text-muted"
              title="Remove member"
            >
              <Trash2 size={14} />
            </button>
          );
        },
      },
    ],
    [crew?.crewLeadResourceId, removeMemberMutation]
  );

  return (
    <div>
      <Breadcrumb
        items={[
          { label: "Resources", href: "/resources" },
          { label: "Crews", href: "/resources/crews" },
          { label: crew.code ?? crew.name, href: `/resources/crews/${crew.id}`, active: true },
        ]}
      />

      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <button
            type="button"
            onClick={() => router.push("/resources/crews")}
            className="mb-2 inline-flex items-center gap-1 text-xs text-text-secondary hover:text-accent"
          >
            <ArrowLeft size={12} /> Back
          </button>
          <h1 className="text-3xl font-bold text-text-primary">{crew.name}</h1>
          <p className="mt-1 text-sm text-text-secondary">
            <span className="font-mono">{crew.code}</span>
            {" · "}
            <span>Lead: </span>
            <Link
              href={`/resources/${crew.crewLeadResourceId}`}
              className="text-accent hover:underline"
            >
              {crew.crewLeadName ?? crew.crewLeadResourceId.slice(0, 8)}
            </Link>
          </p>
          {crew.description && (
            <p className="mt-2 max-w-2xl text-sm text-text-secondary">{crew.description}</p>
          )}
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge status={crew.status} />
          {!editing && (
            <button
              type="button"
              onClick={startEdit}
              className="rounded-md border border-border bg-surface px-3 py-1.5 text-sm text-text-primary hover:bg-surface-hover"
            >
              Edit
            </button>
          )}
          <button
            type="button"
            onClick={() => {
              if (window.confirm("Delete this crew?")) removeMutation.mutate();
            }}
            disabled={removeMutation.isPending}
            className="rounded-md border border-border bg-surface px-3 py-1.5 text-sm text-text-secondary hover:bg-danger/10 hover:text-danger disabled:opacity-50"
          >
            Delete
          </button>
        </div>
      </div>

      {editing && (
        <section className="mb-6 rounded-xl border border-border bg-surface/50 p-6">
          <h2 className="mb-4 text-base font-semibold text-text-primary">
            Edit details
          </h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <label className={labelCls}>Name</label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className={inputCls}
              />
            </div>
            <div>
              <label className={labelCls}>Crew Lead (Labor)</label>
              <SupervisorPicker
                value={crewLeadResourceId}
                onChange={(id) => setCrewLeadResourceId(id)}
                typeCode="LABOR"
              />
            </div>
            <div className="md:col-span-2">
              <label className={labelCls}>Description</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                className={inputCls}
              />
            </div>
          </div>
          <div className="mt-4 flex gap-3">
            <button
              type="button"
              onClick={saveEdit}
              disabled={!name.trim() || !crewLeadResourceId || updateMutation.isPending}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
            >
              {updateMutation.isPending ? "Saving…" : "Save"}
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className="rounded-md border border-border bg-surface px-4 py-2 text-sm text-text-primary hover:bg-surface-hover"
            >
              Cancel
            </button>
          </div>
        </section>
      )}

      <section className="mb-6">
        <h2 className="mb-3 text-lg font-semibold text-text-primary">
          Members ({members.length})
        </h2>
        <SimpleTable
          columns={memberColumns}
          data={members}
          sortable={false}
          emptyMessage="No members."
        />
      </section>

      <section className="mb-6 rounded-xl border border-border bg-surface/50 p-6">
        <h2 className="mb-3 text-base font-semibold text-text-primary">Add member</h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <div className="md:col-span-2">
            <label className={labelCls}>Resource</label>
            <SupervisorPicker
              value={newMemberResourceId}
              onChange={(id) => setNewMemberResourceId(id)}
              excludeId={crew.crewLeadResourceId}
              excludeIds={memberResourceIds}
              placeholder="Pick a resource..."
            />
          </div>
          <div>
            <label className={labelCls}>Role in crew</label>
            <input
              type="text"
              value={newMemberRole}
              onChange={(e) => setNewMemberRole(e.target.value)}
              placeholder="e.g. Operator"
              className={inputCls}
            />
          </div>
        </div>
        <div className="mt-3">
          <button
            type="button"
            onClick={() => {
              if (!newMemberResourceId) return;
              addMemberMutation.mutate({
                resourceId: newMemberResourceId,
                roleInCrew: newMemberRole.trim() || null,
              });
            }}
            disabled={!newMemberResourceId || addMemberMutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
          >
            {addMemberMutation.isPending ? "Adding…" : "Add member"}
          </button>
        </div>
      </section>
    </div>
  );
}
