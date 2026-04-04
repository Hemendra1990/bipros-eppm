"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { resourceApi } from "@/lib/api/resourceApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { ResourceResponse } from "@/lib/types";

export default function ResourcesPage() {
  const queryClient = useQueryClient();
  const { data: resourcesData, isLoading, error } = useQuery({
    queryKey: ["resources"],
    queryFn: () => resourceApi.listResources(0, 50),
  });

  const deleteMutation = useMutation({
    mutationFn: (resourceId: string) => resourceApi.deleteResource(resourceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resources"] });
    },
  });

  const resources = resourcesData?.data?.content ?? [];

  const columns: ColumnDef<ResourceResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    { key: "name", label: "Name", sortable: true },
    {
      key: "type",
      label: "Type",
      sortable: true,
      render: (value) => <span className="text-sm font-medium">{String(value)}</span>,
    },
    {
      key: "status",
      label: "Status",
      render: (value) => <StatusBadge status={String(value)} />,
    },
    { key: "maxUnits", label: "Max Units", sortable: true },
    {
      key: "id",
      label: "Actions",
      render: (value) => (
        <button
          onClick={() => {
            if (window.confirm("Are you sure you want to delete this resource?")) {
              deleteMutation.mutate(String(value));
            }
          }}
          disabled={deleteMutation.isPending}
          className="text-red-600 hover:text-red-700 disabled:text-gray-400"
        >
          <Trash2 size={16} />
        </button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Resources"
        description="Manage labor, nonlabor, and material resources"
        actions={
          <Link
            href="/resources/new"
            className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Plus size={16} />
            New Resource
          </Link>
        }
      />

      {isLoading && (
        <div className="py-12 text-center text-gray-500">Loading resources...</div>
      )}

      {error && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          Failed to load resources. Is the backend running?
        </div>
      )}

      {!isLoading && resources.length === 0 && (
        <EmptyState
          title="No resources yet"
          description="Create your first resource to get started with resource management."
        />
      )}

      {resources.length > 0 && <DataTable columns={columns} data={resources} rowKey="id" />}
    </div>
  );
}
