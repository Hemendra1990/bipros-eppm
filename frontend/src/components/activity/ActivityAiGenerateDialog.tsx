"use client";

import React, { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Sparkles, RefreshCw, Loader2, AlertCircle, CheckCircle, XCircle } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogBody, DialogFooter } from "@/components/ui/dialog";
import { aiApi } from "@/lib/api/aiApi";
import { getErrorMessage } from "@/lib/utils/error";
import toast from "react-hot-toast";
import type { ActivityAiNode, ActivityAiGenerationResponse, ActivityAiApplyResponse } from "@/lib/types";

interface ActivityAiGenerateDialogProps {
  open: boolean;
  onClose: () => void;
  projectId: string;
}

type Phase = "setup" | "generating" | "preview" | "report";

export function ActivityAiGenerateDialog({ open, onClose, projectId }: ActivityAiGenerateDialogProps) {
  const queryClient = useQueryClient();

  const [phase, setPhase] = useState<Phase>("setup");
  const [projectTypeHint, setProjectTypeHint] = useState("");
  const [additionalContext, setAdditionalContext] = useState("");
  const [targetActivityCount, setTargetActivityCount] = useState(15);
  const [defaultDurationDays, setDefaultDurationDays] = useState(5);
  const [generationResult, setGenerationResult] = useState<ActivityAiGenerationResponse | null>(null);
  const [applyResult, setApplyResult] = useState<ActivityAiApplyResponse | null>(null);

  const generateMutation = useMutation({
    mutationFn: () =>
      aiApi.generateActivities(projectId, {
        projectTypeHint: projectTypeHint || undefined,
        additionalContext: additionalContext || undefined,
        targetActivityCount,
        defaultDurationDays,
      }),
    onSuccess: (data) => {
      if (data.data) {
        setGenerationResult(data.data);
        setPhase("preview");
      }
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to generate activities");
      if (msg.includes("WBS_REQUIRED") || msg.includes("Project has no WBS")) {
        toast.error("Please create a WBS for this project before generating activities.");
      } else {
        toast.error(msg);
      }
      setPhase("setup");
    },
  });

  const applyMutation = useMutation({
    mutationFn: () =>
      aiApi.applyGeneratedActivities(projectId, {
        activities: generationResult?.activities ?? [],
      }),
    onSuccess: (data) => {
      if (data.data) {
        setApplyResult(data.data);
        setPhase("report");
        queryClient.invalidateQueries({ queryKey: ["activities", projectId] });
        queryClient.invalidateQueries({ queryKey: ["relationships", projectId] });
        queryClient.invalidateQueries({ queryKey: ["wbs", projectId] });
        const created = data.data.createdActivityIds.length;
        toast.success(`${created} activities created successfully`);
      }
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to apply activities"));
    },
  });

  const handleClose = () => {
    setPhase("setup");
    setGenerationResult(null);
    setApplyResult(null);
    onClose();
  };

  const handleGenerate = () => {
    setPhase("generating");
    generateMutation.mutate();
  };

  const handleRegenerate = () => {
    setPhase("setup");
    setGenerationResult(null);
  };

  return (
    <Dialog open={open} onOpenChange={(v) => !v && handleClose()}>
      <DialogContent className="max-w-3xl max-h-[85vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles size={20} className="text-gold-deep" />
            Generate Activities with AI
          </DialogTitle>
        </DialogHeader>

        <DialogBody className="flex-1 overflow-y-auto">
          {phase === "setup" && (
            <SetupPhase
              projectTypeHint={projectTypeHint}
              setProjectTypeHint={setProjectTypeHint}
              additionalContext={additionalContext}
              setAdditionalContext={setAdditionalContext}
              targetActivityCount={targetActivityCount}
              setTargetActivityCount={setTargetActivityCount}
              defaultDurationDays={defaultDurationDays}
              setDefaultDurationDays={setDefaultDurationDays}
            />
          )}

          {phase === "generating" && <GeneratingPhase />}

          {phase === "preview" && generationResult && (
            <PreviewPhase rationale={generationResult.rationale} activities={generationResult.activities} />
          )}

          {phase === "report" && applyResult && <ReportPhase result={applyResult} />}
        </DialogBody>

        <DialogFooter>
          {phase === "setup" && (
            <>
              <button
                onClick={handleClose}
                className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
              >
                Cancel
              </button>
              <button
                onClick={handleGenerate}
                disabled={generateMutation.isPending}
                className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                <Sparkles size={16} />
                Generate
              </button>
            </>
          )}

          {phase === "generating" && (
            <button
              onClick={handleClose}
              className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
            >
              Cancel
            </button>
          )}

          {phase === "preview" && (
            <>
              <button
                onClick={handleRegenerate}
                className="inline-flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
              >
                <RefreshCw size={16} />
                Regenerate
              </button>
              <button
                onClick={() => applyMutation.mutate()}
                disabled={applyMutation.isPending}
                className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                {applyMutation.isPending ? "Applying..." : "Apply to Project"}
              </button>
            </>
          )}

          {phase === "report" && (
            <button
              onClick={handleClose}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
            >
              Done
            </button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function SetupPhase({
  projectTypeHint,
  setProjectTypeHint,
  additionalContext,
  setAdditionalContext,
  targetActivityCount,
  setTargetActivityCount,
  defaultDurationDays,
  setDefaultDurationDays,
}: {
  projectTypeHint: string;
  setProjectTypeHint: (v: string) => void;
  additionalContext: string;
  setAdditionalContext: (v: string) => void;
  targetActivityCount: number;
  setTargetActivityCount: (v: number) => void;
  defaultDurationDays: number;
  setDefaultDurationDays: (v: number) => void;
}) {
  const inputClass =
    "mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent";

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-text-secondary">
          Describe your project type <span className="text-text-muted">(optional)</span>
        </label>
        <input
          type="text"
          value={projectTypeHint}
          onChange={(e) => setProjectTypeHint(e.target.value)}
          placeholder="e.g., Highway widening, 12 km, 4-lane"
          className={inputClass}
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-text-secondary">
          Additional context <span className="text-text-muted">(optional)</span>
        </label>
        <textarea
          value={additionalContext}
          onChange={(e) => setAdditionalContext(e.target.value)}
          placeholder="Special phases, regulatory constraints, soil conditions..."
          rows={3}
          className={inputClass}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-text-secondary">Target Activity Count</label>
          <input
            type="number"
            min={5}
            max={100}
            value={targetActivityCount}
            onChange={(e) => setTargetActivityCount(Number(e.target.value))}
            className={inputClass}
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-text-secondary">Default Duration (days)</label>
          <input
            type="number"
            min={1}
            max={365}
            value={defaultDurationDays}
            onChange={(e) => setDefaultDurationDays(Number(e.target.value))}
            className={inputClass}
          />
        </div>
      </div>
    </div>
  );
}

function GeneratingPhase() {
  return (
    <div className="flex flex-col items-center justify-center py-12 gap-4">
      <Loader2 size={40} className="animate-spin text-accent" />
      <div className="text-center">
        <p className="text-sm font-medium text-text-primary">Generating activities...</p>
        <p className="mt-1 text-xs text-text-muted">This may take a few moments</p>
      </div>
    </div>
  );
}

function PreviewPhase({ rationale, activities }: { rationale: string; activities: ActivityAiNode[] }) {
  return (
    <div className="space-y-4">
      {rationale && (
        <div className="rounded-lg border border-border bg-surface-hover/30 p-3">
          <p className="text-xs font-medium text-text-secondary mb-1">AI Rationale</p>
          <p className="text-sm text-text-primary">{rationale}</p>
        </div>
      )}
      <div>
        <p className="text-xs font-medium text-text-secondary mb-2">
          Generated Activities ({activities.length})
        </p>
        <div className="rounded-lg border border-border bg-surface/50 max-h-80 overflow-y-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-border bg-surface/80 sticky top-0">
              <tr>
                <th className="px-3 py-2 text-left text-xs font-semibold text-text-secondary">Code</th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-text-secondary">Name</th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-text-secondary">WBS</th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-text-secondary">Days</th>
                <th className="px-3 py-2 text-left text-xs font-semibold text-text-secondary">Predecessors</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {activities.map((a, i) => (
                <tr key={`${a.code}-${i}`} className="hover:bg-surface-hover/30">
                  <td className="px-3 py-2 font-mono text-accent whitespace-nowrap">{a.code}</td>
                  <td className="px-3 py-2 text-text-primary">{a.name}</td>
                  <td className="px-3 py-2 font-mono text-text-secondary whitespace-nowrap">{a.wbsNodeCode}</td>
                  <td className="px-3 py-2 text-text-secondary whitespace-nowrap">{a.originalDurationDays}</td>
                  <td className="px-3 py-2 text-text-muted text-xs">
                    {a.predecessorCodes.length > 0 ? a.predecessorCodes.join(", ") : "\u2014"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function ReportPhase({ result }: { result: ActivityAiApplyResponse }) {
  const hasIssues =
    result.codeCollisions.length > 0 ||
    result.wbsResolutionFailures.length > 0 ||
    result.relationshipResolutionFailures.length > 0;

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-success/30 bg-success/10 p-3 flex items-center gap-2">
        <CheckCircle size={16} className="text-success shrink-0" />
        <p className="text-sm text-text-primary">
          {result.createdActivityIds.length} activities and {result.createdRelationshipIds.length} relationships created.
        </p>
      </div>

      {hasIssues && (
        <div className="space-y-3">
          {result.codeCollisions.length > 0 && (
            <div className="rounded-lg border border-warning/30 bg-warning/10 p-3">
              <div className="flex items-center gap-2 mb-1">
                <AlertCircle size={14} className="text-warning" />
                <p className="text-xs font-medium text-warning">Code Collisions Resolved</p>
              </div>
              <ul className="text-xs text-text-secondary space-y-0.5">
                {result.codeCollisions.map((c, i) => (
                  <li key={i} className="font-mono">{c}</li>
                ))}
              </ul>
            </div>
          )}

          {result.wbsResolutionFailures.length > 0 && (
            <div className="rounded-lg border border-danger/30 bg-danger/10 p-3">
              <div className="flex items-center gap-2 mb-1">
                <XCircle size={14} className="text-danger" />
                <p className="text-xs font-medium text-danger">WBS Resolution Failures</p>
              </div>
              <ul className="text-xs text-text-secondary space-y-0.5">
                {result.wbsResolutionFailures.map((f, i) => (
                  <li key={i} className="font-mono">{f}</li>
                ))}
              </ul>
            </div>
          )}

          {result.relationshipResolutionFailures.length > 0 && (
            <div className="rounded-lg border border-warning/30 bg-warning/10 p-3">
              <div className="flex items-center gap-2 mb-1">
                <AlertCircle size={14} className="text-warning" />
                <p className="text-xs font-medium text-warning">Relationship Resolution Failures</p>
              </div>
              <ul className="text-xs text-text-secondary space-y-0.5">
                {result.relationshipResolutionFailures.map((f, i) => (
                  <li key={i} className="font-mono">{f}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
