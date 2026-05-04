"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import toast from "react-hot-toast";
import { resourceApi, type ResourceResponse } from "@/lib/api/resourceApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { StatusBadge } from "@/components/common/StatusBadge";
import { TabTip } from "@/components/common/TabTip";
import { notificationHelpers } from "@/lib/notificationHelpers";

type TypeTab = "ALL" | "MANPOWER" | "EQUIPMENT" | "MATERIAL";

const TYPE_TABS: { key: TypeTab; label: string }[] = [
  { key: "ALL", label: "All" },
  { key: "MANPOWER", label: "Manpower" },
  { key: "EQUIPMENT", label: "Equipment" },
  { key: "MATERIAL", label: "Material" },
];

// The "Manpower" tab is a UX label; the backend's seeded ResourceType code is "LABOR".
const tabKeyToTypeCode = (key: TypeTab): string => (key === "MANPOWER" ? "LABOR" : key);

export default function ResourcesPage() {
  const queryClient = useQueryClient();
  const [typeTab, setTypeTab] = useState<TypeTab>("ALL");

  const { data: resourcesData, isLoading, error } = useQuery({
    queryKey: ["resources"],
    queryFn: () => resourceApi.listResources(),
  });

  const deleteMutation = useMutation({
    mutationFn: (resourceId: string) => resourceApi.deleteResource(resourceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      toast.success("Resource deleted successfully");
    },
    onError: (err) => notificationHelpers.handleApiError(err, "Failed to delete resource"),
  });

  const deleteAllMutation = useMutation({
    mutationFn: () => resourceApi.deleteAllResources(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      toast.success("All resources deleted successfully");
    },
    onError: (err) => notificationHelpers.handleApiError(err, "Failed to delete all resources"),
  });

  const allResources = useMemo<ResourceResponse[]>(() => {
    const rawData = resourcesData?.data;
    return Array.isArray(rawData)
      ? rawData
      : ((rawData as unknown as { content?: ResourceResponse[] } | null)?.content ?? []);
  }, [resourcesData]);

  const resources = useMemo(() => {
    if (typeTab === "ALL") return allResources;
    const code = tabKeyToTypeCode(typeTab);
    return allResources.filter((r) => r.resourceTypeCode === code);
  }, [allResources, typeTab]);

  // Columns vary by tab. The list endpoint returns slim fields only — detail blocks come on
  // /v1/resources/{id}, not the list — so we render only what's on ResourceResponse.
  const columns = useMemo<ColumnDef<ResourceResponse>[]>(() => {
    const baseCols: ColumnDef<ResourceResponse>[] = [
      { key: "code", label: "Code", sortable: true },
      { key: "name", label: "Name", sortable: true },
    ];

    const typeCol: ColumnDef<ResourceResponse> = {
      key: "resourceTypeName",
      label: "Type",
      sortable: true,
      render: (_value, row) => (
        <span className="text-sm font-medium">
          {row.resourceTypeName ?? row.resourceTypeCode ?? "—"}
        </span>
      ),
    };

    const roleCol: ColumnDef<ResourceResponse> = {
      key: "roleName",
      label: "Role",
      sortable: true,
      render: (_value, row) => row.roleName ?? "—",
    };

    const availabilityCol: ColumnDef<ResourceResponse> = {
      key: "availability",
      label: "Availability",
      sortable: true,
      render: (_value, row) =>
        row.availability == null ? "—" : Number(row.availability).toFixed(2),
    };

    const unitCol: ColumnDef<ResourceResponse> = {
      key: "unit",
      label: "Unit",
      sortable: true,
      render: (_value, row) => row.unit ?? "—",
    };

    const statusCol: ColumnDef<ResourceResponse> = {
      key: "status",
      label: "Status",
      render: (value) => <StatusBadge status={String(value)} />,
    };

    const actionsCol: ColumnDef<ResourceResponse> = {
      key: "id",
      label: "Actions",
      render: (_value, row) => (
        <div className="flex items-center gap-2">
          <Link
            href={`/resources/${row.id}`}
            className="text-accent hover:underline text-sm"
            onClick={(e) => e.stopPropagation()}
          >
            View
          </Link>
          <button
            onClick={(e) => {
              e.stopPropagation();
              if (window.confirm("Delete this resource?")) {
                deleteMutation.mutate(String(row.id));
              }
            }}
            disabled={deleteMutation.isPending}
            className="text-text-secondary hover:text-danger disabled:text-text-muted"
            title="Delete resource"
          >
            <Trash2 size={16} />
          </button>
        </div>
      ),
    };

    if (typeTab === "MATERIAL") {
      return [...baseCols, typeCol, roleCol, unitCol, availabilityCol, statusCol, actionsCol];
    }
    if (typeTab === "MANPOWER") {
      return [...baseCols, typeCol, roleCol, availabilityCol, statusCol, actionsCol];
    }
    if (typeTab === "EQUIPMENT") {
      return [...baseCols, typeCol, roleCol, availabilityCol, statusCol, actionsCol];
    }
    return [...baseCols, typeCol, roleCol, availabilityCol, statusCol, actionsCol];
  }, [typeTab, deleteMutation]);

  return (
    <div>
      <PageHeader
        title="Resources"
        description="Manpower, equipment and material resources used across projects"
        actions={
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                if (
                  window.confirm(
                    "Are you sure you want to delete ALL resources? This action cannot be undone."
                  )
                ) {
                  deleteAllMutation.mutate();
                }
              }}
              disabled={deleteAllMutation.isPending || allResources.length === 0}
              className="inline-flex items-center gap-2 rounded-md bg-danger px-4 py-2 text-sm font-medium text-white hover:bg-danger/90 disabled:opacity-50"
            >
              <Trash2 size={16} />
              {deleteAllMutation.isPending ? "Deleting..." : "Delete All"}
            </button>
            <Link
              href="/resources/new"
              className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
            >
              <Plus size={16} />
              New Resource
            </Link>
          </div>
        }
      />

      <TabTip
        title="Global Resource Pool"
        description="Define every resource (people, equipment, materials) that can be assigned to projects. Click View on a row to open its detail tabs."
      />

      {/* Type tabs */}
      <div className="mt-4 mb-4 flex flex-wrap gap-2">
        {TYPE_TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTypeTab(t.key)}
            className={`rounded-md px-4 py-2 text-sm font-medium transition-colors ${
              typeTab === t.key
                ? "bg-accent text-text-primary"
                : "border border-border bg-surface/50 text-text-secondary hover:bg-surface-hover/50"
            }`}
          >
            {t.label}
            <span className="ml-2 inline-flex items-center justify-center rounded bg-surface-hover/60 px-1.5 py-0.5 text-xs">
              {t.key === "ALL"
                ? allResources.length
                : allResources.filter((r) => r.resourceTypeCode === tabKeyToTypeCode(t.key)).length}
            </span>
          </button>
        ))}
      </div>

      {isLoading && (
        <div className="py-12 text-center text-text-muted">Loading resources...</div>
      )}

      {error && (
        <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">
          Failed to load resources. Is the backend running?
        </div>
      )}

      {!isLoading && resources.length === 0 && (
        <EmptyState
          title={
            typeTab === "ALL"
              ? "No resources yet"
              : `No ${typeTab.toLowerCase()} resources`
          }
          description="Create your first resource to get started with resource management."
        />
      )}

      {resources.length > 0 && (
        <DataTable
          columns={columns}
          data={resources}
          rowKey="id"
          searchable
          searchPlaceholder="Search resources..."
        />
      )}
    </div>
  );
}
