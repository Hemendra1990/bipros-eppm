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

function formatPeriod(periodKey: string): { month: string; year: string } {
  const date = parse(periodKey, "yyyy-MM", new Date());
  return { month: format(date, "MMM"), year: format(date, "yyyy") };
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
    return <div className="text-center text-slate py-12 font-sans">Loading time-phased usage…</div>;
  }
  if (error) {
    return (
      <div className="rounded-xl border border-dashed border-hairline bg-paper py-12 text-center">
        <p className="text-slate">Failed to load time-phased usage. Please try again.</p>
      </div>
    );
  }
  if (!usage || usage.periods.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-hairline bg-paper py-12 text-center">
        <h3 className="font-display text-xl font-medium text-charcoal">No time-phased data</h3>
        <p className="mt-2 text-slate">
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
    <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 px-1 text-[11px] text-slate">
      <span className="inline-flex items-center gap-1.5">
        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-ash">
          P
        </span>
        <span>Planned units</span>
      </span>
      <span className="h-3 w-px bg-hairline" aria-hidden />
      <span className="inline-flex items-center gap-1.5">
        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-emerald">
          A
        </span>
        <span>Actual units</span>
      </span>
      <span className="h-3 w-px bg-hairline" aria-hidden />
      <span className="text-ash">
        Each cell stacks planned over actual; <span className="font-mono">—</span> means none.
      </span>
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
    <div className="rounded-xl border border-hairline bg-paper shadow-sm overflow-hidden">
      <div
        ref={scrollerRef}
        className="overflow-x-auto"
        style={{ maxHeight: "70vh", overflowY: "auto" }}
      >
        <div style={{ minWidth: totalGridWidth }}>
          {/* Header */}
          <div
            className="grid bg-ivory border-b border-hairline sticky top-0 z-20"
            style={{
              gridTemplateColumns: `${NAME_COL_WIDTH}px repeat(${periods.length}, ${MONTH_COL_WIDTH}px)`,
              height: HEADER_HEIGHT,
            }}
          >
            <div className="flex items-center px-4 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate border-r border-hairline sticky left-0 z-30 bg-ivory">
              Resource <span className="mx-1.5 text-gold">/</span> Activity
            </div>
            {periods.map((p, i) => {
              const { month, year } = formatPeriod(p);
              return (
                <div
                  key={p}
                  className={`flex flex-col items-end justify-center px-3 ${
                    i < periods.length - 1 ? "border-r border-hairline" : ""
                  }`}
                >
                  <span className="font-display text-[13px] font-medium leading-none text-charcoal">
                    {month}
                  </span>
                  <span className="mt-1 font-mono text-[10px] uppercase tracking-[0.12em] text-ash">
                    {year}
                  </span>
                  <span className="mt-1 font-mono text-[9px] uppercase tracking-[0.18em] text-ash">
                    P · A
                  </span>
                </div>
              );
            })}
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
            <span aria-hidden className="mr-2.5 inline-block h-1.5 w-1.5 rounded-full bg-gold shadow-[0_0_0_3px_var(--gold-tint)]" />
            <span className="font-display text-[15px] font-semibold uppercase tracking-[0.16em] text-gold-ink dark:text-gold">
              {type.resourceTypeName}
            </span>
            <span className="ml-2.5 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full border border-gold/40 bg-paper px-1.5 font-mono text-[10px] font-semibold text-gold-ink dark:text-gold">
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
            <span className="font-sans text-[13.5px] font-semibold text-charcoal">
              {resource.resourceName}
            </span>
            <span className="ml-2 inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-ivory px-1.5 font-mono text-[10px] font-medium text-slate ring-1 ring-hairline">
              {resource.activities.length}
            </span>
            {resource.unit && (
              <span className="ml-2 font-mono text-[10px] uppercase tracking-[0.10em] text-ash">
                {resource.unit}
              </span>
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
              <span className="font-sans text-[13px] text-slate truncate">
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
 * Three levels, three treatments — kept restrained so the table reads like a financial
 * statement, not a heatmap:
 *  - strong (Type)     — soft gold-tint wash, gold left rule, serif uppercase label
 *  - medium (Resource) — neutral surface, hairline left rule, sans-serif label
 *  - leaf   (Activity) — transparent, hover-only ivory wash
 *
 * Numbers are mono so columns align; emphasis comes from weight, not color.
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
  const indent = 14 + level * 18;

  // Right-grid (the long row) uses a translucent wash so it reads as a band over the surface.
  // Left sticky column uses an opaque variant so horizontal scroll doesn't bleed through it.
  const rowBg =
    emphasis === "strong"
      ? "bg-gold-tint/45 hover:bg-gold-tint/60"
      : emphasis === "medium"
        ? "hover:bg-ivory"
        : "hover:bg-ivory/70";

  const stickyBg =
    emphasis === "strong"
      ? "bg-gold-tint"
      : "bg-paper";

  const leftRule =
    emphasis === "strong"
      ? "border-l-2 border-gold"
      : emphasis === "medium"
        ? "border-l border-gold/40"
        : "border-l border-transparent";

  const isMixedUnit = plannedByPeriod === null && actualByPeriod === null;

  // Number weight — same family across levels (mono), differentiated by weight only.
  const plannedNumClass =
    emphasis === "strong"
      ? "font-mono text-[12px] font-semibold tabular-nums text-charcoal"
      : emphasis === "medium"
        ? "font-mono text-[12px] font-medium tabular-nums text-charcoal"
        : "font-mono text-[11.5px] tabular-nums text-slate";

  const actualNumClass =
    emphasis === "strong"
      ? "font-mono text-[12px] font-bold tabular-nums"
      : emphasis === "medium"
        ? "font-mono text-[12px] font-semibold tabular-nums"
        : "font-mono text-[11.5px] font-medium tabular-nums";

  const tagSize = "text-[9px] tracking-[0.14em]";

  return (
    <div
      className={`grid border-b border-hairline ${rowBg} ${leftRule} transition-colors`}
      style={{
        gridTemplateColumns: `${NAME_COL_WIDTH}px repeat(${periods.length}, ${MONTH_COL_WIDTH}px)`,
        height: ROW_HEIGHT,
      }}
    >
      <div
        className={`flex items-center gap-1.5 px-3 border-r border-hairline sticky left-0 z-10 ${stickyBg}`}
        style={{ paddingLeft: indent }}
      >
        {isGroup && onToggle ? (
          <button
            type="button"
            onClick={onToggle}
            className="p-0.5 rounded hover:bg-paper/60 shrink-0 transition-colors"
            aria-label={isOpen ? "Collapse" : "Expand"}
          >
            {(() => {
              const Icon = isOpen ? ChevronDown : ChevronRight;
              const iconColor =
                emphasis === "strong"
                  ? "text-gold-ink dark:text-gold"
                  : emphasis === "medium"
                    ? "text-slate"
                    : "text-ash";
              const iconSize = emphasis === "strong" ? 16 : 14;
              return <Icon size={iconSize} className={iconColor} />;
            })()}
          </button>
        ) : (
          <div className="w-[20px] shrink-0" />
        )}
        <div className="min-w-0 flex items-center truncate">{label}</div>
      </div>

      {periods.map((p, i) => {
        const planned = plannedByPeriod?.[p];
        const actual = actualByPeriod?.[p];
        const plannedText = formatNumber(planned) || "—";
        const actualText = formatNumber(actual) || "—";
        const hasActual = actual != null && actual !== 0;
        const isLast = i === periods.length - 1;
        return (
          <div
            key={p}
            className={`flex flex-col items-end justify-center gap-1 px-3 ${
              !isLast ? "border-r border-hairline/70" : ""
            }`}
          >
            {isMixedUnit ? (
              <span className="font-mono text-ash text-sm">—</span>
            ) : (
              <>
                <div className="flex items-baseline gap-1.5 leading-none">
                  <span className={`${tagSize} font-mono font-semibold uppercase text-ash`}>P</span>
                  <span className={plannedNumClass}>{plannedText}</span>
                </div>
                <div className="flex items-baseline gap-1.5 leading-none">
                  <span className={`${tagSize} font-mono font-semibold uppercase ${hasActual ? "text-emerald" : "text-ash"}`}>
                    A
                  </span>
                  <span className={`${actualNumClass} ${hasActual ? "text-emerald" : "text-ash"}`}>
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
