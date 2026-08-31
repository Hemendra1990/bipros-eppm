"use client";

import { useMemo } from "react";
import type { SelectOption } from "@/components/common/SearchableSelect";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { CellInput, CellSelect, RowGrid, type RowGridColumn } from "./RowGrid";
import {
  CATEGORY_OPTIONS,
  SEVERITY_OPTIONS,
  STATUS_OPTIONS,
} from "./IssueBadges";
import { useIssueAssignees } from "./useIssueAssignees";
import type {
  DprIssueRow,
  IssueCategory,
  IssueSeverity,
  IssueStatus,
} from "@/lib/types/dpr";

const UNASSIGNED = "";
/** Prefix for a legacy free-text assignee (no user id) shown as a one-off, non-actionable option. */
const LEGACY_PREFIX = "__legacy__:";

const blank = (): DprIssueRow => ({
  id: null,
  title: "",
  description: null,
  category: "OTHER",
  severity: "MEDIUM",
  status: "OPEN",
  supervisorUserId: null,
  supervisorName: null,
  assignedToUserId: null,
  assignedToName: null,
  resolutionNotes: null,
  interventionRequired: false,
  dueDate: null,
});

interface Props {
  projectId: string;
  rows: DprIssueRow[];
  onChange: (rows: DprIssueRow[]) => void;
}

/**
 * Assigned-to is a strict PROJECT-TEAM picker (same roster as the Issues page's IssueForm and
 * the Team tab), replacing the old free-text + datalist input — the assignee drives the
 * assignment email and the Act-by reminder/escalation chain, both of which need a real user id.
 * New rows start on the explicit "Unassigned" option: the server defaults an unassigned issue
 * to the day's supervisor silently, while a person PICKED here is a real assignment and gets
 * the notification. Rows whose stored assignee isn't in the roster (off-team user, legacy
 * free-text name) render as a one-off option so they never look unassigned.
 */
export function IssuesGrid({ projectId, rows, onChange }: Props) {
  const { options: teamOptions, nameByUserId } = useIssueAssignees(projectId);

  const assigneeOptions = useMemo<SelectOption[]>(
    () => [{ value: UNASSIGNED, label: "Unassigned — defaults to the supervisor" }, ...teamOptions],
    [teamOptions]
  );

  const update = (idx: number, patch: Partial<DprIssueRow>) => {
    const next = rows.slice();
    next[idx] = { ...next[idx], ...patch };
    onChange(next);
  };
  const remove = (idx: number) => onChange(rows.filter((_, i) => i !== idx));
  const add = () => onChange([...rows, blank()]);

  const handleAssigneeChange = (idx: number, value: string) => {
    if (value.startsWith(LEGACY_PREFIX)) return; // keep the stored free-text name as-is
    if (!value) {
      update(idx, { assignedToUserId: null, assignedToName: null });
      return;
    }
    update(idx, {
      assignedToUserId: value,
      // Off-roster reselect (the one-off option) has no roster name — keep the stored one.
      assignedToName: nameByUserId.get(value) ?? rows[idx]?.assignedToName ?? null,
    });
  };

  /** Team options plus, when the row's stored assignee isn't in the roster, a one-off entry —
   *  an id-bearing off-team user keeps their real id, a legacy free-text name gets a sentinel. */
  const optionsForRow = (r: DprIssueRow): SelectOption[] => {
    if (r.assignedToUserId) {
      if (assigneeOptions.some((o) => o.value === r.assignedToUserId)) return assigneeOptions;
      return [
        ...assigneeOptions,
        {
          value: r.assignedToUserId,
          label: r.assignedToName ?? `${r.assignedToUserId.slice(0, 8)}…`,
        },
      ];
    }
    if (!r.assignedToName) return assigneeOptions;
    return [
      ...assigneeOptions,
      { value: LEGACY_PREFIX + r.assignedToName, label: `${r.assignedToName} (as typed)` },
    ];
  };

  const valueForRow = (r: DprIssueRow): string =>
    r.assignedToUserId ?? (r.assignedToName ? LEGACY_PREFIX + r.assignedToName : UNASSIGNED);

  const columns: RowGridColumn<DprIssueRow>[] = [
    {
      key: "title",
      label: "Title",
      minWidth: 220,
      grow: 1,
      render: (r, _i, u) => (
        <CellInput
          value={r.title ?? ""}
          onChange={(v) => u({ title: v })}
          placeholder="Short headline…"
        />
      ),
    },
    {
      key: "category",
      label: "Reason",
      minWidth: 180,
      render: (r, _i, u) => (
        <CellSelect
          value={r.category}
          onChange={(v) => u({ category: (v || "OTHER") as IssueCategory })}
          options={CATEGORY_OPTIONS}
        />
      ),
    },
    {
      key: "severity",
      label: "Severity",
      minWidth: 130,
      render: (r, _i, u) => (
        <CellSelect
          value={r.severity}
          onChange={(v) => u({ severity: (v || "MEDIUM") as IssueSeverity })}
          options={SEVERITY_OPTIONS}
        />
      ),
    },
    {
      key: "status",
      label: "Status",
      minWidth: 140,
      render: (r, _i, u) => (
        <CellSelect
          value={r.status}
          onChange={(v) => u({ status: (v || "OPEN") as IssueStatus })}
          options={STATUS_OPTIONS}
        />
      ),
    },
    {
      key: "assignedTo",
      label: "Assigned to",
      minWidth: 200,
      render: (r, i) => (
        <SearchableSelect
          options={optionsForRow(r)}
          value={valueForRow(r)}
          onChange={(v) => handleAssigneeChange(i, v)}
          placeholder="Pick a team member…"
        />
      ),
    },
    {
      key: "description",
      label: "Description",
      minWidth: 240,
      grow: 1,
      render: (r, _i, u) => (
        <CellInput
          value={r.description ?? ""}
          onChange={(v) => u({ description: v || null })}
          placeholder="What happened, why…"
        />
      ),
    },
    {
      // Client requirement (AI Agent sheet, DPR row): checkbox whether next-level
      // intervention is required — flags the issue for project control follow-up.
      key: "interventionRequired",
      label: "Intervention?",
      minWidth: 110,
      render: (r, _i, u) => (
        <label className="flex h-full cursor-pointer items-center justify-center" title="Next-level intervention required">
          <input
            type="checkbox"
            checked={r.interventionRequired ?? false}
            onChange={(e) => u({ interventionRequired: e.target.checked })}
            className="h-4 w-4 accent-gold"
          />
        </label>
      ),
    },
    {
      // Act-by date ("time frame to act on it") — drives the daily overdue reminder to the
      // assignee and the one-shot escalation to their reporting manager.
      key: "dueDate",
      label: "Act by",
      minWidth: 140,
      render: (r, _i, u) => (
        <input
          type="date"
          value={r.dueDate ?? ""}
          onChange={(e) => u({ dueDate: e.target.value || null })}
          className="w-full rounded border border-hairline bg-paper px-2.5 py-1.5 text-[0.95rem] text-charcoal focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold/40"
        />
      ),
    },
  ];

  return (
    <RowGrid
      title="Issues"
      rows={rows}
      columns={columns}
      onAdd={add}
      onChange={update}
      onRemove={remove}
      emptyHint="No issues logged — click Add issue to record a blocker, breakdown, or RFI."
      addLabel="Add issue"
    />
  );
}
