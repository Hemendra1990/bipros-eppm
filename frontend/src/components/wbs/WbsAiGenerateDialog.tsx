"use client";

import React, { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Sparkles,
  RefreshCw,
  ChevronRight,
  ChevronDown,
  Folder,
  FolderOpen,
  File,
  Loader2,
  Upload,
  X,
  FileText,
} from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogBody, DialogFooter } from "@/components/ui/dialog";
import { aiApi } from "@/lib/api/aiApi";
import { getErrorMessage } from "@/lib/utils/error";
import toast from "react-hot-toast";
import type {
  ApplyMode,
  AssetClass,
  CollisionAction,
  CollisionResult,
  WbsAiNode,
  WbsAiGenerationResponse,
  ProjectResponse,
} from "@/lib/types";

const ASSET_CLASSES: AssetClass[] = [
  "ROAD",
  "RAIL",
  "POWER",
  "WATER",
  "ICT",
  "BUILDING",
  "GREEN_INFRASTRUCTURE",
];

const ALLOWED_DOC_EXTS = [".pdf", ".xlsx", ".xls"];
const MAX_DOC_BYTES = 50 * 1024 * 1024;

interface WbsAiGenerateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  project: ProjectResponse;
}

type Phase = "setup" | "generating" | "preview";
type SetupTab = "scratch" | "document";

