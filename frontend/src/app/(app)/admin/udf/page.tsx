"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Pencil, Trash2, X, Hash, DollarSign, Calendar, Type, Tag, ToggleLeft } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import {
  udfApi,
  type UdfDataType,
  type UdfSubject,
  type UdfScope,
  type UserDefinedFieldDto,
  type CreateUserDefinedFieldRequest,
} from "@/lib/api/udfApi";
import { projectApi } from "@/lib/api/projectApi";

const SUBJECTS: UdfSubject[] = ["ACTIVITY", "RESOURCE_ASSIGNMENT", "WBS", "PROJECT"];
const DATA_TYPES: UdfDataType[] = ["TEXT", "NUMBER", "COST", "DATE", "INDICATOR", "CODE"];

const SUBJECT_LABELS: Record<UdfSubject, string> = {
  ACTIVITY: "Activity",
  RESOURCE_ASSIGNMENT: "Resource Assignment",
  WBS: "WBS",
  PROJECT: "Project",
};

const DATA_TYPE_ICONS: Record<UdfDataType, typeof Type> = {
  TEXT: Type,
  NUMBER: Hash,
  COST: DollarSign,
  DATE: Calendar,
  INDICATOR: ToggleLeft,
  CODE: Tag,
};

type ScopeFilter = "GLOBAL" | "PROJECT";

