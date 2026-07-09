"use client";

import Link from "next/link";
import { Radio } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { agentApi } from "@/lib/api/agentApi";
import type { AgentRunDto } from "@/lib/types";

interface TickerEvent {
  id: string;
  projectCode: string;
  actor: string;
  verb: string;
  subject: string;
  href: string;
  agoLabel: string;
}

// Fallback events (used before any agent run exists, or if the feed 403s/errs).
const SEED_EVENTS: TickerEvent[] = [
  {
    id: "e1",
    projectCode: "KHASAB-2026",
    actor: "Hemu",
    verb: "submitted",
    subject: "DPR for Day 124",
    href: "/projects",
    agoLabel: "12 min ago",
  },
  {
    id: "e2",
    projectCode: "KHASAB-2026",
    actor: "S. Al-Farsi",
    verb: "approved",
    subject: "Excavation permit · Zone 3",
    href: "/permits",
    agoLabel: "47 min ago",
  },
  {
    id: "e3",
    projectCode: "KHASAB-2026",
    actor: "QC",
    verb: "raised",
    subject: "Risk R-027 · Slope stability Day 5",
    href: "/reports/risk-register",
    agoLabel: "2 h ago",
  },
];

function humanizeAgent(key: string): string {
  return key.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

function agoLabel(iso?: string | null): string {
  if (!iso) return "";
  const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} min ago`;
  const h = Math.round(mins / 60);
  if (h < 24) return `${h} h ago`;
  return `${Math.round(h / 24)} d ago`;
}

function toTicker(run: AgentRunDto): TickerEvent {
  const findings = run.findingsCount ?? 0;
  const verb =
    run.status === "RUNNING"
      ? "is analysing"
      : run.status === "SUCCEEDED"
        ? findings > 0
          ? "flagged"
          : "cleared"
        : "ran";
  const subject =
    findings > 0 ? `${findings} finding${findings === 1 ? "" : "s"}` : "the latest project data";
  return {
    id: run.id,
    projectCode: humanizeAgent(run.agentKey),
    actor: "AI agent",
    verb,
    subject,
    href: run.projectId ? `/projects/${run.projectId}/ai` : "/ai",
    agoLabel: agoLabel(run.startedAt),
  };
}

export function ActivityTicker({ events: propEvents }: { events?: TickerEvent[] }) {
  // Live portfolio agent activity; degrades to SEED_EVENTS on error / empty / missing permission.
  const { data } = useQuery({
    queryKey: ["portfolio-agent-activity"],
    queryFn: () => agentApi.portfolioActivity(20),
    retry: false,
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
  const live = (data?.data ?? []).map(toTicker);
  const events = propEvents ?? (live.length > 0 ? live : SEED_EVENTS);

  return (
    <section
      data-testid="mc-activity-ticker"
      className="mt-8 overflow-hidden rounded-2xl border border-hairline bg-parchment/40"
    >
      <div className="flex items-center gap-3 border-b border-hairline px-5 py-2.5">
        <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-gold-tint/60 text-gold-deep">
          <Radio size={11} strokeWidth={2} />
        </span>
        <span className="text-[10.5px] font-semibold uppercase tracking-[0.14em] text-ash">
          Live activity
        </span>
        <span className="ml-auto text-[10.5px] font-medium text-ash">
          {events.length} events
        </span>
      </div>

      <ul className="divide-y divide-hairline/60">
        {events.map((e) => (
          <li key={e.id}>
            <Link
              href={e.href}
              className="group flex items-center gap-3 px-5 py-2.5 text-[12.5px] transition-colors hover:bg-ivory/60"
            >
              <span
                aria-hidden
                className="inline-block h-1.5 w-1.5 shrink-0 rounded-full bg-gold"
              />
              <span className="shrink-0 font-mono text-[10.5px] font-semibold uppercase tracking-[0.1em] text-ash">
                {e.projectCode}
              </span>
              <span className="truncate text-charcoal">
                <span className="font-semibold">{e.actor}</span>{" "}
                <span className="text-slate">{e.verb}</span> {e.subject}
              </span>
              <span className="ml-auto shrink-0 text-[10.5px] font-medium uppercase tracking-[0.1em] text-ash">
                {e.agoLabel}
              </span>
              <span
                aria-hidden
                className="shrink-0 text-gold-deep opacity-0 transition-opacity group-hover:opacity-100"
              >
                ›
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
