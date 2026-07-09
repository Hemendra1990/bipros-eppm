"use client";

import { useEffect, useState } from "react";
import { Activity, Radio, Sparkles } from "lucide-react";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils/cn";
import { AgentAvatar } from "./AgentAvatar";
import { useAgentStream } from "./useAgentStream";
import { humanizeType, runStatusTone, severityMeta } from "./agentMeta";

function fmtElapsed(ms: number): string {
  const s = Math.floor(ms / 1000);
  const mm = Math.floor(s / 60);
  const ss = s % 60;
  return `${mm}:${ss.toString().padStart(2, "0")}`;
}

function timeAgo(ts: number): string {
  const s = Math.floor((Date.now() - ts) / 1000);
  if (s < 5) return "just now";
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  return `${Math.floor(m / 60)}h ago`;
}

export function AgentActivityFeed({ projectId }: { projectId: string }) {
  const { agents, live, findingPings, connected, mode, runningCount, isLoading } =
    useAgentStream(projectId);

  // Tick once per second only while something is running (drives the elapsed
  // ticker); idle otherwise so we don't churn renders.
  const [, setTick] = useState(0);
  useEffect(() => {
    if (runningCount === 0) return;
    const id = setInterval(() => setTick((t) => t + 1), 1000);
    return () => clearInterval(id);
  }, [runningCount]);

  return (
    <Card variant="flat" className="p-0">
      <div className="flex items-center justify-between border-b border-hairline px-4 py-3">
        <div className="flex items-center gap-2">
          <Activity size={15} className="text-gold-deep" />
          <span className="font-display text-sm font-semibold text-charcoal">
            Live activity
          </span>
        </div>
        <span
          className={cn(
            "inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide",
            connected
              ? "border-emerald/30 bg-emerald/10 text-emerald"
              : "border-hairline bg-ivory text-slate",
          )}
          title={
            connected
              ? "Connected to the live agent stream"
              : "Live stream unavailable — polling for updates"
          }
        >
          <Radio size={10} className={connected ? "animate-pulse" : ""} />
          {mode === "stream" ? "Live" : "Polling"}
        </span>
      </div>

      {/* Agent roster */}
      <div className="divide-y divide-hairline">
        {agents.length === 0 && isLoading && (
          <div className="px-4 py-6 text-center text-sm text-text-muted">Loading agents…</div>
        )}
        {agents.length === 0 && !isLoading && (
          <div className="px-4 py-6 text-center text-sm text-text-muted">
            No agents registered for this project yet.
          </div>
        )}
        {agents.map((a) => {
          const l = live[a.key];
          const status = l?.status ?? a.lastRun?.status ?? "IDLE";
          const isRunning =
            status === "RUNNING" || status === "GATHERING" || status === "NARRATING" || status === "PENDING";
          const tone = runStatusTone(status);
          const findings = l?.findingsCount ?? a.lastRun?.findingsCount ?? 0;
          return (
            <div key={a.key} className="flex items-center gap-3 px-4 py-2.5">
              <AgentAvatar
                agentKey={a.key}
                displayName={a.displayName}
                size="sm"
                working={isRunning}
              />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium text-text-primary">
                  {a.displayName}
                </div>
                <div className={cn("text-[11px]", tone.tone)}>
                  {isRunning ? l?.label ?? "Working" : tone.label}
                  {isRunning && l?.startedAt != null && (
                    <span className="ml-1 font-mono tabular-nums text-text-muted">
                      {fmtElapsed(Date.now() - l.startedAt)}
                    </span>
                  )}
                </div>
              </div>
              {findings > 0 && (
                <span className="inline-flex items-center gap-1 rounded-md border border-gold/40 bg-gold-tint px-1.5 py-0.5 text-[10px] font-semibold text-gold-ink">
                  <Sparkles size={9} />
                  {findings}
                </span>
              )}
            </div>
          );
        })}
      </div>

      {/* Recent finding pings */}
      {findingPings.length > 0 && (
        <div className="border-t border-hairline">
          <div className="px-4 pt-3 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate">
            Just detected
          </div>
          <ul className="max-h-56 space-y-0 overflow-y-auto px-2 py-2">
            {findingPings.map((p) => {
              const sev = severityMeta(p.severity ?? "INFO");
              return (
                <li
                  key={p.key}
                  className="flex items-start gap-2 rounded-lg px-2 py-1.5 hover:bg-ivory"
                >
                  <span
                    className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full"
                    style={{ background: sev.hue }}
                    aria-hidden
                  />
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[13px] text-text-primary">
                      {p.title ?? humanizeType(p.findingType ?? "Finding")}
                    </div>
                    <div className="text-[10px] text-text-muted">
                      {p.findingType ? humanizeType(p.findingType) : "Finding"} · {timeAgo(p.at)}
                      {p.notifiable ? " · notifiable" : ""}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </Card>
  );
}
