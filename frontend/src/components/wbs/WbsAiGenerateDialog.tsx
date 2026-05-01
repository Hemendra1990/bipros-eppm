"use client";

import React, { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Sparkles, RefreshCw, ChevronRight, ChevronDown, Folder, FolderOpen, File, Loader2 } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogBody, DialogFooter } from "@/components/ui/dialog";
import { aiApi } from "@/lib/api/aiApi";
import { getErrorMessage } from "@/lib/utils/error";
import toast from "react-hot-toast";
import type { AssetClass, WbsAiNode, WbsAiGenerationResponse, ProjectResponse } from "@/lib/types";

const ASSET_CLASSES: AssetClass[] = [
  "ROAD",
  "RAIL",
  "POWER",
  "WATER",
  "ICT",
  "BUILDING",
  "GREEN_INFRASTRUCTURE",
];

interface WbsAiGenerateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  project: ProjectResponse;
}

type Phase = "setup" | "generating" | "preview";

export function WbsAiGenerateDialog({ open, onOpenChange, project }: WbsAiGenerateDialogProps) {
  const queryClient = useQueryClient();

  const industryCode = project.industryCode;
  const detectedAssetClass = industryCode
    ? ASSET_CLASSES.find((ac) => ac === industryCode.toUpperCase()) ?? null
    : null;

  const [phase, setPhase] = useState<Phase>("setup");
  const [assetClass, setAssetClass] = useState<AssetClass | null>(detectedAssetClass);
  const [projectTypeHint, setProjectTypeHint] = useState("");
  const [additionalContext, setAdditionalContext] = useState("");
  const [targetDepth, setTargetDepth] = useState(3);
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

  const applyMutation = useMutation({
    mutationFn: () =>
      aiApi.applyGeneratedWbs(project.id, {
        parentId: null,
        nodes: generationResult?.nodes ?? [],
      }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["wbs", project.id] });
      const collisionCount = data.data?.length ?? 0;
      if (collisionCount > 0) {
        toast.success(`WBS applied with ${collisionCount} code collision(s) resolved`);
      } else {
        toast.success("AI-generated WBS applied successfully");
      }
      handleClose();
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to apply WBS"));
    },
  });

  const handleClose = () => {
    setPhase("setup");
    setGenerationResult(null);
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
            <SetupPhase
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
          )}

          {phase === "generating" && <GeneratingPhase />}

          {phase === "preview" && generationResult && (
            <PreviewPhase rationale={generationResult.rationale} nodes={generationResult.nodes} />
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
              <button
                onClick={handleGenerate}
                disabled={!assetClass || generateMutation.isPending}
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
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function SetupPhase({
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

function GeneratingPhase() {
  return (
    <div className="flex flex-col items-center justify-center py-12 gap-4">
      <Loader2 size={40} className="animate-spin text-accent" />
      <div className="text-center">
        <p className="text-sm font-medium text-text-primary">Designing WBS structure...</p>
        <p className="mt-1 text-xs text-text-muted">This may take a few moments</p>
      </div>
    </div>
  );
}

function PreviewPhase({ rationale, nodes }: { rationale: string; nodes: WbsAiNode[] }) {
  return (
    <div className="space-y-4">
      {rationale && (
        <div className="rounded-lg border border-border bg-surface-hover/30 p-3">
          <p className="text-xs font-medium text-text-secondary mb-1">AI Rationale</p>
          <p className="text-sm text-text-primary">{rationale}</p>
        </div>
      )}
      <div>
        <p className="text-xs font-medium text-text-secondary mb-2">Generated Structure</p>
        <div className="rounded-lg border border-border bg-surface/50 p-3 font-mono text-sm max-h-80 overflow-y-auto">
          <WbsAiTree nodes={nodes} level={0} isLast={[]} />
        </div>
      </div>
    </div>
  );
}

function WbsAiTree({ nodes, level, isLast }: { nodes: WbsAiNode[]; level: number; isLast: boolean[] }) {
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
              <span className="text-text-secondary truncate">{node.name}</span>
            </div>

            {hasChildren && isOpen && node.children && (
              <WbsAiTree
                nodes={node.children}
                level={level + 1}
                isLast={[...isLast, isLastNode]}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
