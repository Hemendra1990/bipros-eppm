"use client";

import Link from "next/link";
import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { resourceApi, type SubordinateView } from "@/lib/api/resourceApi";
import { SimpleTable } from "@/components/common/SimpleTable";
import type { ColumnDef } from "@tanstack/react-table";

interface SubordinatesPanelProps {
  resourceId: string;
}

/**
 * Lists direct subordinates of a resource. Pulls the two-tree union from
 * {@code GET /v1/resources/{id}/subordinates} (org tree ∪ HR tree) and tags
 * each row with its link source.
 */
export function SubordinatesPanel({ resourceId }: SubordinatesPanelProps) {
  const { data, isLoading, error } = useQuery({
    queryKey: ["resource", resourceId, "subordinates"],
    queryFn: () => resourceApi.getSubordinates(resourceId),
    enabled: !!resourceId,
  });

  const columns = useMemo<ColumnDef<SubordinateView>[]>(
    () => [
      {
        accessorKey: "code",
        header: "Code",
        cell: (info) => {
          const row = info.row.original;
          return (
            <Link
              href={`/resources/${row.id}`}
              className="text-accent hover:underline"
            >
              {row.code ?? row.id.slice(0, 8)}
            </Link>
          );
        },
      },
      {
        accessorKey: "name",
        header: "Name",
        cell: (info) => (
          <span className="text-text-primary">
            {(info.getValue() as string) ?? "—"}
          </span>
        ),
      },
      {
        accessorKey: "roleName",
        header: "Role",
        cell: (info) => (
          <span className="text-text-secondary">
            {(info.getValue() as string) ?? "—"}
          </span>
        ),
      },
      {
        accessorKey: "typeCategory",
        header: "Type",
        cell: (info) => (
          <span className="text-text-secondary">
            {(info.getValue() as string) ?? "—"}
          </span>
        ),
      },
      {
        accessorKey: "designation",
        header: "Designation",
        cell: (info) => {
          const row = info.row.original;
          return (
            <span className="text-text-secondary">
              {row.designation ?? row.fullName ?? "—"}
            </span>
          );
        },
      },
      {
        accessorKey: "linkSource",
        header: "Linked via",
        cell: (info) => (
          <LinkSourceBadge
            source={info.getValue() as SubordinateView["linkSource"]}
          />
        ),
      },
    ],
    []
  );

  if (isLoading) {
    return <p className="text-sm text-text-muted">Loading subordinates…</p>;
  }
  if (error) {
    return (
      <p className="text-sm text-danger">
        Failed to load subordinates: {String(error)}
      </p>
    );
  }
  const rows: SubordinateView[] = data?.data ?? [];
  if (rows.length === 0) {
    return (
      <p className="text-sm text-text-muted">
        No direct subordinates. Add one by setting another resource&apos;s
        Parent / Reporting Manager to this record.
      </p>
    );
  }

  return <SimpleTable columns={columns} data={rows} sortable={false} />;
}

function LinkSourceBadge({ source }: { source: SubordinateView["linkSource"] }) {
  const map = {
    org: { label: "Org", cls: "bg-blue-500/15 text-blue-300" },
    hr: { label: "HR", cls: "bg-emerald-500/15 text-emerald-300" },
    both: { label: "Both", cls: "bg-purple-500/15 text-purple-300" },
  } as const;
  const { label, cls } = map[source];
  return (
    <span
      className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}
    >
      {label}
    </span>
  );
}