export default function UdfAdminPage() {
  const queryClient = useQueryClient();
  const [selectedSubject, setSelectedSubject] = useState<UdfSubject>("ACTIVITY");
  // Scope filter: GLOBAL shows org-wide fields; PROJECT shows fields scoped to a chosen project.
  const [scopeFilter, setScopeFilter] = useState<ScopeFilter>("GLOBAL");
  const [filterProjectId, setFilterProjectId] = useState<string>("");
  const [showForm, setShowForm] = useState(false);
  const [editingField, setEditingField] = useState<UserDefinedFieldDto | null>(null);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  // Form state
  const [formName, setFormName] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formDataType, setFormDataType] = useState<UdfDataType>("TEXT");
  const [formScope, setFormScope] = useState<UdfScope>("GLOBAL");
  const [formDefaultValue, setFormDefaultValue] = useState("");
  const [formIsFormula, setFormIsFormula] = useState(false);
  const [formFormulaExpr, setFormFormulaExpr] = useState("");
  const [formSortOrder, setFormSortOrder] = useState(0);

  const { data: fieldsData, isLoading } = useQuery({
    queryKey: ["udf-fields", selectedSubject, scopeFilter, filterProjectId],
    queryFn: () => udfApi.listFields(
      selectedSubject,
      scopeFilter,
      scopeFilter === "PROJECT" && filterProjectId ? filterProjectId : undefined,
    ),
    // Don't hit the API for PROJECT scope with no project chosen — would return [] anyway.
    enabled: scopeFilter === "GLOBAL" || !!filterProjectId,
  });

  const { data: projectsData } = useQuery({
    queryKey: ["projects-for-udf-filter"],
    queryFn: () => projectApi.listProjects(0, 100),
    enabled: scopeFilter === "PROJECT",
    select: (r) => r.data?.content ?? [],
  });

  const fields: UserDefinedFieldDto[] = fieldsData?.data ?? [];

  const resetForm = () => {
    setFormName("");
    setFormDescription("");
    setFormDataType("TEXT");
    setFormScope("GLOBAL");
    setFormDefaultValue("");
    setFormIsFormula(false);
    setFormFormulaExpr("");
    setFormSortOrder(0);
    setEditingField(null);
    setError("");
  };

  const openCreateForm = () => {
    resetForm();
    setShowForm(true);
  };

  const openEditForm = (field: UserDefinedFieldDto) => {
    setEditingField(field);
    setFormName(field.name);
    setFormDescription(field.description ?? "");
    setFormDataType(field.dataType);
    setFormScope(field.scope);
    setFormDefaultValue(field.defaultValue ?? "");
    setFormIsFormula(field.isFormula);
    setFormFormulaExpr(field.formulaExpression ?? "");
    setFormSortOrder(field.sortOrder);
    setShowForm(true);
  };

  const handleSave = async () => {
    if (!formName.trim()) {
      setError("Name is required");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const data: CreateUserDefinedFieldRequest = {
        name: formName,
        description: formDescription || undefined,
        dataType: formDataType,
        subject: selectedSubject,
        scope: formScope,
        defaultValue: formDefaultValue || undefined,
        isFormula: formIsFormula || undefined,
        formulaExpression: formIsFormula ? formFormulaExpr : undefined,
        sortOrder: formSortOrder,
      };

      if (editingField) {
        await udfApi.updateField(editingField.id, data);
      } else {
        await udfApi.createField(data);
      }

      queryClient.invalidateQueries({ queryKey: ["udf-fields", selectedSubject] });
      setShowForm(false);
      resetForm();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to save field";
      setError(msg);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (fieldId: string) => {
    try {
      await udfApi.deleteField(fieldId);
      queryClient.invalidateQueries({ queryKey: ["udf-fields", selectedSubject] });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to delete field";
      setError(msg);
    }
  };

  const inputClass =
    "mt-1 block w-full rounded-md border border-border bg-surface-hover/50 px-3 py-2 text-text-primary placeholder-gray-500 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent text-sm";

  return (
    <div>
      <PageHeader
        title="User Defined Fields"
        description="Define custom fields for activities, WBS, projects, and resource assignments"
        actions={
          <button
            onClick={openCreateForm}
            className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
          >
            <Plus size={16} />
            New Field
          </button>
        }
      />

      {error && !showForm && (
        <div className="mb-4 rounded-md bg-danger/10 p-3 text-sm text-danger">
          {error}
        </div>
      )}

      {/* Subject Filter Tabs */}
      <div className="mb-4 flex gap-1 rounded-lg bg-surface-hover/50 p-1">
        {SUBJECTS.map((subject) => (
          <button
            key={subject}
            onClick={() => setSelectedSubject(subject)}
            className={`rounded-md px-4 py-2 text-sm font-medium transition-colors ${
              selectedSubject === subject
                ? "bg-accent text-text-primary"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            {SUBJECT_LABELS[subject]}
          </button>
        ))}
      </div>

      {/* Scope filter: GLOBAL vs per-project */}
      <div className="mb-6 flex flex-wrap items-center gap-3 text-sm">
        <span className="text-text-secondary">Scope:</span>
        <div className="flex gap-1 rounded-md bg-surface-hover/50 p-1">
          {(["GLOBAL", "PROJECT"] as const).map((s) => (
            <button
              key={s}
              onClick={() => setScopeFilter(s)}
              className={`rounded-sm px-3 py-1.5 text-xs font-medium transition-colors ${
                scopeFilter === s ? "bg-accent text-text-primary" : "text-text-secondary hover:text-text-primary"
              }`}
            >
              {s === "GLOBAL" ? "Global (org-wide)" : "Per-project"}
            </button>
          ))}
        </div>
        {scopeFilter === "PROJECT" && (
          <select
            value={filterProjectId}
            onChange={(e) => setFilterProjectId(e.target.value)}
            className="rounded-md border border-border bg-surface-hover/50 px-3 py-1.5 text-xs text-text-primary focus:border-accent focus:outline-none"
          >
            <option value="">-- Select a project --</option>
            {(projectsData ?? []).map((p) => (
              <option key={p.id} value={p.id}>{p.code} — {p.name}</option>
            ))}
          </select>
        )}
      </div>

      {/* Create/Edit Form Modal */}
      {showForm && (
        <div className="mb-6 rounded-lg border border-border bg-surface/80 p-6">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-text-primary">
              {editingField ? "Edit Field" : "New Field"}
            </h3>
            <button
              onClick={() => {
                setShowForm(false);
                resetForm();
              }}
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
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                placeholder="e.g., Risk Score"
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-text-secondary">
                Data Type
              </label>
              <select
                value={formDataType}
                onChange={(e) => setFormDataType(e.target.value as UdfDataType)}
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
                value={formScope}
                onChange={(e) => setFormScope(e.target.value as UdfScope)}
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
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
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
                value={formSortOrder}
                onChange={(e) => setFormSortOrder(parseInt(e.target.value, 10) || 0)}
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-text-secondary">
                Default Value
              </label>
              <input
                type="text"
                value={formDefaultValue}
                onChange={(e) => setFormDefaultValue(e.target.value)}
                placeholder="Optional"
                className={inputClass}
              />
            </div>
            <div className="flex items-end gap-3">
              <label className="flex items-center gap-2 text-sm text-text-secondary">
                <input
                  type="checkbox"
                  checked={formIsFormula}
                  onChange={(e) => setFormIsFormula(e.target.checked)}
                  className="rounded border-border"
                />
                Formula Field
              </label>
            </div>
            {formIsFormula && (
              <div className="col-span-3">
                <label className="block text-xs font-medium text-text-secondary">
                  Formula Expression
                </label>
                <input
                  type="text"
                  value={formFormulaExpr}
                  onChange={(e) => setFormFormulaExpr(e.target.value)}
                  placeholder="e.g., field1 + field2"
                  className={inputClass}
                />
              </div>
            )}
          </div>

          <div className="mt-4 flex gap-3">
            <button
              onClick={handleSave}
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
              onClick={() => {
                setShowForm(false);
                resetForm();
              }}
              className="rounded-md bg-surface-active/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-active"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Fields List */}
      {isLoading && (
        <div className="py-12 text-center text-text-muted">Loading fields...</div>
      )}

      {!isLoading && fields.length === 0 && !showForm && (
        <EmptyState
          title="No fields defined"
          description={
            scopeFilter === "PROJECT" && !filterProjectId
              ? "Select a project above to view its scoped UDFs."
              : `No ${scopeFilter === "PROJECT" ? "project-scoped" : "global"} user defined fields for ${SUBJECT_LABELS[selectedSubject]} yet. Create one to get started.`
          }
        />
      )}

      {fields.length > 0 && (
        <div className="space-y-2">
          {fields.map((field) => {
            const Icon = DATA_TYPE_ICONS[field.dataType];
            return (
              <div
                key={field.id}
                className="flex items-center justify-between rounded-lg border border-border bg-surface/50 px-5 py-4"
              >
                <div className="flex items-center gap-4">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-surface-hover">
                    <Icon size={16} className="text-accent" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-text-primary">
                        {field.name}
                      </span>
                      <span className="rounded bg-surface-hover px-1.5 py-0.5 text-xs text-text-secondary">
                        {field.dataType}
                      </span>
                      <span className="rounded bg-surface-hover px-1.5 py-0.5 text-xs text-text-muted">
                        {field.scope}
                      </span>
                      {field.isFormula && (
                        <span className="rounded bg-purple-500/10 px-1.5 py-0.5 text-xs text-purple-400">
                          Formula
                        </span>
                      )}
                    </div>
                    {field.description && (
                      <p className="mt-0.5 text-xs text-text-muted">
                        {field.description}
                      </p>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-1">
                  <button
                    onClick={() => openEditForm(field)}
                    className="rounded p-2 text-text-muted hover:bg-surface-hover hover:text-accent"
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    onClick={() => handleDelete(field.id)}
                    className="rounded p-2 text-text-muted hover:bg-danger/10 hover:text-danger"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
