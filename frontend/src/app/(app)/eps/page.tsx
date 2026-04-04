"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { projectApi } from "@/lib/api/projectApi";
import { TreeView } from "@/components/common/TreeView";
import { PageHeader } from "@/components/common/PageHeader";
import type { EpsNodeResponse } from "@/lib/types";

export default function EpsPage() {
  const { data: epsData, isLoading, error } = useQuery({
    queryKey: ["eps"],
    queryFn: () => projectApi.getEpsTree(),
  });

  const epsNodes = epsData?.data ?? [];

  return (
    <div>
      <PageHeader
        title="Enterprise Project Structure"
        description="Manage the EPS hierarchy and related projects"
        actions={
          <button className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700">
            <Plus size={16} />
            Add Node
          </button>
        }
      />

      {isLoading && (
        <div className="py-12 text-center text-gray-500">Loading EPS structure...</div>
      )}

      {error && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          Failed to load EPS structure. Is the backend running?
        </div>
      )}

      {!isLoading && epsNodes.length === 0 && (
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <p className="text-gray-500">No EPS nodes yet. Create your first node to get started.</p>
        </div>
      )}

      {epsNodes.length > 0 && (
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <TreeView nodes={epsNodes} />
        </div>
      )}
    </div>
  );
}
