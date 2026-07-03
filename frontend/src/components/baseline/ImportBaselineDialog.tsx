"use client";

import React, { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Upload, FileText, X, Download } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogBody, DialogFooter } from "@/components/ui/dialog";
import { baselineApi, type ImportFormat, type ImportPreview } from "@/lib/api/baselineApi";
import { getErrorMessage } from "@/lib/utils/error";
import { useProjectCurrency } from "@/lib/currency/ProjectCurrencyProvider";

const MAX_BYTES = 50 * 1024 * 1024;

// Phase 1: only EXCEL and XER import are wired up on the backend. The rest are shown
// in the selector (so the supported format list is visible) but disabled for now.
const FORMAT_OPTIONS: {
  value: ImportFormat;
  shortLabel: string;
  label: string;
  ext: string;
  enabled: boolean;
}[] = [
  { value: "EXCEL", shortLabel: "Excel", label: "Excel (.xlsx)", ext: ".xlsx", enabled: true },
  { value: "XER", shortLabel: "Primavera XER", label: "Primavera XER (.xer)", ext: ".xer", enabled: true },
  { value: "P6XML", shortLabel: "Primavera P6 XML", label: "Primavera P6 XML (.xml)", ext: ".xml", enabled: false },
  { value: "MSP_XML", shortLabel: "MS-Project XML", label: "MS-Project XML (.xml)", ext: ".xml", enabled: false },
  { value: "CSV", shortLabel: "CSV", label: "CSV (.csv)", ext: ".csv", enabled: false },
];

interface ImportBaselineDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string;
  onImported: () => void;
}

