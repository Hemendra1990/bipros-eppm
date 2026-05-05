"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { SearchableSelect, type SelectOption } from "@/components/common/SearchableSelect";
import { resourceApi, type ResourceResponse } from "@/lib/api/resourceApi";

interface SupervisorPickerProps {
  /** Currently selected resource id, or "" / null when unset. */
  value: string | null | undefined;
  onChange: (value: string | null, resource: ResourceResponse | null) => void;
  /**
   * Restrict candidates to a single resource type. Omit to allow mixed-type
   * parenting (a supervisor can have manpower + equipment + material children).
   */
  typeCode?: "LABOR" | "EQUIPMENT" | "MATERIAL";
  /**
   * Exclude this id (and optionally the IDs in {@code excludeIds}) from the
   * candidate list — typically the resource being edited, to prevent self-parenting.
   */
  excludeId?: string | null;
  excludeIds?: ReadonlyArray<string>;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
}

/**
 * Searchable picker over the resource catalogue used wherever a supervisor or
 * parent resource needs to be chosen (org tree {@code parent_id} or HR tree
 * {@code reporting_manager_id}). Wraps {@link SearchableSelect} and loads
 * candidates via React Query — sync filtering is acceptable for now since the
 * resource catalogue is global and bounded; switch to server-side search via
 * {@code onSearchChange} once a {@code GET /v1/resources?q=} endpoint exists.
 */
export function SupervisorPicker({
  value,
  onChange,
  typeCode,
  excludeId,
  excludeIds,
  placeholder = "Search supervisor...",
  disabled,
  className,
}: SupervisorPickerProps) {
  const { data, isFetching } = useQuery({
    queryKey: ["resources", typeCode ?? "ALL"],
    queryFn: () =>
      typeCode ? resourceApi.listByType(typeCode) : resourceApi.listResources(),
  });

  const resources = useMemo(() => data?.data ?? [], [data]);

  const excluded = useMemo(() => {
    const set = new Set<string>();
    if (excludeId) set.add(excludeId);
    if (excludeIds) excludeIds.forEach((id) => set.add(id));
    return set;
  }, [excludeId, excludeIds]);

  const options: SelectOption[] = useMemo(
    () =>
      resources
        .filter((r: ResourceResponse) => !excluded.has(r.id))
        .map((r: ResourceResponse) => ({
          value: r.id,
          label: formatLabel(r),
        })),
    [resources, excluded],
  );

  const selectedLabel = useMemo(() => {
    if (!value) return "";
    const hit = resources.find((r: ResourceResponse) => r.id === value);
    return hit ? formatLabel(hit) : "";
  }, [value, resources]);

  return (
    <SearchableSelect
      options={options}
      value={value ?? ""}
      selectedLabel={selectedLabel}
      onChange={(picked) => {
        if (!picked) {
          onChange(null, null);
          return;
        }
        const r = resources.find((x: ResourceResponse) => x.id === picked) ?? null;
        onChange(picked, r);
      }}
      placeholder={placeholder}
      disabled={disabled}
      loading={isFetching}
      className={className}
    />
  );
}

function formatLabel(r: ResourceResponse): string {
  const parts: string[] = [];
  if (r.code) parts.push(r.code);
  if (r.name) parts.push(r.name);
  const head = parts.join(" — ") || r.id;
  const designation = r.manpower?.master?.designation;
  return designation ? `${head} (${designation})` : head;
}
