"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Trash2 } from "lucide-react";
import { Breadcrumb } from "@/components/common/Breadcrumb";
import { PageHeader } from "@/components/common/PageHeader";
import { SupervisorPicker } from "@/components/resources/SupervisorPicker";
import {
  crewApi,
  type CrewMemberRequest,
  type CrewRequest,
} from "@/lib/api/crewApi";
import { getErrorMessage } from "@/lib/utils/error";

const inputCls =
  "w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-accent focus:outline-none";
const labelCls = "block text-sm font-medium text-text-secondary mb-1";

interface DraftMember extends CrewMemberRequest {
  resourceLabel?: string | null;
}

export default function NewCrewPage() {
  const router = useRouter();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [crewLeadResourceId, setCrewLeadResourceId] = useState<string | null>(null);
  const [crewLeadName, setCrewLeadName] = useState<string | null>(null);
  const [members, setMembers] = useState<DraftMember[]>([]);

  const createMutation = useMutation({
    mutationFn: (req: CrewRequest) => crewApi.create(req),
    onSuccess: (res) => {
      toast.success("Crew created");
      const id = res.data?.id;
      router.push(id ? `/resources/crews/${id}` : "/resources/crews");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Failed to create crew")),
  });

  const canSubmit = name.trim().length > 0 && !!crewLeadResourceId;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit || !crewLeadResourceId) return;
    createMutation.mutate({
      code: code.trim() || null,
      name: name.trim(),
      description: description.trim() || null,
      crewLeadResourceId,
      members: members
        .filter((m) => m.resourceId)
        .map(({ resourceId, roleInCrew, startDate, endDate }) => ({
          resourceId,
          roleInCrew: roleInCrew?.trim() || null,
          startDate: startDate || null,
          endDate: endDate || null,
        })),
    });
  };

  const addMemberRow = () =>
    setMembers((prev) => [...prev, { resourceId: "", roleInCrew: "" }]);
  const updateMember = (idx: number, patch: Partial<DraftMember>) =>
    setMembers((prev) => prev.map((m, i) => (i === idx ? { ...m, ...patch } : m)));
  const removeMember = (idx: number) =>
    setMembers((prev) => prev.filter((_, i) => i !== idx));

  return (
    <div>
      <Breadcrumb
        items={[
          { label: "Resources", href: "/resources" },
          { label: "Crews", href: "/resources/crews" },
          { label: "New Crew", href: "/resources/crews/new", active: true },
        ]}
      />
      <PageHeader
        title="New Crew"
        description="A crew is a named group with a Labor crew lead and any mix of manpower, equipment, and materials. The lead is auto-added as a member with role LEAD."
      />

      <form onSubmit={handleSubmit} className="mt-4 max-w-3xl space-y-6">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className={labelCls}>Code (optional)</label>
            <input
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="Auto-generated as CREW-NNN if blank"
              className={inputCls}
            />
          </div>
          <div>
            <label className={labelCls}>Name *</label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={inputCls}
            />
          </div>
        </div>

        <div>
          <label className={labelCls}>Description</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className={inputCls}
          />
        </div>

        <div>
          <label className={labelCls}>Crew Lead * (Labor)</label>
          <SupervisorPicker
            value={crewLeadResourceId}
            onChange={(id, r) => {
              setCrewLeadResourceId(id);
              setCrewLeadName(r?.name ?? null);
            }}
            typeCode="LABOR"
            placeholder="Pick a Labor resource as crew lead..."
          />
          {crewLeadName && (
            <p className="mt-1 text-xs text-text-muted">
              Selected: {crewLeadName}. Will be auto-added as a LEAD member.
            </p>
          )}
        </div>

        <section>
          <div className="mb-2 flex items-center justify-between">
            <h2 className="text-base font-semibold text-text-primary">
              Members (optional)
            </h2>
            <button
              type="button"
              onClick={addMemberRow}
              className="rounded-md border border-border bg-surface px-3 py-1 text-sm text-text-primary hover:bg-surface-hover"
            >
              Add member
            </button>
          </div>
          {members.length === 0 ? (
            <p className="text-sm text-text-muted">
              No additional members. The crew lead will be the only member until
              you add more here or from the crew detail page.
            </p>
          ) : (
            <div className="space-y-3">
              {members.map((m, idx) => (
                <div
                  key={idx}
                  className="rounded-md border border-border bg-surface/40 p-3"
                >
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                    <div className="md:col-span-2">
                      <label className={labelCls}>Resource</label>
                      <SupervisorPicker
                        value={m.resourceId}
                        onChange={(id, r) =>
                          updateMember(idx, {
                            resourceId: id ?? "",
                            resourceLabel: r?.name ?? null,
                          })
                        }
                        excludeId={crewLeadResourceId ?? undefined}
                        excludeIds={members
                          .map((mm) => mm.resourceId)
                          .filter((rid, i) => rid && i !== idx)}
                        placeholder="Pick a resource..."
                      />
                    </div>
                    <div>
                      <label className={labelCls}>Role in crew</label>
                      <input
                        type="text"
                        value={m.roleInCrew ?? ""}
                        onChange={(e) =>
                          updateMember(idx, { roleInCrew: e.target.value })
                        }
                        placeholder="e.g. Operator"
                        className={inputCls}
                      />
                    </div>
                    <div>
                      <label className={labelCls}>Start date</label>
                      <input
                        type="date"
                        value={m.startDate ?? ""}
                        onChange={(e) =>
                          updateMember(idx, { startDate: e.target.value || null })
                        }
                        className={inputCls}
                      />
                    </div>
                    <div>
                      <label className={labelCls}>End date</label>
                      <input
                        type="date"
                        value={m.endDate ?? ""}
                        onChange={(e) =>
                          updateMember(idx, { endDate: e.target.value || null })
                        }
                        className={inputCls}
                      />
                    </div>
                    <div className="flex items-end">
                      <button
                        type="button"
                        onClick={() => removeMember(idx)}
                        className="inline-flex items-center gap-1 rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-secondary hover:bg-danger/10 hover:text-danger"
                      >
                        <Trash2 size={14} /> Remove
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <div className="flex gap-3">
          <button
            type="submit"
            disabled={!canSubmit || createMutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
          >
            {createMutation.isPending ? "Creating…" : "Create crew"}
          </button>
          <button
            type="button"
            onClick={() => router.push("/resources/crews")}
            className="rounded-md border border-border bg-surface px-4 py-2 text-sm text-text-primary hover:bg-surface-hover"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