export function WbsAiGenerateDialog({ open, onOpenChange, project }: WbsAiGenerateDialogProps) {
  const queryClient = useQueryClient();

  const industryCode = project.industryCode;
  const detectedAssetClass = industryCode
    ? ASSET_CLASSES.find((ac) => ac === industryCode.toUpperCase()) ?? null
    : null;

  const [phase, setPhase] = useState<Phase>("setup");
  const [setupTab, setSetupTab] = useState<SetupTab>("document");
  const [assetClass, setAssetClass] = useState<AssetClass | null>(detectedAssetClass);
  const [projectTypeHint, setProjectTypeHint] = useState("");
  const [additionalContext, setAdditionalContext] = useState("");
  const [targetDepth, setTargetDepth] = useState(3);
  const [docFile, setDocFile] = useState<File | null>(null);
  /** When set, generated nodes are grafted under this parent (ADD_UNDER mode). null = project root. */
  const [addUnderParentId, setAddUnderParentId] = useState<string | null>(null);
  /** Set after POST .../generate-from-document returns 202; drives the job poll. */
  const [jobId, setJobId] = useState<string | null>(null);
  const [docAssetClass, setDocAssetClass] = useState<AssetClass | null>(detectedAssetClass);
  const [docTargetDepth, setDocTargetDepth] = useState(3);
  const [generationResult, setGenerationResult] = useState<WbsAiGenerationResponse | null>(null);

  const generateMutation = useMutation({
    mutationFn: () =>
      aiApi.generateWbs(project.id, {
        assetClass: assetClass ?? undefined,
        projectTypeHint: projectTypeHint || undefined,
        additionalContext: additionalContext || undefined,
        targetDepth,
      }),
    onSuccess: (data) => {
      if (data.data) {
        if (data.data.assetClassNeedsConfirmation) {
          toast.error("Please select a project type to continue");
          setPhase("setup");
          return;
        }
        setGenerationResult(data.data);
        setPhase("preview");
      }
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to generate WBS"));
      setPhase("setup");
    },
  });

  const generateFromDocumentMutation = useMutation({
    mutationFn: () => {
      if (!docFile) {
        return Promise.reject(new Error("No file selected"));
      }
      return aiApi.generateWbsFromDocument(
        project.id,
        {
          assetClass: docAssetClass ?? undefined,
          targetDepth: docTargetDepth,
        },
        docFile,
      );
    },
    onSuccess: (data) => {
      // Backend returns 202 + jobId. Switch to Generating phase and start polling.
      const accepted = data.data;
      if (accepted?.jobId) {
        setJobId(accepted.jobId);
        // Phase stays "generating" until the job poll resolves.
      }
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to generate WBS from document"));
      setPhase("setup");
    },
  });

  // Poll the job until terminal state. Stops automatically once status is
  // DONE / FAILED / CANCELLED, or when the user closes the modal.
  const jobQuery = useQuery({
    queryKey: ["wbs-ai-job", project.id, jobId],
    queryFn: () => aiApi.getWbsAiJob(project.id, jobId!),
    enabled: !!jobId && phase === "generating",
    refetchInterval: (q) => {
      const status = q.state.data?.data?.status;
      if (status === "DONE" || status === "FAILED" || status === "CANCELLED") return false;
      return 2000;
    },
    refetchOnWindowFocus: false,
  });

  useEffect(() => {
    const view = jobQuery.data?.data;
    if (!view) return;
    if (view.status === "DONE" && view.result) {
      setGenerationResult(view.result);
      setPhase("preview");
      setJobId(null);
    } else if (view.status === "FAILED") {
      toast.error(view.errorMessage ?? "AI generation failed");
      setPhase("setup");
      setJobId(null);
    } else if (view.status === "CANCELLED") {
      toast("Generation cancelled");
      setPhase("setup");
      setJobId(null);
    }
  }, [jobQuery.data]);

  const applyMutation = useMutation({
    mutationFn: () => {
      const mode: ApplyMode = addUnderParentId ? "ADD_UNDER" : "MERGE";
      return aiApi.applyGeneratedWbs(project.id, {
        parentId: addUnderParentId,
        mode,
        nodes: generationResult?.nodes ?? [],
      });
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["wbs", project.id] });
      const results = data.data ?? [];
      const renamed = results.filter((r) => r.action === "RENAMED").length;
      const skipped = results.filter((r) => r.action === "SKIPPED_DUPLICATE").length;
      const inserted = results.filter(
        (r) => r.action === "INSERTED_NEW" || r.action === "RESOLVED_TO_EXISTING_PARENT",
      ).length;
      const parts: string[] = [];
      if (inserted) parts.push(`${inserted} inserted`);
      if (renamed) parts.push(`${renamed} renamed`);
      if (skipped) parts.push(`${skipped} skipped (duplicates)`);
      toast.success(parts.length ? `WBS applied — ${parts.join(", ")}.` : "AI-generated WBS applied successfully");
      handleClose();
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to apply WBS"));
    },
  });

  const handleClose = () => {
    // If the user closes mid-generation, fire-and-forget cancel so the worker
    // drops its result instead of pointlessly persisting it.
    if (jobId) {
      aiApi.cancelWbsAiJob(project.id, jobId).catch(() => { /* best-effort */ });
    }
    setPhase("setup");
    setSetupTab("document");
    setGenerationResult(null);
    setDocFile(null);
    setAddUnderParentId(null);
    setJobId(null);
    onOpenChange(false);
  };

  const handleGenerate = () => {
    if (!assetClass) {
      toast.error("Please select a project type");
      return;
    }
    setPhase("generating");
    generateMutation.mutate();
  };

  const handleGenerateFromDocument = () => {
    if (!docFile) {
      toast.error("Please choose a PDF or Excel file");
      return;
    }
    setPhase("generating");
    generateFromDocumentMutation.mutate();
  };

  const handleRegenerate = () => {
    setPhase("setup");
    setGenerationResult(null);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles size={20} className="text-gold-deep" />
            Generate WBS with AI
          </DialogTitle>
        </DialogHeader>

        <DialogBody className="flex-1 overflow-y-auto">
          {phase === "setup" && (
            <div className="space-y-4">
              <SetupTabs activeTab={setupTab} onChange={setSetupTab} />
              {setupTab === "scratch" ? (
                <SetupFromScratch
                  detectedAssetClass={detectedAssetClass}
                  assetClass={assetClass}
                  setAssetClass={setAssetClass}
                  projectTypeHint={projectTypeHint}
                  setProjectTypeHint={setProjectTypeHint}
                  additionalContext={additionalContext}
                  setAdditionalContext={setAdditionalContext}
                  targetDepth={targetDepth}
                  setTargetDepth={setTargetDepth}
                />
              ) : (
                <SetupFromDocument
                  file={docFile}
                  setFile={setDocFile}
                  assetClass={docAssetClass}
                  setAssetClass={setDocAssetClass}
                  targetDepth={docTargetDepth}
                  setTargetDepth={setDocTargetDepth}
                />
              )}
            </div>
          )}

          {phase === "generating" && (
            <GeneratingPhase
              fromDocument={!!jobId || generateFromDocumentMutation.isPending}
              progressStage={jobQuery.data?.data?.progressStage ?? null}
              progressPct={jobQuery.data?.data?.progressPct ?? null}
            />
          )}

          {phase === "preview" && generationResult && (
            <PreviewPhase
              rationale={generationResult.rationale}
              nodes={generationResult.nodes}
              annotations={generationResult.previewAnnotations ?? null}
            />
          )}
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
              {setupTab === "scratch" ? (
                <button
                  onClick={handleGenerate}
                  disabled={!assetClass || generateMutation.isPending}
                  className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
                >
                  <Sparkles size={16} />
                  Generate
                </button>
              ) : (
                <button
                  onClick={handleGenerateFromDocument}
                  disabled={!docFile || generateFromDocumentMutation.isPending}
                  className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
                >
                  <Sparkles size={16} />
                  Generate from document
                </button>
              )}
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
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function SetupTabs({
  activeTab,
  onChange,
}: {
  activeTab: SetupTab;
  onChange: (tab: SetupTab) => void;
}) {
  const tabClass = (active: boolean) =>
    `inline-flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
      active
        ? "border-accent text-text-primary"
        : "border-transparent text-text-secondary hover:text-text-primary"
    }`;
  return (
    <div className="flex border-b border-border">
      <button type="button" onClick={() => onChange("scratch")} className={tabClass(activeTab === "scratch")}>
        <Sparkles size={14} />
        From scratch
      </button>
      <button type="button" onClick={() => onChange("document")} className={tabClass(activeTab === "document")}>
        <FileText size={14} />
        From document
      </button>
    </div>
  );
}

function SetupFromDocument({
  file,
  setFile,
  assetClass,
  setAssetClass,
  targetDepth,
  setTargetDepth,
}: {
  file: File | null;
  setFile: (f: File | null) => void;
  assetClass: AssetClass | null;
  setAssetClass: (ac: AssetClass | null) => void;
  targetDepth: number;
  setTargetDepth: (v: number) => void;
}) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [dragOver, setDragOver] = useState(false);

  const inputClass =
    "mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent";

  const validateAndSet = (chosen: File) => {
    const lower = chosen.name.toLowerCase();
    const okExt = ALLOWED_DOC_EXTS.some((ext) => lower.endsWith(ext));
    if (!okExt) {
      toast.error("Only PDF or Excel (.xlsx, .xls) files are allowed");
      return;
    }
    if (chosen.size > MAX_DOC_BYTES) {
      toast.error("File exceeds the 50 MB limit");
      return;
    }
    setFile(chosen);
  };

  const onPick = () => inputRef.current?.click();
  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const chosen = e.target.files?.[0];
    if (chosen) validateAndSet(chosen);
    e.target.value = "";
  };
  const onDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    const chosen = e.dataTransfer.files?.[0];
    if (chosen) validateAndSet(chosen);
  };

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-text-secondary">Document</label>
        <p className="mt-1 text-xs text-text-muted">
          Upload a PDF or Excel file describing the project&apos;s WBS / activities. Max 50 MB.
        </p>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.xlsx,.xls,application/pdf,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
          className="hidden"
          onChange={onChange}
        />
        {file ? (
          <div className="mt-2 flex items-center gap-3 rounded-md border border-border bg-surface-hover/40 px-3 py-2">
            <FileText size={18} className="text-accent flex-shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="truncate text-sm text-text-primary">{file.name}</p>
              <p className="text-xs text-text-muted">{(file.size / 1024).toFixed(1)} KB</p>
            </div>
            <button
              type="button"
              onClick={() => setFile(null)}
              className="rounded p-1 text-text-secondary hover:bg-surface-active hover:text-text-primary"
              aria-label="Remove file"
            >
              <X size={14} />
            </button>
          </div>
        ) : (
          <div
            onClick={onPick}
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={onDrop}
            className={`mt-2 flex cursor-pointer flex-col items-center justify-center gap-2 rounded-md border-2 border-dashed px-4 py-8 text-center transition-colors ${
              dragOver
                ? "border-accent bg-surface-hover/60"
                : "border-border bg-surface-hover/20 hover:bg-surface-hover/40"
            }`}
          >
            <Upload size={22} className="text-text-secondary" />
            <p className="text-sm text-text-primary">Drop a file here or click to browse</p>
            <p className="text-xs text-text-muted">PDF, .xlsx, .xls — up to 50 MB</p>
          </div>
        )}
      </div>

      <div>
        <label className="block text-sm font-medium text-text-secondary">
          Project Type <span className="text-text-muted">(optional hint)</span>
        </label>
        <select
          value={assetClass ?? ""}
          onChange={(e) => setAssetClass(e.target.value ? (e.target.value as AssetClass) : null)}
          className={inputClass}
        >
          <option value="">Let AI infer from the document</option>
          {ASSET_CLASSES.map((ac) => (
            <option key={ac} value={ac}>
              {ac.replace(/_/g, " ")}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-text-secondary">Target Depth</label>
        <select
          value={targetDepth}
          onChange={(e) => setTargetDepth(Number(e.target.value))}
          className={inputClass}
        >
          <option value={2}>2 levels (high-level phases)</option>
          <option value={3}>3 levels (recommended)</option>
          <option value={4}>4 levels (detailed)</option>
        </select>
      </div>
    </div>
  );
}

function SetupFromScratch({
  detectedAssetClass,
  assetClass,
  setAssetClass,
  projectTypeHint,
  setProjectTypeHint,
  additionalContext,
  setAdditionalContext,
  targetDepth,
  setTargetDepth,
}: {
  detectedAssetClass: AssetClass | null;
  assetClass: AssetClass | null;
  setAssetClass: (ac: AssetClass | null) => void;
  projectTypeHint: string;
  setProjectTypeHint: (v: string) => void;
  additionalContext: string;
  setAdditionalContext: (v: string) => void;
  targetDepth: number;
  setTargetDepth: (v: number) => void;
}) {
  const inputClass =
    "mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent";

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-text-secondary">Project Type</label>
        {detectedAssetClass ? (
          <div className="mt-1 flex items-center gap-2">
            <span className="text-sm text-text-primary font-medium">{detectedAssetClass}</span>
            <span className="text-xs text-text-muted">(detected from project)</span>
            <button
              type="button"
              onClick={() => setAssetClass(null)}
              className="text-xs text-accent hover:underline"
            >
              change
            </button>
          </div>
        ) : (
          <select
            value={assetClass ?? ""}
            onChange={(e) => setAssetClass(e.target.value ? (e.target.value as AssetClass) : null)}
            className={inputClass}
            required
          >
            <option value="">Select project type...</option>
            {ASSET_CLASSES.map((ac) => (
              <option key={ac} value={ac}>
                {ac.replace(/_/g, " ")}
              </option>
            ))}
          </select>
        )}
      </div>

      {!detectedAssetClass && (
        <div>
          <label className="block text-sm font-medium text-text-secondary">
            Describe your project type <span className="text-text-muted">(optional)</span>
          </label>
          <input
            type="text"
            value={projectTypeHint}
            onChange={(e) => setProjectTypeHint(e.target.value)}
            placeholder="e.g., 4-lane national highway, greenfield"
            className={inputClass}
          />
        </div>
      )}

      <div>
        <label className="block text-sm font-medium text-text-secondary">
          Additional context <span className="text-text-muted">(optional)</span>
        </label>
        <textarea
          value={additionalContext}
          onChange={(e) => setAdditionalContext(e.target.value)}
          placeholder="Scale, complexity, special phases, regulatory constraints..."
          rows={3}
          className={inputClass}
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-text-secondary">Target Depth</label>
        <select
          value={targetDepth}
          onChange={(e) => setTargetDepth(Number(e.target.value))}
          className={inputClass}
        >
          <option value={2}>2 levels (high-level phases)</option>
          <option value={3}>3 levels (recommended)</option>
          <option value={4}>4 levels (detailed)</option>
        </select>
      </div>
    </div>
  );
}

