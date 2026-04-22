"use client";

import { useState } from "react";
import { X } from "lucide-react";
import type {
  UdfDataType,
  UdfSubject,
  UdfScope,
  UserDefinedFieldDto,
  CreateUserDefinedFieldRequest,
} from "@/lib/api/udfApi";

const DATA_TYPES: UdfDataType[] = [
  "TEXT",
  "NUMBER",
  "COST",
  "DATE",
  "INDICATOR",
  "CODE",
];

interface UdfFieldFormProps {
  subject: UdfSubject;
  editingField?: UserDefinedFieldDto | null;
  onSave: (data: CreateUserDefinedFieldRequest) => Promise<void>;
  onCancel: () => void;
}

export function UdfFieldForm({
  subject,
  editingField,
  onSave,
  onCancel,
}: UdfFieldFormProps) {
  const [name, setName] = useState(editingField?.name ?? "");
  const [description, setDescription] = useState(
    editingField?.description ?? ""
  );
  const [dataType, setDataType] = useState<UdfDataType>(
    editingField?.dataType ?? "TEXT"
  );
  const [scope, setScope] = useState<UdfScope>(
    editingField?.scope ?? "GLOBAL"
  );
  const [defaultValue, setDefaultValue] = useState(
    editingField?.defaultValue ?? ""
  );
  const [isFormula, setIsFormula] = useState(editingField?.isFormula ?? false);
  const [formulaExpr, setFormulaExpr] = useState(
    editingField?.formulaExpression ?? ""
  );
  const [sortOrder, setSortOrder] = useState(editingField?.sortOrder ?? 0);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const handleSubmit = async () => {
    if (!name.trim()) {
      setError("Name is required");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await onSave({
        name,
        description: description || undefined,
        dataType,
        subject,
        scope,
        defaultValue: defaultValue || undefined,
        isFormula: isFormula || undefined,
        formulaExpression: isFormula ? formulaExpr : undefined,
        sortOrder,
      });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to save field");
    } finally {
      setSaving(false);
    }
  };

  const inputClass =
    "mt-1 block w-full rounded-md border border-border bg-surface-hover/50 px-3 py-2 text-text-primary placeholder-gray-500 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent text-sm";

  return (
    <div className="rounded-lg border border-border bg-surface/80 p-6">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-text-primary">
          {editingField ? "Edit Field" : "New Field"}
        </h3>
        <button
          onClick={onCancel}
          className="rounded p-1 text-text-secondary hover:text-text-primary"
        >
          <X size={16} />
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-md bg-danger/10 p-3 text-sm text-danger">
          {error}
        </div>
      )}

      <div className="grid grid-cols-3 gap-4">
        <div>
          <label className="block text-xs font-medium text-text-secondary">
            Name *
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g., Risk Score"
            className={inputClass}
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-text-secondary">
            Data Type
          </label>
          <select
            value={dataType}
            onChange={(e) => setDataType(e.target.value as UdfDataType)}
            className={inputClass}
          >
            {DATA_TYPES.map((dt) => (
              <option key={dt} value={dt}>
                {dt}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-text-secondary">
            Scope
          </label>
          <select
            value={scope}
            onChange={(e) => setScope(e.target.value as UdfScope)}
            className={inputClass}
          >
            <option value="GLOBAL">Global</option>
            <option value="PROJECT">Project</option>
          </select>
        </div>
        <div className="col-span-2">
          <label className="block text-xs font-medium text-text-secondary">
            Description
          </label>
          <input
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional description"
            className={inputClass}
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-text-secondary">
            Sort Order
          </label>
          <input
            type="number"
            value={sortOrder}
            onChange={(e) => setSortOrder(parseInt(e.target.value, 10) || 0)}
            className={inputClass}
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-text-secondary">
            Default Value
          </label>
          <input
            type="text"
            value={defaultValue}
            onChange={(e) => setDefaultValue(e.target.value)}
            placeholder="Optional"
            className={inputClass}
          />
        </div>
        <div className="flex items-end gap-3">
          <label className="flex items-center gap-2 text-sm text-text-secondary">
            <input
              type="checkbox"
              checked={isFormula}
              onChange={(e) => setIsFormula(e.target.checked)}
              className="rounded border-border"
            />
            Formula Field
          </label>
        </div>
        {isFormula && (
          <div className="col-span-3">
            <label className="block text-xs font-medium text-text-secondary">
              Formula Expression
            </label>
            <textarea
              value={formulaExpr}
              onChange={(e) => setFormulaExpr(e.target.value)}
              placeholder="e.g., field1 + field2"
              rows={2}
              className={`${inputClass} font-mono`}
            />
          </div>
        )}
      </div>

      <div className="mt-4 flex gap-3">
        <button
          onClick={handleSubmit}
          disabled={saving}
          className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
        >
          {saving
            ? "Saving..."
            : editingField
              ? "Update Field"
              : "Create Field"}
        </button>
        <button
          onClick={onCancel}
          className="rounded-md bg-surface-active/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-active"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}
