"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { getErrorMessage } from "@/lib/utils/error";
import { documentApi } from "@/lib/api/documentApi";
import { TabTip } from "@/components/common/TabTip";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";

interface DrawingFormData {
  drawingNumber: string;
  title: string;
  discipline: "CIVIL" | "STRUCTURAL" | "MECHANICAL" | "ELECTRICAL" | "PLUMBING" | "ARCHITECTURAL";
  revision: string;
  status: "PRELIMINARY" | "IFA" | "IFC" | "AS_BUILT" | "SUPERSEDED";
}

export default function DrawingsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<DrawingFormData>({
    drawingNumber: "",
    title: "",
    discipline: "CIVIL",
    revision: "",
    status: "PRELIMINARY",
  });
  const [error, setError] = useState("");
  const queryClient = useQueryClient();

  const { data: drawings = [] } = useQuery({
    queryKey: ["drawings", projectId],
    queryFn: () => documentApi.listDrawings(projectId),
    select: (response) => response.data || [],
  });

  const createDrawingMutation = useMutation({
    mutationFn: (data: DrawingFormData) => documentApi.createDrawing(projectId, { projectId, ...data }),
    onSuccess: () => {
      toast.success("Drawing created successfully");
      setShowForm(false);
      setFormData({
        drawingNumber: "",
        title: "",
        discipline: "CIVIL",
        revision: "",
        status: "PRELIMINARY",
      });
      setError("");
      queryClient.invalidateQueries({ queryKey: ["drawings", projectId] });
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to create drawing");
      setError(msg);
      toast.error(msg);
    },
  });

  const handleFormChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value as any,
    }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!formData.drawingNumber || !formData.title) {
      setError("Drawing Number and Title are required");
      return;
    }

    createDrawingMutation.mutate(formData);
  };

  const getStatusColor = (
    status: "PRELIMINARY" | "IFA" | "IFC" | "AS_BUILT" | "SUPERSEDED"
  ) => {
    switch (status) {
      case "PRELIMINARY":
        return "bg-warning/10 text-warning ring-1 ring-amber-500/20";
      case "IFA":
        return "bg-accent/10 text-accent ring-1 ring-accent/20";
      case "IFC":
        return "bg-success/10 text-success ring-1 ring-success/20";
      case "AS_BUILT":
        return "bg-purple-500/10 text-purple-400 ring-1 ring-purple-500/20";
      case "SUPERSEDED":
        return "bg-surface-active/50 text-text-secondary ring-1 ring-border/50";
      default:
        return "bg-surface-active/50 text-text-secondary ring-1 ring-border/50";
    }
  };

  const drawingColumns = useMemo<ColumnDef<typeof drawings[number]>[]>(
    () => [
      {
        accessorKey: "drawingNumber",
        header: "Drawing Number",
        cell: (info) => (
          <span className="font-medium text-text-primary">
            {info.getValue() as string}
          </span>
        ),
      },
      {
        accessorKey: "title",
        header: "Title",
        cell: (info) => (
          <span className="text-text-primary">{info.getValue() as string}</span>
        ),
      },
      {
        accessorKey: "discipline",
        header: "Discipline",
        cell: (info) => (
          <span className="flex items-center gap-2">
            {getDisciplineIcon(info.getValue() as string)}{" "}
            {info.getValue() as string}
          </span>
        ),
      },
      {
        accessorKey: "revision",
        header: "Revision",
        cell: (info) => (
          <span className="text-text-secondary">{info.getValue() as string}</span>
        ),
      },
      {
        accessorKey: "revisionDate",
        header: "Revision Date",
        cell: (info) => (
          <span className="text-text-secondary text-xs">
            {new Date(info.getValue() as string).toLocaleDateString()}
          </span>
        ),
      },
      {
        accessorKey: "status",
        header: "Status",
        cell: (info) => (
          <span
            className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${getStatusColor(
              info.getValue() as DrawingFormData["status"]
            )}`}
          >
            {info.getValue() as string}
          </span>
        ),
      },
      {
        accessorKey: "packageCode",
        header: "Package",
        cell: (info) => (
          <span className="text-text-secondary text-sm">
            {info.getValue() as string}
          </span>
        ),
      },
      {
        accessorKey: "scale",
        header: "Scale",
        cell: (info) => (
          <span className="text-text-secondary text-sm">
            {info.getValue() as string}
          </span>
        ),
      },
      {
        id: "actions",
        header: "Actions",
        cell: () => (
          <button className="text-accent hover:text-blue-300 text-xs font-medium">
            View
          </button>
        ),
      },
    ],
    []
  );

  const getDisciplineIcon = (discipline: string) => {
    const icons: Record<string, string> = {
      CIVIL: "🏗️",
      STRUCTURAL: "🏢",
      ELECTRICAL: "⚡",
      MECHANICAL: "⚙️",
      ARCHITECTURAL: "🏛️",
      PLUMBING: "🔧",
      HVAC: "❄️",
    };
    return icons[discipline] || "📋";
  };

  return (
    <div className="space-y-6">
      <TabTip
        title="Drawing Register"
        description="Track engineering drawings by discipline. Monitor revision status (IFA = Issued for Approval, IFC = Issued for Construction, As-Built = Final)."
      />
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Drawing Register</h1>
          <p className="text-sm text-text-secondary mt-1">
            {drawings.length} drawing{drawings.length !== 1 ? "s" : ""} found
          </p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover transition-colors text-sm font-medium"
        >
          + Add Drawing
        </button>
      </div>

      {/* Create Form */}
      {showForm && (
        <div className="bg-surface/50 rounded-xl border border-border p-6 shadow-xl">
          {error && (
            <div className="mb-4 rounded-md bg-danger/10 p-3 text-sm text-danger">
              {error}
            </div>
          )}
          <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">
                Drawing Number
              </label>
              <input
                type="text"
                name="drawingNumber"
                value={formData.drawingNumber}
                onChange={handleFormChange}
                placeholder="e.g., DWG-001"
                className="w-full px-3 py-2 bg-surface-hover border border-border rounded-lg text-text-primary placeholder-text-muted focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">Title</label>
              <input
                type="text"
                name="title"
                value={formData.title}
                onChange={handleFormChange}
                placeholder="Drawing title"
                className="w-full px-3 py-2 bg-surface-hover border border-border rounded-lg text-text-primary placeholder-text-muted focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">
                Discipline
              </label>
              <select
                name="discipline"
                value={formData.discipline}
                onChange={handleFormChange}
                className="w-full px-3 py-2 bg-surface-hover border border-border rounded-lg text-text-primary focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
              >
                <option value="CIVIL">CIVIL</option>
                <option value="STRUCTURAL">STRUCTURAL</option>
                <option value="MECHANICAL">MECHANICAL</option>
                <option value="ELECTRICAL">ELECTRICAL</option>
                <option value="PLUMBING">PLUMBING</option>
                <option value="ARCHITECTURAL">ARCHITECTURAL</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">Revision</label>
              <input
                type="text"
                name="revision"
                value={formData.revision}
                onChange={handleFormChange}
                placeholder="e.g., A, B, C"
                className="w-full px-3 py-2 bg-surface-hover border border-border rounded-lg text-text-primary placeholder-text-muted focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-text-secondary mb-1">Status</label>
              <select
                name="status"
                value={formData.status}
                onChange={handleFormChange}
                className="w-full px-3 py-2 bg-surface-hover border border-border rounded-lg text-text-primary focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
              >
                <option value="PRELIMINARY">PRELIMINARY</option>
                <option value="IFA">IFA (Issued for Approval)</option>
                <option value="IFC">IFC (Issued for Construction)</option>
                <option value="AS_BUILT">AS_BUILT</option>
                <option value="SUPERSEDED">SUPERSEDED</option>
              </select>
            </div>
            <div className="col-span-2 flex gap-3">
              <button
                type="submit"
                disabled={createDrawingMutation.isPending}
                className="flex-1 px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover disabled:bg-border transition-colors font-medium"
              >
                {createDrawingMutation.isPending ? "Creating..." : "Create Drawing"}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="flex-1 px-4 py-2 border border-border bg-surface-hover text-text-secondary rounded-lg hover:bg-surface-active transition-colors font-medium"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      <VirtualDataTable
        columns={drawingColumns}
        data={drawings}
        emptyMessage="No drawings found"
      />
    </div>
  );
}
