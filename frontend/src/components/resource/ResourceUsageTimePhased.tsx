"use client";

import React, { useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronRight } from "lucide-react";
import { format, parse } from "date-fns";
import {
  resourceApi,
  type ResourceTypeUsage,
  type ResourceUsageNode,
  type ResourceUsageTimePhasedResponse,
} from "@/lib/api/resourceApi";

interface Props {
  projectId: string;
}

const NAME_COL_WIDTH = 340;
const MONTH_COL_WIDTH = 120;
const ROW_HEIGHT = 56;
const HEADER_HEIGHT = 64;

function formatPeriod(periodKey: string): string {
  // "2026-05" → "May 2026"
  const date = parse(periodKey, "yyyy-MM", new Date());
  return format(date, "MMM yyyy");
}

function formatNumber(value: number | null | undefined): string {
  if (value == null) return "";
  if (value === 0) return "";
  if (Math.abs(value - Math.round(value)) < 0.01) return Math.round(value).toLocaleString();
  return value.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

export function ResourceUsageTimePhased({ projectId }: Props) {
  const { data, isLoading, error } = useQuery({
    queryKey: ["resource-usage-time-phased", projectId],
    queryFn: () => resourceApi.getTimePhasedUsage(projectId),
  });

  const usage: ResourceUsageTimePhasedResponse | undefined = data?.data ?? undefined;

  if (isLoading) {
    return <div className="text-center text-text-secondary py-12">Loading time-phased usage…</div>;
  }
  if (error) {
    return (
      <div className="rounded-lg border border-dashed border-border py-12 text-center">
        <p className="text-text-secondary">Failed to load time-phased usage. Please try again.</p>
      </div>
    );
  }
  if (!usage || usage.periods.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border py-12 text-center">
        <h3 className="text-lg font-medium text-text-primary">No time-phased data</h3>
        <p className="mt-2 text-text-secondary">
          Set a planned start / finish on the project (or its activities) to see the time-phased view.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <Legend />
      <Grid usage={usage} />
    </div>
  );
}

function Legend() {
  return (
    <div className="flex items-center gap-5 text-xs text-text-secondary px-1">
      <span className="inline-flex items-center gap-1.5">
        <span className="text-[10px] font-semibold uppercase text-text-muted">P</span>
        <span>Planned units</span>
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="text-[10px] font-semibold uppercase text-success">A</span>
        <span>Actual units</span>
      </span>
      <span className="text-text-muted">— each cell stacks planned (top) over actual (bottom); “—” means none</span>
    </div>
  );
}

function Grid({ usage }: { usage: ResourceUsageTimePhasedResponse }) {
  const { periods, resourceTypes } = usage;

  const [expanded, setExpanded] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    for (const t of resourceTypes) initial[`type-${t.resourceTypeId}`] = true;
    return initial;
  });
  const toggle = (key: string) =>
    setExpanded((prev) => ({ ...prev, [key]: !prev[key] }));

  const scrollerRef = useRef<HTMLDivElement | null>(null);
  const totalGridWidth = NAME_COL_WIDTH + MONTH_COL_WIDTH * periods.length;

  return (
    <div className="rounded-xl border border-border bg-surface shadow-xl overflow-hidden">
      <div
        ref={scrollerRef}
        className="overflow-x-auto"
        style={{ maxHeight: "70vh", overflowY: "auto" }}
      >
        <div style={{ minWidth: totalGridWidth }}>
          {/* Header */}
          <div
            className="grid bg-surface border-b border-border/50 sticky top-0 z-20"
            style={{
              gridTemplateColumns: `${NAME_COL_WIDTH}px repeat(${periods.length}, ${MONTH_COL_WIDTH}px)`,
              height: HEADER_HEIGHT,
            }}
          >
            <div
              className="flex items-center px-3 text-xs font-semibold uppercase tracking-wider text-text-secondary border-r border-border/50 sticky left-0 z-30 bg-surface"
            >
              Resource / Activity
            </div>
            {periods.map((p) => (
              <div
                key={p}
                className="flex flex-col items-end justify-center px-2 text-xs font-semibold uppercase tracking-wider text-text-secondary border-r border-border/30"
              >
                <span>{formatPeriod(p)}</span>
                <span className="text-[10px] font-normal normal-case text-text-muted mt-0.5">
                  P · A
                </span>
              </div>
            ))}
          </div>

          {resourceTypes.map((type) => (
            <TypeRows
              key={type.resourceTypeId}
              type={type}
              periods={periods}
              expanded={expanded}
              toggle={toggle}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

function TypeRows({
  type,
  periods,
  expanded,
  toggle,
}: {
  type: ResourceTypeUsage;
  periods: string[];
  expanded: Record<string, boolean>;
  toggle: (key: string) => void;
}) {
  const key = `type-${type.resourceTypeId}`;
  const isOpen = !!expanded[key];
  const hasChildren = type.resources.length > 0;

  return (
    <>
      <Row
        level={0}
        isGroup
        isOpen={isOpen}
        onToggle={hasChildren ? () => toggle(key) : undefined}
        label={
          <>
            <span className="font-bold text-base text-amber-300 uppercase tracking-wide">
              {type.resourceTypeName}
            </span>
            <span className="ml-2.5 text-[11px] font-semibold text-amber-200 bg-amber-500/20 px-2 py-0.5 rounded-full">
              {type.resources.length}
            </span>
          </>
        }
        plannedByPeriod={type.plannedByPeriod}
        actualByPeriod={type.actualByPeriod}
        periods={periods}
        emphasis="strong"
      />
      {isOpen &&
        type.resources.map((resource) => (
          <ResourceRows
            key={resource.resourceId}
            resource={resource}
            periods={periods}
            expanded={expanded}
            toggle={toggle}
          />
        ))}
    </>
  );
}

function ResourceRows({
  resource,
  periods,
  expanded,
  toggle,
}: {
  resource: ResourceUsageNode;
  periods: string[];
  expanded: Record<string, boolean>;
  toggle: (key: string) => void;
}) {
  const key = `resource-${resource.resourceId}`;
  const isOpen = !!expanded[key];
  const hasChildren = resource.activities.length > 0;

  return (
    <>
      <Row
        level={1}
        isGroup
        isOpen={isOpen}
        onToggle={hasChildren ? () => toggle(key) : undefined}
        label={
          <>
            <span className="font-semibold text-sm text-cyan-100">
              {resource.resourceName}
            </span>
            <span className="ml-2 text-[11px] font-medium text-cyan-200 bg-cyan-500/20 px-1.5 py-0.5 rounded-full">
              {resource.activities.length}
            </span>
            {resource.unit && (
              <span className="ml-2 text-[11px] text-cyan-400/70">({resource.unit})</span>
            )}
          </>
        }
        plannedByPeriod={resource.plannedByPeriod}
        actualByPeriod={resource.actualByPeriod}
        periods={periods}
        emphasis="medium"
      />
      {isOpen &&
        resource.activities.map((activity) => (
          <Row
            key={`activity-${activity.activityId}`}
            level={2}
            isGroup={false}
            label={
              <span className="text-sm text-text-secondary truncate">
                {activity.activityName}
              </span>
            }
            plannedByPeriod={activity.plannedByPeriod}
            actualByPeriod={activity.actualByPeriod}
            periods={periods}
            emphasis="leaf"
          />
        ))}
    </>
  );
}

/**
 * Visual hierarchy is the core of this view: scanning down a long tree, the user must instantly
 * tell whether they're looking at a Resource Type (rollup over many resources), a Resource
 * (rollup over many activities), or an Activity (leaf). Three levels, three distinct treatments:
 *
 *  - strong (Type)   — accent-tinted band + 4px accent left border + uppercase bold label
 *  - medium (Resource) — neutral elevated band + 2px muted left border + semibold label
 *  - leaf   (Activity) — transparent, no border, regular label
 *
 * Cell text scales the same way: larger, brighter at the type level so the rollup pops; smaller
 * and softer at the leaf so individual rows don't compete with their group totals.
 */
function Row({
  level,
  isGroup,
  isOpen,
  onToggle,
  label,
  plannedByPeriod,
  actualByPeriod,
  periods,
  emphasis,
}: {
  level: number;
  isGroup: boolean;
  isOpen?: boolean;
  onToggle?: () => void;
  label: React.ReactNode;
  plannedByPeriod: Record<string, number> | null;
  actualByPeriod: Record<string, number> | null;
  periods: string[];
  emphasis: "strong" | "medium" | "leaf";
}) {
  const indent = 12 + level * 18;

  // Three distinct color families so the level is unmistakable at a glance:
  //   strong  → amber/gold   (matches the brand accent — "category header")
  //   medium  → cyan/teal    (clearly different from gold without competing — "individual entity")
  //   leaf    → neutral      (no tint, recedes — "leaf detail")
  // The right-grid bg is translucent (looks like a wash over the dark surface), the sticky left
  // column uses an opaque variant so horizontal scroll doesn't bleed through it.
  const rowBg =
    emphasis === "strong"
      ? "bg-amber-500/15 hover:bg-amber-500/25"
      : emphasis === "medium"
        ? "bg-cyan-500/10 hover:bg-cyan-500/20"
        : "hover:bg-slate-700/40";

  const stickyBg =
    emphasis === "strong"
      ? "bg-amber-950"
      : emphasis === "medium"
        ? "bg-cyan-950"
        : "bg-surface";

  const leftBorder =
    emphasis === "strong"
      ? "border-l-4 border-amber-500"
      : emphasis === "medium"
        ? "border-l-[3px] border-cyan-500/80"
        : "border-l-0";

  // null means "mixed units, not aggregable" → em dash in every cell.
  const isMixedUnit = plannedByPeriod === null && actualByPeriod === null;

  // Cell text emphasis matches the row level.
  const plannedTextSize =
    emphasis === "strong"
      ? "text-sm font-semibold text-text-primary"
      : emphasis === "medium"
        ? "text-sm font-medium text-text-secondary"
        : "text-sm text-text-secondary";

  const actualTextBase =
    emphasis === "strong"
      ? "text-base font-bold"
      : emphasis === "medium"
        ? "text-base font-semibold"
        : "text-sm font-medium";

  const tagSize = emphasis === "leaf" ? "text-[10px]" : "text-[11px]";

  return (
    <div
      className={`grid border-b border-border/30 ${rowBg} ${leftBorder} transition-colors`}
      style={{
        gridTemplateColumns: `${NAME_COL_WIDTH}px repeat(${periods.length}, ${MONTH_COL_WIDTH}px)`,
        height: ROW_HEIGHT,
      }}
    >
      <div
        className={`flex items-center gap-1.5 px-3 border-r border-border/40 sticky left-0 z-10 ${stickyBg}`}
        style={{ paddingLeft: indent }}
      >
        {isGroup && onToggle ? (
          <button
            type="button"
            onClick={onToggle}
            className="p-0.5 hover:bg-surface-hover rounded shrink-0"
            aria-label={isOpen ? "Collapse" : "Expand"}
          >
            {(() => {
              const Icon = isOpen ? ChevronDown : ChevronRight;
              const iconColor =
                emphasis === "strong"
                  ? "text-amber-400"
                  : emphasis === "medium"
                    ? "text-cyan-400"
                    : "text-text-muted";
              const iconSize = emphasis === "strong" ? 18 : 16;
              return <Icon size={iconSize} className={iconColor} />;
            })()}
          </button>
        ) : (
          <div className="w-[22px] shrink-0" />
        )}
        <div className="min-w-0 flex items-center truncate">{label}</div>
      </div>

      {periods.map((p) => {
        const planned = plannedByPeriod?.[p];
        const actual = actualByPeriod?.[p];
        const plannedText = formatNumber(planned) || "—";
        const actualText = formatNumber(actual) || "—";
        return (
          <div
            key={p}
            className="flex flex-col items-end justify-center gap-0.5 px-2.5 border-r border-border/20"
          >
            {isMixedUnit ? (
              <span className="text-text-muted text-base">—</span>
            ) : (
              <>
                <div className="flex items-baseline gap-1.5 leading-tight">
                  <span className={`${tagSize} font-semibold uppercase text-text-muted`}>P</span>
                  <span className={plannedTextSize}>{plannedText}</span>
                </div>
                <div className="flex items-baseline gap-1.5 leading-tight">
                  <span className={`${tagSize} font-semibold uppercase text-success/80`}>A</span>
                  <span
                    className={`${actualTextBase} ${
                      actual ? "text-success" : "text-text-muted"
                    }`}
                  >
                    {actualText}
                  </span>
                </div>
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}
