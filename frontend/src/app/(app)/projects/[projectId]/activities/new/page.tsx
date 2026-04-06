"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import type { CreateActivityRequest } from "@/lib/api/activityApi";
import type { WbsNodeResponse } from "@/lib/types";
import { getErrorMessage } from "@/lib/utils/error";

export default function NewActivityPage() {
  const router = useRouter();
  const params = useParams();
  const projectId = params.projectId as string;

  const [formData, setFormData] = useState({
    code: "",
    name: "",
    activityType: "TASK_DEPENDENT",
    durationType: "FIXED_DURATION_AND_UNITS",
    duration: 0,
    wbsNodeId: "",
    plannedStartDate: "",
    plannedFinishDate: "",
  });

  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const { data: wbsData, isLoading: isLoadingWbs } = useQuery({
    queryKey: ["wbs", projectId],
    queryFn: () => projectApi.getWbsTree(projectId),
  });

  const wbsNodes = wbsData?.data ?? [];

  // Flatten WBS tree for dropdown
  const flattenedWbs = flattenWbsNodes(wbsNodes);

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === "duration" ? parseInt(value, 10) : value,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!formData.code || !formData.name || !formData.wbsNodeId) {
      setError("Code, Name, and WBS Node are required");
      return;
    }

    setIsSubmitting(true);
    try {
      const createRequest: CreateActivityRequest = {
        code: formData.code,
        name: formData.name,
        projectId: projectId,
        wbsNodeId: formData.wbsNodeId,
        originalDuration: formData.duration || undefined,
        activityType: formData.activityType,
        durationType: formData.durationType,
        plannedStartDate: formData.plannedStartDate || undefined,
        plannedFinishDate: formData.plannedFinishDate || undefined,
      };

      const result = await activityApi.createActivity(projectId, createRequest);
      if (result.data) {
        router.push(`/projects/${projectId}?tab=activities`);
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create activity"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="New Activity"
        description="Create a new activity for this project"
      />

      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-sm">
        <form onSubmit={handleSubmit} className="space-y-6">
          {error && (
            <div className="rounded-md bg-red-500/10 p-4 text-sm text-red-400">{error}</div>
          )}

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">Code *</label>
              <input
                type="text"
                name="code"
                value={formData.code}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., ACT-001"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Name *</label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., Design Phase"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">Activity Type</label>
              <select
                name="activityType"
                value={formData.activityType}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="TASK_DEPENDENT">Task Dependent</option>
                <option value="RESOURCE_DEPENDENT">Resource Dependent</option>
                <option value="LEVEL_OF_EFFORT">Level of Effort</option>
                <option value="START_MILESTONE">Start Milestone</option>
                <option value="FINISH_MILESTONE">Finish Milestone</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Duration Type</label>
              <select
                name="durationType"
                value={formData.durationType}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="FIXED_DURATION_AND_UNITS">Fixed Duration & Units</option>
                <option value="FIXED_DURATION_AND_UNITS_PER_TIME">Fixed Duration & Units/Time</option>
                <option value="FIXED_UNITS">Fixed Units</option>
                <option value="FIXED_UNITS_PER_TIME">Fixed Units/Time</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">
                Duration (days) *
              </label>
              <input
                type="number"
                name="duration"
                value={formData.duration}
                onChange={handleChange}
                min="0"
                className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., 5"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">WBS Node *</label>
              <SearchableSelect
                value={formData.wbsNodeId}
                onChange={(val) => setFormData((prev) => ({ ...prev, wbsNodeId: val }))}
                placeholder="Search WBS nodes..."
                options={flattenedWbs.map((node) => ({
                  value: node.id,
                  label: `${node.indent}${node.code} - ${node.name}`,
                }))}
                disabled={isLoadingWbs}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300">
                Planned Start Date
              </label>
              <input
                type="date"
                name="plannedStartDate"
                value={formData.plannedStartDate}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">
                Planned Finish Date
              </label>
              <input
                type="date"
                name="plannedFinishDate"
                value={formData.plannedFinishDate}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-slate-700 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
          </div>

          <div className="flex gap-3 pt-6">
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-600"
            >
              {isSubmitting ? "Creating..." : "Create Activity"}
            </button>
            <button
              type="button"
              onClick={() => router.push(`/projects/${projectId}?tab=activities`)}
              className="rounded-md bg-slate-700/50 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function flattenWbsNodes(
  nodes: WbsNodeResponse[],
  level = 0
): Array<{ id: string; code: string; name: string; indent: string }> {
  const result: Array<{ id: string; code: string; name: string; indent: string }> = [];
  for (const node of nodes) {
    result.push({
      id: node.id,
      code: node.code,
      name: node.name,
      indent: "  ".repeat(level),
    });
    if (node.children && node.children.length > 0) {
      result.push(...flattenWbsNodes(node.children, level + 1));
    }
  }
  return result;
}