function GeneratingPhase({
  fromDocument,
  progressStage,
  progressPct,
}: {
  fromDocument: boolean;
  progressStage?: string | null;
  progressPct?: number | null;
}) {
  const stageLabel = (() => {
    if (!progressStage || progressStage === "PENDING") {
      return fromDocument ? "Queued — reading your document..." : "Designing WBS structure...";
    }
    switch (progressStage) {
      case "READING": return "Reading the uploaded document...";
      case "ANALYZING": return "AI is analyzing the document...";
      case "MERGING": return "Merging with existing project WBS...";
      case "DONE": return "Done.";
      default: return progressStage;
    }
  })();
  const pct = typeof progressPct === "number" ? Math.max(0, Math.min(100, progressPct)) : null;

  return (
    <div className="flex flex-col items-center justify-center py-12 gap-4">
      <Loader2 size={40} className="animate-spin text-accent" />
      <div className="text-center w-full max-w-sm">
        <p className="text-sm font-medium text-text-primary">{stageLabel}</p>
        <p className="mt-1 text-xs text-text-muted">This may take 30–90 seconds</p>
        {pct !== null && (
          <div className="mt-4 h-1.5 w-full rounded-full bg-surface-active overflow-hidden">
            <div
              className="h-full bg-accent transition-[width] duration-500"
              style={{ width: `${pct}%` }}
            />
          </div>
        )}
      </div>
    </div>
  );
}

