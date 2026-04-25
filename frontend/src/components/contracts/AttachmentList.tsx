"use client";

import type { ContractAttachment } from "@/lib/types";
import { ATTACHMENT_TYPE_LABELS } from "@/lib/contracts/contractTypeOptions";

interface AttachmentListProps {
  attachments: ContractAttachment[];
  /** Renders a download button per row. */
  onDownload: (attachment: ContractAttachment) => void;
  /** Optional delete handler. Hide the button by passing undefined. */
  onDelete?: (attachment: ContractAttachment) => void;
  /** Optional groupings: render under headers when provided. */
  groupBy?: "entityType" | "none";
  emptyText?: string;
}

const entityGroupLabel: Record<ContractAttachment["entityType"], string> = {
  CONTRACT: "Contract",
  MILESTONE: "Milestones",
  VARIATION_ORDER: "Variation Orders",
  PERFORMANCE_BOND: "Performance Bonds",
};

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function AttachmentList({
  attachments,
  onDownload,
  onDelete,
  groupBy = "none",
  emptyText = "No attachments yet.",
}: AttachmentListProps) {
  if (attachments.length === 0) {
    return <p className="text-sm text-text-muted py-2">{emptyText}</p>;
  }

  const grouped =
    groupBy === "entityType"
      ? Array.from(
          attachments.reduce((acc, a) => {
            const key = a.entityType;
            if (!acc.has(key)) acc.set(key, []);
            acc.get(key)!.push(a);
            return acc;
          }, new Map<ContractAttachment["entityType"], ContractAttachment[]>()),
        )
      : ([["CONTRACT", attachments] as const] satisfies ReadonlyArray<
          readonly [ContractAttachment["entityType"], ContractAttachment[]]
        >);

  return (
    <div className="space-y-4">
      {grouped.map(([type, rows]) => (
        <div key={type}>
          {groupBy === "entityType" ? (
            <h4 className="text-xs uppercase tracking-wide text-text-muted font-semibold mb-2">
              {entityGroupLabel[type]} ({rows.length})
            </h4>
          ) : null}
          <ul className="divide-y divide-border/60 border border-border/60 rounded-lg overflow-hidden">
            {rows.map((a) => (
              <li
                key={a.id}
                className="flex items-center gap-3 px-3 py-2 bg-surface/40"
              >
                <span aria-hidden className="text-lg">
                  📎
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-text-primary truncate">
                    {a.fileName}
                  </p>
                  <p className="text-xs text-text-muted truncate">
                    {ATTACHMENT_TYPE_LABELS[a.attachmentType] ?? a.attachmentType}
                    {" · "}
                    {formatBytes(a.fileSize)}
                    {a.uploadedAt
                      ? ` · ${new Date(a.uploadedAt).toLocaleString()}`
                      : ""}
                    {a.uploadedBy ? ` · ${a.uploadedBy}` : ""}
                  </p>
                  {a.description ? (
                    <p className="text-xs text-text-secondary mt-0.5">
                      {a.description}
                    </p>
                  ) : null}
                </div>
                <button
                  onClick={() => onDownload(a)}
                  className="px-2 py-1 text-sm font-medium text-accent hover:text-blue-300"
                >
                  Download
                </button>
                {onDelete ? (
                  <button
                    onClick={() => onDelete(a)}
                    className="px-2 py-1 text-sm font-medium text-danger hover:text-red-300"
                  >
                    Delete
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
