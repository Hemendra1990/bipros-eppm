"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { wbsTemplateApi } from "@/lib/api/wbsTemplateApi";
import { getErrorMessage } from "@/lib/utils/error";
import { PageHeader } from "@/components/common/PageHeader";
import type {
  AssetClass,
  CreateWbsTemplateRequest,
  WbsTemplateResponse,
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

interface TemplateFormData {
  code: string;
  name: string;
  assetClass: AssetClass;
  description: string;
  defaultStructure: string;
  isActive: boolean;
}

export default function WbsTemplatesPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<WbsTemplateResponse | null>(null);
  const [showStructureViewer, setShowStructureViewer] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [formData, setFormData] = useState<TemplateFormData>({
    code: "",
    name: "",
    assetClass: "ROAD",
    description: "",
    defaultStructure: "[]",
    isActive: true,
  });

  const { data: templatesData, isLoading } = useQuery({
    queryKey: ["wbs-templates"],
    queryFn: () => wbsTemplateApi.listTemplates(),
  });

  const templates = templatesData?.data ?? [];

  const createMutation = useMutation({
    mutationFn: (data: CreateWbsTemplateRequest) =>
      wbsTemplateApi.createTemplate(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wbs-templates"] });
      setSuccess("WBS template created successfully");
      setShowForm(false);
      setFormData({
        code: "",
        name: "",
        assetClass: "ROAD",
        description: "",
        defaultStructure: "[]",
        isActive: true,
      });
      setTimeout(() => setSuccess(""), 3000);
    },
    onError: (err: unknown) => {
      setError(getErrorMessage(err, "Failed to create template"));
    },
  });

  const handleInputChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value, type } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? (e.target as HTMLInputElement).checked : value,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    // Validate JSON
    try {
      JSON.parse(formData.defaultStructure);
    } catch {
      setError("Default structure must be valid JSON");
      return;
    }

    createMutation.mutate(formData as CreateWbsTemplateRequest);
  };

  if (isLoading) {
    return <div className="text-center py-8 text-text-muted">Loading templates...</div>;
  }

  return (
    <div>
      <PageHeader
        title="WBS Templates"
        description="Manage predefined Work Breakdown Structure templates for different asset classes"
      />

      {error && (
        <div className="mb-4 rounded-md bg-danger/10 p-4 text-sm text-danger">{error}</div>
      )}
      {success && (
        <div className="mb-4 rounded-md bg-success/10 p-4 text-sm text-success">
          {success}
        </div>
      )}

      <div className="mb-6">
        <button
          onClick={() => setShowForm(!showForm)}
          className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          {showForm ? "Cancel" : "Create Template"}
        </button>
      </div>

      {showForm && (
        <div className="mb-8 rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-text-primary">Create New WBS Template</h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Code <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  name="code"
                  value={formData.code}
                  onChange={handleInputChange}
                  required
                  className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  placeholder="e.g., ROAD, BUILDING"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Name <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  name="name"
                  value={formData.name}
                  onChange={handleInputChange}
                  required
                  className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  placeholder="e.g., Road Infrastructure Project"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Asset Class <span className="text-red-500">*</span>
                </label>
                <select
                  name="assetClass"
                  value={formData.assetClass}
                  onChange={handleInputChange}
                  className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                >
                  {ASSET_CLASSES.map((ac) => (
                    <option key={ac} value={ac}>
                      {ac}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex items-center">
                <label className="flex items-center">
                  <input
                    type="checkbox"
                    name="isActive"
                    checked={formData.isActive}
                    onChange={handleInputChange}
                    className="h-4 w-4 rounded border-border text-accent"
                  />
                  <span className="ml-2 text-sm text-text-secondary">Active</span>
                </label>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">
                Description
              </label>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleInputChange}
                className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                rows={3}
                placeholder="Template description..."
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">
                Default Structure (JSON) <span className="text-red-500">*</span>
              </label>
              <textarea
                name="defaultStructure"
                value={formData.defaultStructure}
                onChange={handleInputChange}
                required
                className="mt-1 block w-full rounded-md border border-border px-3 py-2 font-mono text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                rows={8}
                placeholder='[{"code":"ROOT","name":"Project","level":0,"children":[...]}]'
              />
            </div>

            <div className="flex gap-3 pt-4">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
              >
                {createMutation.isPending ? "Creating..." : "Create Template"}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface/80"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="rounded-lg border border-border bg-surface/50 shadow-sm overflow-hidden">
        <table className="min-w-full divide-y divide-border/50">
          <thead className="bg-surface/80">
            <tr>
              <th className="px-6 py-3 text-left text-sm font-semibold text-text-primary">
                Code
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-text-primary">
                Name
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-text-primary">
                Asset Class
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-text-primary">
                Status
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-text-primary">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/50">
            {templates.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-6 py-8 text-center text-sm text-text-muted">
                  No templates found
                </td>
              </tr>
            ) : (
              templates.map((template) => (
                <tr key={template.id} className="hover:bg-surface/80">
                  <td className="px-6 py-4 text-sm font-medium text-text-primary">
                    {template.code}
                  </td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{template.name}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{template.assetClass}</td>
                  <td className="px-6 py-4 text-sm">
                    <span
                      className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ${
                        template.isActive
                          ? "bg-success/10 text-success"
                          : "bg-surface-hover/50 text-text-primary"
                      }`}
                    >
                      {template.isActive ? "Active" : "Inactive"}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm">
                    <button
                      onClick={() => {
                        setSelectedTemplate(template);
                        setShowStructureViewer(true);
                      }}
                      className="text-accent hover:text-accent"
                    >
                      View Structure
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {showStructureViewer && selectedTemplate && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-background bg-opacity-50 flex items-center justify-center">
          <div className="relative w-full max-w-2xl m-4 rounded-lg bg-surface/50 p-6 shadow-xl">
            <h2 className="mb-4 text-lg font-semibold text-text-primary">
              {selectedTemplate.name} - Structure
            </h2>
            <pre className="mb-4 overflow-auto max-h-96 rounded-md bg-surface-hover/50 p-4 text-sm text-text-primary">
              {JSON.stringify(JSON.parse(selectedTemplate.defaultStructure), null, 2)}
            </pre>
            <button
              onClick={() => {
                setShowStructureViewer(false);
                setSelectedTemplate(null);
              }}
              className="rounded-md bg-surface-active px-4 py-2 text-sm font-medium text-text-primary hover:bg-surface-hover"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