function PreviewPhase({
  rationale,
  nodes,
  annotations,
}: {
  rationale: string;
  nodes: WbsAiNode[];
  annotations: CollisionResult[] | null;
}) {
  // Group annotations by code so the tree can quickly look up the action for each node.
  const annotByCode = React.useMemo(() => {
    const m = new Map<string, CollisionResult>();
    (annotations ?? []).forEach((a) => m.set(a.originalCode, a));
    return m;
  }, [annotations]);

  // Summary chips at the top of the preview so the user sees the overall picture at a glance.
  const counts = React.useMemo(() => {
    const c: Partial<Record<CollisionAction, number>> = {};
    (annotations ?? []).forEach((a) => {
      c[a.action] = (c[a.action] ?? 0) + 1;
    });
    return {
      INSERTED_NEW: c.INSERTED_NEW ?? 0,
      RENAMED: c.RENAMED ?? 0,
      SKIPPED_DUPLICATE: c.SKIPPED_DUPLICATE ?? 0,
      RESOLVED_TO_EXISTING_PARENT: c.RESOLVED_TO_EXISTING_PARENT ?? 0,
    };
  }, [annotations]);

  return (
    <div className="space-y-4">
      {rationale && (
        <div className="rounded-lg border border-border bg-surface-hover/30 p-3">
          <p className="text-xs font-medium text-text-secondary mb-1">AI Rationale</p>
          <p className="text-sm text-text-primary">{rationale}</p>
        </div>
      )}
      {annotations && annotations.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 text-xs">
          {counts.INSERTED_NEW > 0 && (
            <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-emerald-300">
              {counts.INSERTED_NEW} new
            </span>
          )}
          {counts.RESOLVED_TO_EXISTING_PARENT > 0 && (
            <span className="rounded-full bg-sky-500/15 px-2 py-0.5 text-sky-300">
              {counts.RESOLVED_TO_EXISTING_PARENT} under existing
            </span>
          )}
          {counts.RENAMED > 0 && (
            <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-amber-300">
              {counts.RENAMED} renamed
            </span>
          )}
          {counts.SKIPPED_DUPLICATE > 0 && (
            <span className="rounded-full bg-slate-500/15 px-2 py-0.5 text-slate-300">
              {counts.SKIPPED_DUPLICATE} skipped
            </span>
          )}
        </div>
      )}
      <div>
        <p className="text-xs font-medium text-text-secondary mb-2">Generated Structure</p>
        <div className="rounded-lg border border-border bg-surface/50 p-3 font-mono text-sm max-h-80 overflow-y-auto">
          <WbsAiTree nodes={nodes} level={0} isLast={[]} annotByCode={annotByCode} />
        </div>
      </div>
    </div>
  );
}

