"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronRight, Search } from "lucide-react";
import { resourceApi, type ResourceResponse, type ResourceTreeNode } from "@/lib/api/resourceApi";
import { Breadcrumb } from "@/components/common/Breadcrumb";
import { PageHeader } from "@/components/common/PageHeader";

type TypeFilter = "ALL" | "LABOR" | "EQUIPMENT" | "MATERIAL";

const TYPE_FILTERS: { key: TypeFilter; label: string }[] = [
  { key: "ALL", label: "All" },
  { key: "LABOR", label: "Manpower" },
  { key: "EQUIPMENT", label: "Equipment" },
  { key: "MATERIAL", label: "Material" },
];

/**
 * Resource hierarchy explorer. Walks the org tree (Resource.parent_id) starting
 * from each root and lets the user expand/collapse. Type filter narrows
 * children at each level. The search box highlights matching nodes by name/code.
 */
export default function ResourceHierarchyPage() {
  const [typeFilter, setTypeFilter] = useState<TypeFilter>("ALL");
  const [search, setSearch] = useState("");

  const { data: rootsData, isLoading: rootsLoading } = useQuery({
    queryKey: ["resources", "roots"],
    queryFn: () => resourceApi.listRoots(),
  });
  const roots = useMemo<ResourceResponse[]>(() => rootsData?.data ?? [], [rootsData]);

  return (
    <div>
      <Breadcrumb
        items={[
          { label: "Resources", href: "/resources" },
          { label: "Hierarchy", href: "/resources/hierarchy", active: true },
        ]}
      />
      <PageHeader
        title="Resource Hierarchy"
        description="Organisational tree of resources via parent_id. Supervisors appear above their direct reports; mixed-type children (manpower + equipment + materials) are allowed."
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="relative">
          <Search
            size={16}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted"
          />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter by name or code..."
            className="w-72 rounded-md border border-border bg-surface px-9 py-2 text-sm text-text-primary placeholder:text-text-muted"
          />
        </div>
        <div className="flex gap-1 rounded-md border border-border bg-surface p-1">
          {TYPE_FILTERS.map((t) => (
            <button
              key={t.key}
              type="button"
              onClick={() => setTypeFilter(t.key)}
              className={`rounded px-3 py-1 text-sm transition-colors ${
                typeFilter === t.key
                  ? "bg-accent text-text-primary"
                  : "text-text-secondary hover:bg-surface-hover"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {rootsLoading ? (
        <p className="text-sm text-text-muted">Loading roots…</p>
      ) : roots.length === 0 ? (
        <p className="text-sm text-text-muted">
          No root resources. All resources currently have a parent — start from the
          resource list to inspect any record&apos;s subordinates.
        </p>
      ) : (
        <div className="space-y-2 rounded-md border border-border bg-surface/40 p-3">
          {roots
            .filter((r) =>
              typeFilter === "ALL" ? true : r.resourceTypeCode === typeFilter,
            )
            .map((r) => (
              <RootNode
                key={r.id}
                root={r}
                typeFilter={typeFilter}
                search={search.trim().toLowerCase()}
              />
            ))}
        </div>
      )}
    </div>
  );
}

function RootNode({
  root,
  typeFilter,
  search,
}: {
  root: ResourceResponse;
  typeFilter: TypeFilter;
  search: string;
}) {
  const [open, setOpen] = useState(false);
  const enabled = open;

  const { data, isFetching } = useQuery({
    queryKey: [
      "resource",
      root.id,
      "tree",
      typeFilter === "ALL" ? "any" : typeFilter,
    ],
    queryFn: () =>
      resourceApi.getTree(root.id, {
        depth: 6,
        ...(typeFilter === "ALL" ? {} : { typeCode: typeFilter }),
      }),
    enabled,
  });

  const tree = data?.data ?? null;

  return (
    <div>
      <NodeRow
        label={root.name ?? root.code ?? root.id}
        code={root.code}
        type={root.resourceTypeCode}
        id={root.id}
        depth={0}
        hasChildren // unknown until expanded; assume true so toggle is offered
        open={open}
        onToggle={() => setOpen((v) => !v)}
        highlight={matchesSearch(root, search)}
      />
      {open && (
        <div className="ml-6 mt-1 border-l border-border pl-4">
          {isFetching && <p className="py-1 text-xs text-text-muted">Loading…</p>}
          {tree?.children?.map((child) => (
            <TreeBranch key={child.id} node={child} search={search} />
          ))}
          {tree && (tree.children?.length ?? 0) === 0 && !isFetching && (
            <p className="py-1 text-xs text-text-muted">No subordinates.</p>
          )}
        </div>
      )}
    </div>
  );
}

function TreeBranch({ node, search }: { node: ResourceTreeNode; search: string }) {
  const hasChildren = (node.children?.length ?? 0) > 0;
  const [open, setOpen] = useState(false);
  return (
    <div>
      <NodeRow
        label={node.name ?? node.code ?? node.id}
        code={node.code}
        type={node.typeCategory}
        id={node.id}
        depth={node.depth}
        hasChildren={hasChildren}
        open={open}
        onToggle={() => setOpen((v) => !v)}
        highlight={
          (search &&
            ((node.name ?? "").toLowerCase().includes(search) ||
              (node.code ?? "").toLowerCase().includes(search))) ||
          false
        }
      />
      {open && hasChildren && (
        <div className="ml-6 mt-1 border-l border-border pl-4">
          {node.children.map((child) => (
            <TreeBranch key={child.id} node={child} search={search} />
          ))}
        </div>
      )}
    </div>
  );
}

function NodeRow({
  label,
  code,
  type,
  id,
  hasChildren,
  open,
  onToggle,
  highlight,
}: {
  label: string;
  code: string | null | undefined;
  type: string | null | undefined;
  id: string;
  depth: number;
  hasChildren: boolean;
  open: boolean;
  onToggle: () => void;
  highlight: boolean;
}) {
  return (
    <div
      className={`flex items-center gap-2 rounded px-2 py-1 ${
        highlight ? "bg-gold-deep/10" : "hover:bg-surface-hover"
      }`}
    >
      <button
        type="button"
        onClick={onToggle}
        className="text-text-muted hover:text-accent"
        aria-label={open ? "Collapse" : "Expand"}
      >
        {hasChildren ? (
          open ? (
            <ChevronDown size={14} />
          ) : (
            <ChevronRight size={14} />
          )
        ) : (
          <span className="inline-block w-[14px]" />
        )}
      </button>
      {type && (
        <span className="rounded bg-surface px-1.5 py-0.5 text-[10px] font-medium uppercase text-text-secondary">
          {type}
        </span>
      )}
      <Link href={`/resources/${id}`} className="text-sm text-text-primary hover:text-accent">
        {label}
      </Link>
      {code && code !== label && (
        <span className="font-mono text-xs text-text-muted">{code}</span>
      )}
    </div>
  );
}

function matchesSearch(r: ResourceResponse, search: string): boolean {
  if (!search) return false;
  return (
    (r.name ?? "").toLowerCase().includes(search) ||
    (r.code ?? "").toLowerCase().includes(search)
  );
}
