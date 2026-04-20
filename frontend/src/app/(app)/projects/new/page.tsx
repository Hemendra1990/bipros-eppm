"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useMutation } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { projectApi } from "@/lib/api/projectApi";
import { obsApi } from "@/lib/api/obsApi";
import { getErrorMessage } from "@/lib/utils/error";
import { getPriorityInfo } from "@/lib/utils/format";
import { projectNotifications, notificationHelpers } from "@/lib/notificationHelpers";
import { Breadcrumb } from "@/components/common/Breadcrumb";
import type { CreateProjectRequest } from "@/lib/types";

export default function NewProjectPage() {
  const router = useRouter();
  const [formData, setFormData] = useState<CreateProjectRequest>({
    code: "",
    name: "",
    description: "",
    epsNodeId: "",
    plannedStartDate: "",
    plannedFinishDate: "",
    priority: 5,
  });

  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const { data: epsData, isLoading: isLoadingEps } = useQuery({
    queryKey: ["eps"],
    queryFn: () => projectApi.getEpsTree(),
  });

  const { data: obsData, isLoading: isLoadingObs } = useQuery({
    queryKey: ["obs"],
    queryFn: () => obsApi.getObsTree(),
  });

  const epsNodes = epsData?.data ?? [];
  const obsNodes = obsData?.data ?? [];

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    if (error) setError("");
    if (fieldErrors[name]) setFieldErrors((prev) => { const next = { ...prev }; delete next[name]; return next; });
    setFormData((prev) => ({
      ...prev,
      [name]: name === "priority" ? parseInt(value, 10) : value,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    const errors: Record<string, string> = {};
    if (!formData.code.trim()) errors.code = "Project code is required";
    if (!formData.name.trim()) errors.name = "Project name is required";
    if (!formData.epsNodeId) errors.epsNodeId = "EPS Node is required";
    if (!formData.plannedStartDate) errors.plannedStartDate = "Planned start date is required";
    if (!formData.plannedFinishDate) errors.plannedFinishDate = "Planned finish date is required";
    if (formData.plannedStartDate && formData.plannedFinishDate && formData.plannedStartDate > formData.plannedFinishDate) {
      errors.plannedFinishDate = "Finish date must be after start date";
    }
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setError("Please fix the highlighted fields");
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await projectApi.createProject(formData);
      if (result.data) {
        projectNotifications.created();
        router.push(`/projects/${result.data.id}`);
      }
    } catch (err: unknown) {
      const msg = getErrorMessage(err, "Failed to create project");
      const isDuplicate =
        (err instanceof Error && /already exists|duplicate|conflict/i.test(err.message)) ||
        (typeof err === "object" && err !== null && "status" in err && (err as { status: number }).status === 409);
      setError(isDuplicate ? `Project code "${formData.code}" already exists. Please use a different code.` : msg);
      notificationHelpers.handleApiError(err, isDuplicate ? "Duplicate project code" : "Failed to create project");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div>
      <div className="mb-4">
        <Breadcrumb items={[
          { label: "Projects", href: "/projects" },
          { label: "New Project", href: "/projects/new", active: true },
        ]} />
      </div>
      <PageHeader title="New Project" description="Create a new project to get started" />

      <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
        <form onSubmit={handleSubmit} className="space-y-6">
          {error && (
            <div className="flex items-center justify-between rounded-md bg-red-500/10 p-4 text-sm text-red-400">
              <span>{error}</span>
              <button type="button" onClick={() => setError("")} className="ml-3 text-red-400 hover:text-red-300">&times;</button>
            </div>
          )}

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">Code *</label>
              <input
                type="text"
                name="code"
                value={formData.code}
                onChange={handleChange}
                className={`mt-1 block w-full rounded-md border ${fieldErrors.code ? "border-red-500" : "border-slate-700"} bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500`}
                placeholder="e.g., PROJ-001"
              />
              {fieldErrors.code && <p className="mt-1 text-xs text-red-400">{fieldErrors.code}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Name *</label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                className={`mt-1 block w-full rounded-md border ${fieldErrors.name ? "border-red-500" : "border-slate-700"} bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500`}
                placeholder="e.g., Website Redesign"
              />
              {fieldErrors.name && <p className="mt-1 text-xs text-red-400">{fieldErrors.name}</p>}
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-300">Description</label>
            <textarea
              name="description"
              value={formData.description || ""}
              onChange={handleChange}
              rows={4}
              className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="Project description"
            />
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">EPS Node *</label>
              <SearchableSelect
                value={formData.epsNodeId}
                onChange={(val) => { if (error) setError(""); if (fieldErrors.epsNodeId) setFieldErrors((prev) => { const next = { ...prev }; delete next.epsNodeId; return next; }); setFormData((prev) => ({ ...prev, epsNodeId: val })); }}
                placeholder="Search EPS nodes..."
                options={epsNodes.map((node) => ({
                  value: node.id,
                  label: `${node.code} - ${node.name}`,
                }))}
                disabled={isLoadingEps}
              />
              {fieldErrors.epsNodeId && <p className="mt-1 text-xs text-red-400">{fieldErrors.epsNodeId}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">OBS Node</label>
              <SearchableSelect
                value={formData.obsNodeId || ""}
                onChange={(val) => { if (error) setError(""); setFormData((prev) => ({ ...prev, obsNodeId: val })); }}
                placeholder="Search OBS nodes..."
                options={obsNodes.map((node) => ({
                  value: node.id,
                  label: `${node.code} - ${node.name}`,
                }))}
                disabled={isLoadingObs}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">Priority</label>
              <select
                name="priority"
                value={formData.priority}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((p) => (
                  <option key={p} value={p}>
                    {p} - {getPriorityInfo(p).label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">Planned Start Date *</label>
              <input
                type="date"
                name="plannedStartDate"
                value={formData.plannedStartDate}
                onChange={handleChange}
                className={`mt-1 block w-full rounded-md border ${fieldErrors.plannedStartDate ? "border-red-500" : "border-slate-700"} bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500`}
              />
              {fieldErrors.plannedStartDate && <p className="mt-1 text-xs text-red-400">{fieldErrors.plannedStartDate}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Planned Finish Date *</label>
              <input
                type="date"
                name="plannedFinishDate"
                value={formData.plannedFinishDate || ""}
                onChange={handleChange}
                className={`mt-1 block w-full rounded-md border ${fieldErrors.plannedFinishDate ? "border-red-500" : "border-slate-700"} bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500`}
              />
              {fieldErrors.plannedFinishDate && <p className="mt-1 text-xs text-red-400">{fieldErrors.plannedFinishDate}</p>}
            </div>
          </div>

          <div className="flex gap-3 pt-6">
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-500"
            >
              {isSubmitting ? "Creating..." : "Create Project"}
            </button>
            <button
              type="button"
              onClick={() => router.back()}
              className="rounded-md border border-slate-700 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/50"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