function actionTag(action: CollisionAction): { label: string; className: string } | null {
  switch (action) {
    case "INSERTED_NEW":
      return { label: "NEW", className: "bg-emerald-500/15 text-emerald-300" };
    case "RENAMED":
      return { label: "RENAMED", className: "bg-amber-500/15 text-amber-300" };
    case "SKIPPED_DUPLICATE":
      return { label: "SKIPPED", className: "bg-slate-500/15 text-slate-300" };
    case "RESOLVED_TO_EXISTING_PARENT":
      return { label: "UNDER EXISTING", className: "bg-sky-500/15 text-sky-300" };
    default:
      return null;
  }
}

function WbsAiTree({
  nodes,
  level,
  isLast,
  annotByCode,
}: {
  nodes: WbsAiNode[];
  level: number;
  isLast: boolean[];
  annotByCode: Map<string, CollisionResult>;
}) {
  const [expanded, setExpanded] = useState<Record<number, boolean>>(() => {
    const initial: Record<number, boolean> = {};
    nodes.forEach((_, i) => {
      initial[i] = true;
    });
    return initial;
  });

  const toggle = (idx: number) => {
    setExpanded((prev) => ({ ...prev, [idx]: !prev[idx] }));
  };

  return (
    <div>
      {nodes.map((node, index) => {
        const hasChildren = node.children && node.children.length > 0;
        const isOpen = expanded[index] ?? false;
        const isLastNode = index === nodes.length - 1;

        return (
          <div key={`${node.code}-${index}`}>
            <div className="flex items-center py-0.5 hover:bg-surface-hover/50 rounded">
              {Array.from({ length: level }).map((_, i) => (
                <span key={i} className="inline-flex w-6 justify-center flex-shrink-0">
                  {isLast[i] ? (
                    <span className="w-px" />
                  ) : (
                    <span className="w-px bg-surface-active h-full min-h-[24px]" />
                  )}
                </span>
              ))}

              {level > 0 && (
                <span className="inline-flex w-6 items-center justify-center flex-shrink-0 text-text-muted">
                  {isLastNode ? "└" : "├"}
                  <span className="inline-block w-2 h-px bg-surface-active" />
                </span>
              )}

              <button
                type="button"
                onClick={() => hasChildren && toggle(index)}
                className={`flex-shrink-0 w-5 h-5 flex items-center justify-center rounded ${
                  hasChildren
                    ? "text-text-secondary hover:text-text-primary hover:bg-surface-active cursor-pointer"
                    : "text-transparent cursor-default"
                }`}
              >
                {hasChildren ? (
                  isOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />
                ) : null}
              </button>

              <span className="flex-shrink-0 mr-2">
                {hasChildren ? (
                  isOpen ? (
                    <FolderOpen size={16} className="text-warning" />
                  ) : (
                    <Folder size={16} className="text-amber-500" />
                  )
                ) : (
                  <File size={16} className="text-text-muted" />
                )}
              </span>

              <span className="font-semibold text-accent mr-2 flex-shrink-0">{node.code}</span>
              <span className="text-text-secondary truncate flex-1 min-w-0">{node.name}</span>
              {(() => {
                const annot = annotByCode.get(node.code);
                if (!annot) return null;
                const tag = actionTag(annot.action);
                if (!tag) return null;
                return (
                  <span
                    title={annot.reason ?? ""}
                    className={`ml-2 flex-shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium ${tag.className}`}
                  >
                    {tag.label}
                    {annot.action === "RENAMED" && annot.resolvedCode !== annot.originalCode
                      ? ` → ${annot.resolvedCode}`
                      : ""}
                  </span>
                );
              })()}
            </div>

            {hasChildren && isOpen && node.children && (
              <WbsAiTree
                nodes={node.children}
                level={level + 1}
                isLast={[...isLast, isLastNode]}
                annotByCode={annotByCode}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