export function ImportBaselineDialog({ open, onOpenChange, projectId, onImported }: ImportBaselineDialogProps) {
  const queryClient = useQueryClient();
  const { money } = useProjectCurrency();
  const inputRef = useRef<HTMLInputElement>(null);
  const [format, setFormat] = useState<ImportFormat>("EXCEL");
  const [file, setFile] = useState<File | null>(null);
  const [name, setName] = useState("");
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedFormat = FORMAT_OPTIONS.find((o) => o.value === format)!;

  const reset = () => {
    setFormat("EXCEL");
    setFile(null);
    setName("");
    setPreview(null);
    setErrorMessage(null);
  };

  const close = () => {
    reset();
    onOpenChange(false);
  };

  const pick = (chosen: File) => {
    const lower = chosen.name.toLowerCase();
    if (!lower.endsWith(selectedFormat.ext)) {
      toast.error(`Only ${selectedFormat.ext} files are supported for ${selectedFormat.shortLabel}`);
      return;
    }
    if (chosen.size > MAX_BYTES) {
      toast.error("File exceeds the 50 MB limit");
      return;
    }
    setFile(chosen);
    if (!name) setName(chosen.name.replace(/\.[^.]+$/, ""));
    setErrorMessage(null);
  };

  const previewMutation = useMutation({
    mutationFn: () => baselineApi.previewImport(projectId, file!, format),
    onSuccess: (res) => {
      setPreview(res.data ?? null);
      setErrorMessage(null);
    },
    onError: (err: unknown) => {
      const message = getErrorMessage(err, "Failed to preview the file");
      setErrorMessage(message);
      toast.error(message);
    },
  });

  const importMutation = useMutation({
    mutationFn: () =>
      baselineApi.importBaseline(projectId, {
        file: file!,
        format,
        name,
        type: "PRIMARY",
        description: `Imported from ${file!.name}`,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["baselines", projectId] });
      queryClient.invalidateQueries({ queryKey: ["project", projectId] });
      queryClient.invalidateQueries({ queryKey: ["activities", projectId] });
      queryClient.invalidateQueries({ queryKey: ["relationships", projectId] });
      toast.success("Baseline imported");
      onImported();
      close();
    },
    onError: (err: unknown) => {
      const message = getErrorMessage(err, "Failed to import baseline");
      setErrorMessage(message);
      toast.error(message);
    },
  });

  const downloadTemplateMutation = useMutation({
    mutationFn: () => baselineApi.downloadTemplate(projectId, format),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `baseline-import-template${selectedFormat.ext}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    },
    onError: (err: unknown) => toast.error(getErrorMessage(err, "Failed to download template")),
  });

  return (
    <Dialog open={open} onOpenChange={(o) => (o ? onOpenChange(o) : close())}>
      <DialogContent className="max-w-2xl max-h-[85vh] flex flex-col">
        <DialogHeader>
          <DialogTitle>Import Baseline ({selectedFormat.shortLabel})</DialogTitle>
        </DialogHeader>
        <DialogBody className="flex-1 overflow-y-auto space-y-4">
          {!preview && (
            <>
              <div>
                <label className="mb-1 block text-sm text-text-secondary">Baseline name</label>
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Client Approved Programme"
                  className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm text-text-secondary">Format</label>
                <select
                  value={format}
                  onChange={(e) => {
                    setFormat(e.target.value as ImportFormat);
                    setFile(null);
                    setErrorMessage(null);
                  }}
                  className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
                >
                  {FORMAT_OPTIONS.filter((opt) => opt.enabled).map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex items-center gap-2">
                <div className="flex-1 rounded-md border border-dashed border-border p-6 text-center">
                  <input
                    ref={inputRef}
                    type="file"
                    accept={selectedFormat.ext}
                    className="hidden"
                    onChange={(e) => {
                      const c = e.target.files?.[0];
                      if (c) pick(c);
                      e.target.value = "";
                    }}
                  />
                  {file ? (
                    <div className="inline-flex items-center gap-2 text-sm text-text-primary">
                      <FileText size={16} />
                      {file.name}
                      <button type="button" onClick={() => setFile(null)} aria-label="Remove file">
                        <X size={14} />
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => inputRef.current?.click()}
                      className="inline-flex items-center gap-2 text-sm text-text-secondary hover:text-text-primary"
                    >
                      <Upload size={16} />
                      Choose a {selectedFormat.ext} file
                    </button>
                  )}
                </div>
                <button
                  type="button"
                  disabled={format !== "EXCEL" || downloadTemplateMutation.isPending}
                  onClick={() => downloadTemplateMutation.mutate()}
                  title={format === "EXCEL" ? "Download a blank Excel template" : "Template available for Excel"}
                  className="inline-flex shrink-0 items-center gap-2 rounded-md border border-border px-3 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50 disabled:opacity-50"
                >
                  <Download size={16} />
                  {downloadTemplateMutation.isPending ? "Downloading…" : "Download template"}
                </button>
              </div>
            </>
          )}
          {preview && (
            <div className="space-y-2 text-sm">
              <div className="grid grid-cols-3 gap-2">
                <Stat label="Activities" value={preview.activitiesInFile} />
                <Stat label="Matched" value={preview.matched} />
                <Stat label="New" value={preview.newActivities} />
                <Stat label="In project, not in file" value={preview.missingInFile} />
                <Stat label="WBS nodes" value={preview.wbsNodes} />
                <Stat label="Relationships" value={preview.relationships} />
              </div>
              <div className="text-text-secondary">
                Dates: {preview.dateRangeStart ?? "—"} → {preview.dateRangeFinish ?? "—"}
                {preview.totalPlannedCost != null && Number(preview.totalPlannedCost) > 0 && (
                  <> · Total planned cost: {money(preview.totalPlannedCost)}</>
                )}
              </div>
              {preview.warnings.map((w, i) => (
                <div key={i} className="rounded bg-warning/10 px-3 py-2 text-warning">
                  ⚠ {w}
                </div>
              ))}
              {preview.resources &&
                (preview.resources.manpowerRows > 0 ||
                  preview.resources.equipmentRows > 0 ||
                  preview.resources.materialRows > 0 ||
                  preview.resources.subContractorRows > 0) && (
                  <div className="space-y-1 rounded-md border border-border bg-surface/60 px-3 py-2">
                    <div className="text-xs font-medium text-text-secondary">Resource plan</div>
                    {preview.resources.manpowerRows > 0 && (
                      <div className="text-text-secondary">
                        Manpower: {preview.resources.manpowerApplied} of {preview.resources.manpowerRows} applied
                      </div>
                    )}
                    {preview.resources.equipmentRows > 0 && (
                      <div className="text-text-secondary">
                        Equipment: {preview.resources.equipmentApplied} of {preview.resources.equipmentRows} applied
                      </div>
                    )}
                    {preview.resources.materialRows > 0 && (
                      <div className="text-text-secondary">
                        Material: {preview.resources.materialApplied} of {preview.resources.materialRows} applied
                      </div>
                    )}
                    {preview.resources.subContractorRows > 0 && (
                      <div className="text-text-secondary">
                        Sub-contractor: {preview.resources.subContractorApplied} of{" "}
                        {preview.resources.subContractorRows} applied
                      </div>
                    )}
                    {preview.resources.warnings.map((w, i) => (
                      <div key={i} className="rounded bg-warning/10 px-3 py-2 text-warning">
                        ⚠ {w}
                      </div>
                    ))}
                  </div>
                )}
            </div>
          )}
          {errorMessage && (
            <div className="rounded-md bg-danger/10 px-3 py-2 text-sm text-danger">{errorMessage}</div>
          )}
        </DialogBody>
        <DialogFooter>
          {!preview ? (
            <button
              type="button"
              disabled={!file || previewMutation.isPending}
              onClick={() => previewMutation.mutate()}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
            >
              {previewMutation.isPending ? "Reading…" : "Preview"}
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={() => setPreview(null)}
                className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
              >
                Back
              </button>
              <button
                type="button"
                disabled={importMutation.isPending || !name.trim()}
                onClick={() => importMutation.mutate()}
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
              >
                {importMutation.isPending ? "Importing…" : "Import as baseline"}
              </button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-md border border-border bg-surface/60 px-3 py-2">
      <div className="text-lg font-semibold text-text-primary">{value}</div>
      <div className="text-xs text-text-secondary">{label}</div>
    </div>
  );
}
