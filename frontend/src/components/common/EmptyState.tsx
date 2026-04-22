import React from "react";
import { LucideIcon } from "lucide-react";

interface EmptyStateProps {
  icon?: LucideIcon;
  title: string;
  description: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}

export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="rounded-xl border border-dashed border-border bg-surface/30 py-16 text-center">
      {Icon && <Icon className="mx-auto h-12 w-12 text-text-muted" />}
      <h3 className="mt-2 text-sm font-medium text-text-secondary">{title}</h3>
      <p className="mt-1 text-sm text-text-muted">{description}</p>
      {action && (
        <button
          onClick={action.onClick}
          className="mt-4 inline-flex items-center rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          {action.label}
        </button>
      )}
    </div>
  );
}
