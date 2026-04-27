"use client";

import { useState } from "react";

interface Props {
  label: string;
  field: string;
  value: string;
  onSave: (value: string) => void;
}

export function RiskDescriptionTab({ label, value, onSave }: Props) {
  const [text, setText] = useState(value);
  const [hasChanges, setHasChanges] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setText(e.target.value);
    setHasChanges(true);
  };

  const handleSave = () => {
    onSave(text);
    setHasChanges(false);
  };

  const handleCancel = () => {
    setText(value);
    setHasChanges(false);
  };

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
        <h3 className="text-lg font-semibold text-text-primary mb-4">{label}</h3>
        <textarea
          className="w-full px-4 py-3 bg-surface-hover border border-border rounded-lg text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent min-h-[200px]"
          value={text}
          onChange={handleChange}
          placeholder={`Enter ${label.toLowerCase()}...`}
        />
        {hasChanges && (
          <div className="flex gap-2 mt-3">
            <button
              onClick={handleSave}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
            >
              Save
            </button>
            <button
              onClick={handleCancel}
              className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
            >
              Cancel
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
