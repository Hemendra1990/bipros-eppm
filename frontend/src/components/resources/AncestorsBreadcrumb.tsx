"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ChevronRight } from "lucide-react";
import { resourceApi, type AncestorView } from "@/lib/api/resourceApi";

interface AncestorsBreadcrumbProps {
  resourceId: string;
  /** Display name of the current resource — rendered as the trailing segment. */
  currentName: string | null | undefined;
}

/**
 * Renders the parent_id chain from root → … → current resource as a
 * breadcrumb. Returns null if the resource has no ancestors (the resource is
 * already a hierarchy root).
 */
export function AncestorsBreadcrumb({ resourceId, currentName }: AncestorsBreadcrumbProps) {
  const { data, isLoading } = useQuery({
    queryKey: ["resource", resourceId, "ancestors"],
    queryFn: () => resourceApi.getAncestors(resourceId),
    enabled: !!resourceId,
  });

  if (isLoading) return null;
  const ancestors: AncestorView[] = data?.data ?? [];
  if (ancestors.length === 0) return null;

  // Backend returns nearest-first (immediate parent first, root last).
  // Render root → … → parent → current.
  const rootFirst = [...ancestors].reverse();

  return (
    <nav
      className="flex items-center flex-wrap gap-1 text-xs text-text-muted"
      aria-label="Resource hierarchy"
    >
      {rootFirst.map((a, idx) => (
        <span key={a.id} className="flex items-center gap-1">
          <Link
            href={`/resources/${a.id}`}
            className="hover:text-accent hover:underline"
          >
            {a.name ?? a.code ?? a.id.slice(0, 8)}
          </Link>
          {idx < rootFirst.length - 1 && <ChevronRight size={12} />}
        </span>
      ))}
      <ChevronRight size={12} />
      <span className="text-text-secondary">{currentName ?? "current"}</span>
    </nav>
  );
}
