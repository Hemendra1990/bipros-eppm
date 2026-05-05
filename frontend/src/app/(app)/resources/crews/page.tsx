"use client";

import { useMemo } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2, Users } from "lucide-react";
import toast from "react-hot-toast";
import { crewApi, type CrewResponse } from "@/lib/api/crewApi";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";
import { PageHeader } from "@/components/common/PageHeader";
import { Breadcrumb } from "@/components/common/Breadcrumb";
import { StatusBadge } from "@/components/common/StatusBadge";
import { TabTip } from "@/components/common/TabTip";
import { notificationHelpers } from "@/lib/notificationHelpers";

export default function CrewsListPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useQuery({
    queryKey: ["crews"],
    queryFn: () => crewApi.list(),
  });

  const crews = useMemo<CrewResponse[]>(() => data?.data ?? [], [data]);

  const remove = useMutation({
    mutationFn: (id: string) => crewApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["crews"] });
      toast.success("Crew deleted");
    },
    onError: (err) => notificationHelpers.handleApiError(err, "Failed to delete crew"),
  });

  const columns = useMemo<ColumnDef<CrewResponse>[]>(
    () => [
      {
        accessorKey: "code",
        header: "Code",
        enableSorting: true,
        cell: (info) => (
          <Link
            href={`/resources/crews/${info.row.original.id}`}
            className="font-mono text-sm text-accent hover:underline"
          >
            {info.row.original.code ?? "—"}
          </Link>
        ),
      },
      { accessorKey: "name", header: "Name", enableSorting: true },
      {
        accessorKey: "crewLeadName",
        header: "Crew Lead",
        enableSorting: true,
        cell: (info) => {
          const c = info.row.original;
          if (!c.crewLeadResourceId) return "—";
          return (
            <Link
              href={`/resources/${c.crewLeadResourceId}`}
              className="text-text-primary hover:text-accent hover:underline"
              onClick={(e) => e.stopPropagation()}
            >
              {c.crewLeadName ?? c.crewLeadCode ?? c.crewLeadResourceId.slice(0, 8)}
            </Link>
          );
        },
      },
      {
        accessorKey: "memberCount",
        header: "Members",
        enableSorting: true,
        cell: (info) => info.row.original.memberCount,
      },
      {
        accessorKey: "status",
        header: "Status",
        cell: (info) => <StatusBadge status={String(info.getValue())} />,
      },
      {
        id: "actions",
        header: "Actions",
        cell: (info) => (
          <div className="flex items-center gap-2">
            <Link
              href={`/resources/crews/${info.row.original.id}`}
              className="text-accent hover:underline text-sm"
              onClick={(e) => e.stopPropagation()}
            >
              View
            </Link>
            <button
              onClick={(e) => {
                e.stopPropagation();
                if (window.confirm("Delete this crew?")) {
                  remove.mutate(info.row.original.id);
                }
              }}
              disabled={remove.isPending}
              className="text-text-secondary hover:text-danger disabled:text-text-muted"
              title="Delete crew"
            >
              <Trash2 size={16} />
            </button>
          </div>
        ),
      },
    ],
    [remove],
  );

  return (
    <div>
      <Breadcrumb
        items={[
          { label: "Resources", href: "/resources" },
          { label: "Crews", href: "/resources/crews", active: true },
        ]}
      />
      <PageHeader
        title="Crews"
        description="Named groups of resources led by a Labor crew lead. A crew can include manpower, equipment, and materials — assign a whole crew to an activity to deploy them together."
        actions={
          <Link
            href="/resources/crews/new"
            className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
          >
            <Plus size={16} />
            New Crew
          </Link>
        }
      />

      <TabTip
        title="What is a Crew?"
        description="A first-class group of resources with a Labor crew lead. Mixed-type membership is allowed (manpower + equipment + materials). Use crews when you want to deploy a whole team to an activity in one step."
      />

      {isLoading ? (
        <p className="mt-6 text-sm text-text-muted">Loading crews…</p>
      ) : error ? (
        <p className="mt-6 text-sm text-danger">Failed to load crews.</p>
      ) : crews.length === 0 ? (
        <div className="mt-8 rounded-xl border border-dashed border-border bg-surface/30 p-10 text-center">
          <Users className="mx-auto mb-3 text-text-muted" size={32} />
          <p className="text-sm text-text-muted">
            No crews defined yet. Create one to start grouping resources for activity assignment.
          </p>
        </div>
      ) : (
        <div className="mt-4">
          <VirtualDataTable data={crews} columns={columns} />
        </div>
      )}
    </div>
  );
}
