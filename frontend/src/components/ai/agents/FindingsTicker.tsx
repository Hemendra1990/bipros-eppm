"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Radio, ChevronRight } from "lucide-react";
import type { AgentFindingDto } from "@/lib/types";
import { compactNum } from "./FindingVisual";
import { severityMeta, humanizeType } from "./agentMeta";
import styles from "./FindingsTicker.module.css";

const ROTATE_MS = 5000;

/** The single most telling number on a finding (first numeric metric), compacted for the headline. */
function headlineMetric(f: AgentFindingDto): { value: string; label: string } | null {
  for (const ev of f.evidence ?? []) {
    if (ev.type !== "METRIC" || ev.value == null) continue;
    const m = String(ev.value).replace(/,/g, "").match(/-?\d+(\.\d+)?/);
    if (!m) continue;
    const isPct = /%/.test(ev.value);
    return { value: compactNum(parseFloat(m[0])) + (isPct ? "%" : ""), label: ev.label };
  }
  return null;
}

/**
 * A rotating "AI briefing desk" headline strip over the top findings — critical/high first — with a
 * live pulse, a big number, and a slide cross-fade every few seconds. Turns the wall of cards into a
 * scannable news reel. Pauses on hover; hidden when there's nothing urgent.
 */
export function FindingsTicker({
  findings,
  agentNames,
}: {
  findings: AgentFindingDto[];
  agentNames?: Record<string, string>;
}) {
  const reel = useMemo(() => {
    return [...findings]
      .filter((f) => f.severity === "CRITICAL" || f.severity === "HIGH" || f.notifiable)
      .sort((a, b) => severityMeta(b.severity).order - severityMeta(a.severity).order)
      .slice(0, 6);
  }, [findings]);

  const [idx, setIdx] = useState(0);
  const [paused, setPaused] = useState(false);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    setIdx(0);
  }, [reel.length]);

  useEffect(() => {
    if (paused || reel.length <= 1) return;
    timer.current = setInterval(() => setIdx((i) => (i + 1) % reel.length), ROTATE_MS);
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [paused, reel.length]);

  if (reel.length === 0) return null;

  const f = reel[Math.min(idx, reel.length - 1)];
  const sev = severityMeta(f.severity);
  const metric = headlineMetric(f);
  const agent = agentNames?.[f.agentKey] ?? humanizeType(f.agentKey);

  return (
    <div
      // Intentionally dark in BOTH themes (a news "lower-third"). Uses a fixed obsidian rather than
      // the theme-flipping `charcoal` token, which inverts to a light surface in dark mode and made
      // the white headline invisible.
      className="mb-4 overflow-hidden rounded-2xl text-white shadow-lg ring-1 ring-white/10"
      style={{ backgroundColor: "#1a1712" }}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
    >
      {/* top rail: live label + counter */}
      <div className={`${styles.sweep} flex items-center justify-between gap-3 border-b border-white/10 px-4 py-1.5`}>
        <div className="flex items-center gap-2">
          <span className={`${styles.dot} inline-block h-2 w-2 rounded-full`} style={{ background: "#ef4444" }} />
          <span className="text-[10px] font-bold uppercase tracking-[0.22em] text-white/80">
            <Radio size={11} className="mr-1 inline" />
            AI Briefing
          </span>
        </div>
        <div className="flex items-center gap-2 text-[10px] tabular-nums text-white/55">
          <span>
            {idx + 1} / {reel.length}
          </span>
          <div className="flex gap-1">
            {reel.map((_, i) => (
              <button
                key={i}
                aria-label={`Headline ${i + 1}`}
                onClick={() => setIdx(i)}
                className="h-1.5 w-1.5 rounded-full transition-colors"
                style={{ background: i === idx ? "#f5a623" : "rgba(255,255,255,0.28)" }}
              />
            ))}
          </div>
        </div>
      </div>

      {/* headline body */}
      <div key={f.id} className={`${styles.slide} flex items-center gap-4 px-4 py-3.5`}>
        <span
          className="shrink-0 rounded-md px-2 py-1 text-[10px] font-bold uppercase tracking-wider"
          style={{ backgroundColor: sev.hue, color: "#fff" }}
        >
          {sev.label}
        </span>

        <div className="min-w-0 flex-1">
          <div className="mb-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-white/45">
            {agent}
          </div>
          <div className="truncate font-display text-[15px] font-semibold leading-tight text-white">
            {f.title}
          </div>
        </div>

        {metric && (
          <div className="shrink-0 text-right">
            <div className="font-display text-2xl font-bold tabular-nums leading-none" style={{ color: "#ffd77a" }}>
              {metric.value}
            </div>
            <div className="mt-0.5 max-w-[130px] truncate text-[10px] uppercase tracking-wide text-white/45">
              {metric.label}
            </div>
          </div>
        )}

        <ChevronRight size={18} className="hidden shrink-0 text-white/30 sm:block" />
      </div>

      {/* progress bar toward next rotation */}
      <div className="h-0.5 w-full bg-white/10">
        <div
          key={`${f.id}-${paused}`}
          className={styles.bar}
          style={{ width: paused ? "8%" : "100%", height: "100%", background: "#f5a623" }}
        />
      </div>
    </div>
  );
}
