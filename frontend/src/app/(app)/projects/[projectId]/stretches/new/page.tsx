"use client";

import { useRouter, useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { stretchApi } from "@/lib/api/stretchApi";
import { userApi } from "@/lib/api/userApi";
import { PageHeader } from "@/components/common/PageHeader";
import type { CreateStretchRequest, StretchStatus } from "@/lib/types";

const STATUS_OPTIONS: StretchStatus[] = ["NOT_STARTED", "ACTIVE", "COMPLETE", "SNAGGING"];

/** Convert "145+000" ↔ 145000 metres. */
function parseChainage(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const match = trimmed.match(/^(\d+)\+(\d+)$/);
  if (match) {
    return parseInt(match[1], 10) * 1000 + parseInt(match[2], 10);
  }
  const plain = parseInt(trimmed, 10);
  return Number.isNaN(plain) ? null : plain;
}

export default function NewStretchPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const queryClient = useQueryClient();

  const [stretchCode, setStretchCode] = useState("");
  const [name, setName] = useState("");
  const [fromChainage, setFromChainage] = useState("");
  const [toChainage, setToChainage] = useState("");
  const [assignedSupervisorId, setAssignedSupervisorId] = useState<string>("");
  const [packageCode, setPackageCode] = useState("");
  const [status, setStatus] = useState<StretchStatus>("NOT_STARTED");
  const [milestoneName, setMilestoneName] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  const { data: usersData } = useQuery({
    queryKey: ["users", "supervisors"],
    queryFn: () => userApi.listUsers(0, 200),
  });

  const mutation = useMutation({
    mutationFn: (body: CreateStretchRequest) => stretchApi.create(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["stretches", projectId] });
      router.push(`/projects/${projectId}/stretches`);
    },
    onError: (err: { response?: { data?: { error?: { message?: string } } } }) => {
      setErrors({
        _form:
          err.response?.data?.error?.message ??
          "Unable to create stretch. Check the values and try again.",
      });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const fromM = parseChainage(fromChainage);
    const toM = parseChainage(toChainage);
    const nextErrors: Record<string, string> = {};
    if (fromM == null) nextErrors.fromChainage = "Enter as km+m (e.g. 145+000)";
    if (toM == null) nextErrors.toChainage = "Enter as km+m (e.g. 149+000)";
    if (fromM != null && toM != null && toM <= fromM) {
      nextErrors.toChainage = "To-chainage must be greater than From-chainage";
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    mutation.mutate({
      stretchCode: stretchCode.trim() || undefined,
      name: name.trim() || undefined,
      fromChainageM: fromM!,
      toChainageM: toM!,
      assignedSupervisorId: assignedSupervisorId || undefined,
      packageCode: packageCode.trim() || undefined,
      status,
      milestoneName: milestoneName.trim() || undefined,
      targetDate: targetDate || undefined,
    });
  };

  const users = usersData?.data?.content ?? [];

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="New Stretch"
        description="Subdivide the project corridor and assign a supervisor."
      />

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border border-border bg-surface p-6">
        {errors._form && (
          <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {errors._form}
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Stretch ID (optional)</label>
            <input
              value={stretchCode}
              onChange={(e) => setStretchCode(e.target.value)}
              placeholder="STR-NNN (auto)"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Name</label>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">From Chainage (km+m) *</label>
            <input
              value={fromChainage}
              onChange={(e) => setFromChainage(e.target.value)}
              placeholder="145+000"
              required
              className={`mt-1 block w-full rounded-md border bg-surface-hover px-3 py-2 text-text-primary focus:outline-none ${
                errors.fromChainage ? "border-danger" : "border-border focus:border-accent"
              }`}
            />
            {errors.fromChainage && <p className="mt-1 text-xs text-danger">{errors.fromChainage}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">To Chainage (km+m) *</label>
            <input
              value={toChainage}
              onChange={(e) => setToChainage(e.target.value)}
              placeholder="149+000"
              required
              className={`mt-1 block w-full rounded-md border bg-surface-hover px-3 py-2 text-text-primary focus:outline-none ${
                errors.toChainage ? "border-danger" : "border-border focus:border-accent"
              }`}
            />
            {errors.toChainage && <p className="mt-1 text-xs text-danger">{errors.toChainage}</p>}
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Assigned Supervisor</label>
            <select
              value={assignedSupervisorId}
              onChange={(e) => setAssignedSupervisorId(e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              <option value="">— Unassigned —</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {[u.firstName, u.lastName].filter(Boolean).join(" ") || u.username}
                  {u.designation ? ` — ${u.designation}` : ""}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Package Code</label>
            <input
              value={packageCode}
              onChange={(e) => setPackageCode(e.target.value)}
              placeholder="PKG-1A"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Status</label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as StretchStatus)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s.replace("_", " ")}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Target Date</label>
            <input
              type="date"
              value={targetDate}
              onChange={(e) => setTargetDate(e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">Milestone Name</label>
          <input
            value={milestoneName}
            onChange={(e) => setMilestoneName(e.target.value)}
            placeholder="Substantial completion — Stretch 1"
            className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
          />
        </div>

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={() => router.push(`/projects/${projectId}/stretches`)}
            className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
          >
            {mutation.isPending ? "Saving…" : "Create Stretch"}
          </button>
        </div>
      </form>
    </div>
  );
}
