"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Lock, Printer, ShieldOff, X } from "lucide-react";
import { permitApi, type ApprovalDto } from "@/lib/api/permitApi";
import { useAuth } from "@/lib/auth/useAuth";
import { ApprovalTimeline, PermitStatusBadge, PermitTypeBadge, RiskBadge } from "@/components/permits";

export default function PermitDetailPage() {
  const params = useParams<{ permitId: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { hasAnyRole } = useAuth();
  const [actionError, setActionError] = useState<string | null>(null);

  const { data: permit, isLoading, error } = useQuery({
    queryKey: ["permit", params.permitId],
    queryFn: () => permitApi.get(params.permitId),
    enabled: !!params.permitId,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["permit", params.permitId] });
    queryClient.invalidateQueries({ queryKey: ["permits-list"] });
    queryClient.invalidateQueries({ queryKey: ["permits-dashboard"] });
  };

  const approveMutation = useMutation({
    mutationFn: ({ stepNo, remarks }: { stepNo: number; remarks?: string }) =>
      permitApi.approve(params.permitId, stepNo, remarks),
    onSuccess: invalidate,
    onError: (err: Error) => setActionError(err.message),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ stepNo, reason }: { stepNo: number; reason: string }) =>
      permitApi.reject(params.permitId, stepNo, reason),
    onSuccess: invalidate,
    onError: (err: Error) => setActionError(err.message),
  });

  const issueMutation = useMutation({
    mutationFn: () => permitApi.issue(params.permitId),
    onSuccess: invalidate,
    onError: (err: Error) => setActionError(err.message),
  });

  const closeMutation = useMutation({
    mutationFn: (remarks: string) => permitApi.close(params.permitId, remarks),
    onSuccess: invalidate,
    onError: (err: Error) => setActionError(err.message),
  });

  const revokeMutation = useMutation({
    mutationFn: (reason: string) => permitApi.revoke(params.permitId, reason),
    onSuccess: invalidate,
    onError: (err: Error) => setActionError(err.message),
  });

  if (isLoading) {
    return <p className="p-6 text-sm text-slate">Loading permit…</p>;
  }
  if (error || !permit) {
    return <p className="p-6 text-sm text-burgundy">Failed to load permit.</p>;
  }

  const principal = permit.workers.find((w) => w.roleOnPermit === "PRINCIPAL") || permit.workers[0];
  const activeApproval: ApprovalDto | undefined = permit.approvals.find(
    (a) => a.status === "PENDING"
  );

  const canApproveCurrent = (() => {
    if (!activeApproval) return false;
    const role = activeApproval.role.replace("ROLE_", "");
    return hasAnyRole([role, "ADMIN"]);
  })();
  const canIssue =
    permit.status === "APPROVED" && hasAnyRole(["PROJECT_MANAGER", "HSE_OFFICER", "ADMIN"]);
  const canClose =
    (permit.status === "ISSUED" ||
      permit.status === "IN_PROGRESS" ||
      permit.status === "SUSPENDED") &&
    hasAnyRole(["HSE_OFFICER", "ADMIN"]);
  const canRevoke =
    !["CLOSED", "REVOKED", "EXPIRED", "REJECTED", "DRAFT"].includes(permit.status) &&
    hasAnyRole(["HSE_OFFICER", "ADMIN"]);

  return (
    <div className="space-y-6 p-6">
      <header className="flex items-start justify-between gap-3">
        <button
          type="button"
          onClick={() => router.back()}
          className="inline-flex items-center gap-1 rounded-md border border-divider bg-paper px-3 py-1.5 text-sm text-slate hover:bg-ivory"
        >
          <ArrowLeft size={14} /> Back
        </button>
        <Link
          href="/permits"
          className="text-xs text-slate hover:underline"
        >
          ← All permits
        </Link>
      </header>

      <section className="rounded-xl border border-hairline bg-paper p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs uppercase tracking-widest text-slate">Permit ID</p>
            <h1 className="font-mono text-2xl font-bold text-gold-deep">{permit.permitCode}</h1>
          </div>
          <PermitStatusBadge status={permit.status} />
        </div>
        <div
          className="mt-4 rounded-lg border p-4"
          style={{
            backgroundColor: `${permit.permitTypeColorHex || "#D4AF37"}11`,
            borderColor: `${permit.permitTypeColorHex || "#D4AF37"}55`,
          }}
        >
          <PermitTypeBadge
            code={permit.permitTypeCode}
            name={permit.permitTypeName}
            colorHex={permit.permitTypeColorHex}
            size="md"
          />
          <div className="mt-2 text-sm text-charcoal">
            {permit.taskDescription || "—"}
            {permit.locationZone ? ` · ${permit.locationZone}` : ""}
            {permit.chainageMarker ? ` · ${permit.chainageMarker}` : ""}
          </div>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
          <Cell label="Worker">{principal?.fullName || "—"}</Cell>
          <Cell label="Trade">{principal?.trade || "—"}</Cell>
          <Cell label="Nationality">{principal?.nationality || "—"}</Cell>
          <Cell label="Shift">{permit.shift}</Cell>
          <Cell label="Risk Level">
            <RiskBadge level={permit.riskLevel} />
          </Cell>
          <Cell label="Validity">
            {new Date(permit.startAt).toLocaleString()} → {new Date(permit.endAt).toLocaleString()}
          </Cell>
        </div>
      </section>

      <section className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="rounded-xl border border-hairline bg-paper p-5 shadow-sm lg:col-span-2">
          <h2 className="text-sm font-semibold text-charcoal">Approval Timeline</h2>
          <div className="mt-3">
            <ApprovalTimeline approvals={permit.approvals} />
          </div>
        </div>

        <div className="space-y-3 rounded-xl border border-hairline bg-paper p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-charcoal">Actions</h2>

          {activeApproval && canApproveCurrent && (
            <ApprovalActions
              approval={activeApproval}
              onApprove={(remarks) =>
                approveMutation.mutate({ stepNo: activeApproval.stepNo, remarks })
              }
              onReject={(reason) =>
                rejectMutation.mutate({ stepNo: activeApproval.stepNo, reason })
              }
              busy={approveMutation.isPending || rejectMutation.isPending}
            />
          )}

          {canIssue && (
            <ActionButton
              onClick={() => issueMutation.mutate()}
              busy={issueMutation.isPending}
              tone="success"
            >
              Issue Permit (generate QR &amp; SMS)
            </ActionButton>
          )}

          {canClose && (
            <CloseButton
              onClose={(remarks) => closeMutation.mutate(remarks)}
              busy={closeMutation.isPending}
            />
          )}

          {canRevoke && (
            <RevokeButton
              onRevoke={(reason) => revokeMutation.mutate(reason)}
              busy={revokeMutation.isPending}
            />
          )}

          {permit.qrAvailable && (
            <a
              href={permitApi.qrPngUrl(permit.id)}
              target="_blank"
              rel="noopener noreferrer"
              className="flex w-full items-center justify-center gap-2 rounded-md border border-divider bg-paper px-3 py-2 text-sm font-semibold text-charcoal hover:bg-ivory"
            >
              <Printer size={14} /> Print / QR
            </a>
          )}

          {!canApproveCurrent && !canIssue && !canClose && !canRevoke && (
            <p className="text-xs text-slate">
              No actions are available for your role at this permit&apos;s current status.
            </p>
          )}
        </div>
      </section>

      <section className="rounded-xl border border-hairline bg-paper p-5 shadow-sm">
        <h2 className="text-sm font-semibold text-charcoal">All Workers ({permit.workers.length})</h2>
        <table className="mt-3 w-full text-sm">
          <thead className="text-left text-xs font-semibold uppercase tracking-wider text-slate">
            <tr>
              <th className="py-2">Name</th>
              <th className="py-2">Civil ID</th>
              <th className="py-2">Trade</th>
              <th className="py-2">Nationality</th>
              <th className="py-2">Role</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-hairline">
            {permit.workers.map((w) => (
              <tr key={w.id}>
                <td className="py-2 text-charcoal">{w.fullName}</td>
                <td className="py-2 text-slate">{w.civilId || "—"}</td>
                <td className="py-2 text-slate">{w.trade || "—"}</td>
                <td className="py-2 text-slate">{w.nationality || "—"}</td>
                <td className="py-2 text-xs font-semibold text-charcoal">{w.roleOnPermit}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {permit.gasTests.length > 0 && (
        <section className="rounded-xl border border-hairline bg-paper p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-charcoal">Gas Tests</h2>
          <ul className="mt-3 space-y-2 text-sm">
            {permit.gasTests.map((g) => (
              <li
                key={g.id}
                className="flex items-center justify-between rounded-md border border-hairline bg-ivory/50 px-3 py-2"
              >
                <span className="text-xs text-slate">
                  {new Date(g.testedAt).toLocaleString()}
                </span>
                <span className="text-xs">
                  LEL {g.lelPct ?? "?"}% · O₂ {g.o2Pct ?? "?"}% · H₂S {g.h2sPpm ?? "?"} · CO {g.coPpm ?? "?"}
                </span>
                <span
                  className={`text-xs font-semibold ${
                    g.result === "PASS" ? "text-emerald" : "text-burgundy"
                  }`}
                >
                  {g.result}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {permit.lifecycleEvents.length > 0 && (
        <section className="rounded-xl border border-hairline bg-paper p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-charcoal">Lifecycle</h2>
          <ul className="mt-3 space-y-1 text-xs">
            {permit.lifecycleEvents.map((e) => (
              <li key={e.id} className="flex items-baseline gap-2">
                <span className="font-mono text-slate">
                  {new Date(e.occurredAt).toLocaleString()}
                </span>
                <span className="text-charcoal">{e.eventType.replace(/_/g, " ")}</span>
                {e.payloadJson && <span className="text-slate">{e.payloadJson}</span>}
              </li>
            ))}
          </ul>
        </section>
      )}

      {actionError && (
        <div className="rounded-md border border-burgundy/40 bg-burgundy/10 px-4 py-2 text-sm text-burgundy">
          {actionError}
        </div>
      )}
    </div>
  );
}

function Cell({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-md border border-hairline bg-ivory/40 p-3">
      <div className="text-[11px] font-semibold uppercase tracking-wider text-slate">{label}</div>
      <div className="mt-1 text-sm font-medium text-charcoal">{children}</div>
    </div>
  );
}

function ActionButton({
  onClick,
  busy,
  tone,
  children,
}: {
  onClick: () => void;
  busy?: boolean;
  tone: "success" | "danger" | "neutral";
  children: React.ReactNode;
}) {
  const tones = {
    success: "bg-emerald text-white hover:opacity-90",
    danger: "bg-burgundy text-white hover:opacity-90",
    neutral: "border border-divider bg-paper text-charcoal hover:bg-ivory",
  };
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={busy}
      className={`flex w-full items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-semibold disabled:opacity-50 ${tones[tone]}`}
    >
      {children}
    </button>
  );
}

function ApprovalActions({
  approval,
  onApprove,
  onReject,
  busy,
}: {
  approval: ApprovalDto;
  onApprove: (remarks?: string) => void;
  onReject: (reason: string) => void;
  busy?: boolean;
}) {
  const [remarks, setRemarks] = useState("");
  const [showReject, setShowReject] = useState(false);
  const [reason, setReason] = useState("");
  return (
    <div className="space-y-2">
      <p className="text-xs text-slate">
        Active step: <span className="font-semibold text-charcoal">{approval.label}</span>
      </p>
      <textarea
        value={remarks}
        onChange={(e) => setRemarks(e.target.value)}
        placeholder="Approval remarks (optional)"
        className="block w-full rounded-md border border-divider bg-paper px-3 py-2 text-sm"
        rows={2}
      />
      <ActionButton
        onClick={() => onApprove(remarks || undefined)}
        busy={busy}
        tone="success"
      >
        Approve
      </ActionButton>
      <button
        type="button"
        onClick={() => setShowReject((s) => !s)}
        className="text-xs text-burgundy hover:underline"
      >
        {showReject ? "Cancel reject" : "Reject this step"}
      </button>
      {showReject && (
        <div className="space-y-2 rounded-md border border-burgundy/30 bg-burgundy/5 p-3">
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Rejection reason (required)"
            className="block w-full rounded-md border border-divider bg-paper px-3 py-2 text-sm"
            rows={2}
          />
          <ActionButton
            onClick={() => reason.trim() && onReject(reason.trim())}
            busy={busy}
            tone="danger"
          >
            <X size={14} /> Confirm Reject
          </ActionButton>
        </div>
      )}
    </div>
  );
}

function CloseButton({ onClose, busy }: { onClose: (remarks: string) => void; busy?: boolean }) {
  const [open, setOpen] = useState(false);
  const [remarks, setRemarks] = useState("");
  if (!open) {
    return (
      <ActionButton onClick={() => setOpen(true)} tone="neutral">
        <Lock size={14} /> Close Permit
      </ActionButton>
    );
  }
  return (
    <div className="space-y-2 rounded-md border border-emerald/30 bg-emerald/5 p-3">
      <textarea
        value={remarks}
        onChange={(e) => setRemarks(e.target.value)}
        placeholder="Close remarks"
        className="block w-full rounded-md border border-divider bg-paper px-3 py-2 text-sm"
        rows={2}
      />
      <ActionButton onClick={() => onClose(remarks)} busy={busy} tone="success">
        Confirm Close
      </ActionButton>
    </div>
  );
}

function RevokeButton({
  onRevoke,
  busy,
}: {
  onRevoke: (reason: string) => void;
  busy?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  if (!open) {
    return (
      <ActionButton onClick={() => setOpen(true)} tone="danger">
        <ShieldOff size={14} /> Revoke
      </ActionButton>
    );
  }
  return (
    <div className="space-y-2 rounded-md border border-burgundy/30 bg-burgundy/5 p-3">
      <textarea
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="Revocation reason (required)"
        className="block w-full rounded-md border border-divider bg-paper px-3 py-2 text-sm"
        rows={2}
      />
      <ActionButton
        onClick={() => reason.trim() && onRevoke(reason.trim())}
        busy={busy}
        tone="danger"
      >
        Confirm Revoke
      </ActionButton>
    </div>
  );
}
