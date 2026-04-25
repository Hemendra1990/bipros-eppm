"use client";

import { useRef, useState } from "react";
import type {
  ContractAttachmentType,
  UploadContractAttachmentMetadata,
} from "@/lib/types";
import { ATTACHMENT_TYPE_OPTIONS } from "@/lib/contracts/contractTypeOptions";

interface AttachmentUploadFormProps {
  isPending: boolean;
  onSubmit: (metadata: UploadContractAttachmentMetadata, file: File) => void;
  onCancel?: () => void;
}

const inputClass =
  "w-full px-3 py-2 bg-surface-hover border border-border rounded-lg text-text-primary placeholder-text-muted focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50";
const labelClass = "block text-sm font-medium text-text-secondary mb-1";

export function AttachmentUploadForm({
  isPending,
  onSubmit,
  onCancel,
}: AttachmentUploadFormProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [attachmentType, setAttachmentType] =
    useState<ContractAttachmentType>("OTHER");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const file = fileInputRef.current?.files?.[0];
    if (!file) {
      setError("Please choose a file to upload.");
      return;
    }
    setError(null);
    onSubmit(
      {
        attachmentType,
        description: description.trim() === "" ? undefined : description.trim(),
      },
      file,
    );
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-surface/40 border border-border rounded-lg p-4 space-y-3"
    >
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>File *</label>
          <input
            ref={fileInputRef}
            type="file"
            required
            className={inputClass}
          />
        </div>
        <div>
          <label className={labelClass}>Document Type *</label>
          <select
            value={attachmentType}
            onChange={(e) =>
              setAttachmentType(e.target.value as ContractAttachmentType)
            }
            className={inputClass}
          >
            {ATTACHMENT_TYPE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div>
        <label className={labelClass}>Description</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          placeholder="Optional"
          className={inputClass}
        />
      </div>
      {error ? <p className="text-sm text-danger">{error}</p> : null}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={isPending}
          className="px-3 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600 disabled:bg-border text-sm transition-colors"
        >
          {isPending ? "Uploading…" : "Upload"}
        </button>
        {onCancel ? (
          <button
            type="button"
            onClick={onCancel}
            className="px-3 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border text-sm transition-colors"
          >
            Cancel
          </button>
        ) : null}
      </div>
    </form>
  );
}
