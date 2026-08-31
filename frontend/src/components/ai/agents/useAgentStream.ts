"use client";

import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { agentApi } from "@/lib/api/agentApi";
import type { AgentSummaryDto } from "@/lib/types";

export interface LiveEntry {
  /** Raw status token (RUNNING / GATHERING / NARRATING / SUCCEEDED / …). */
  status: string;
  /** Friendly phase label for the current status. */
  label: string;
  /** Epoch ms when the current run started (for the elapsed ticker). */
  startedAt?: number;
  findingsCount?: number;
}

export interface FindingPing {
  key: string;
  findingId?: string;
  agentKey?: string;
  findingType?: string;
  severity?: string;
  title?: string;
  notifiable?: boolean;
  at: number;
}

const RUNNING_STATES = new Set(["RUNNING", "GATHERING", "NARRATING", "PENDING"]);
const MAX_PINGS = 30;
const MAX_STREAM_FAILURES = 4; // give up on SSE after this many, rely on polling

export interface UseAgentStream {
  agents: AgentSummaryDto[];
  live: Record<string, LiveEntry>;
  findingPings: FindingPing[];
  connected: boolean;
  mode: "stream" | "polling";
  runningCount: number;
  isLoading: boolean;
  refresh: () => void;
}

/**
 * Live agent activity for one project. Layers an SSE stream over a react-query
 * poll of `listAgents`:
 *   - the stream (if the endpoint exists) drives instant status + finding pings,
 *   - the poll is the always-available fallback feed and the source of the agent
 *     roster; its interval tightens when the stream is disconnected.
 * The stream reconnects with exponential backoff and gracefully gives up (→
 * "polling" mode) after repeated failures so a missing backend endpoint is a
 * non-event.
 */
export function useAgentStream(
  projectId: string | undefined,
  enabled = true,
): UseAgentStream {
  const qc = useQueryClient();
  const [live, setLive] = useState<Record<string, LiveEntry>>({});
  const [findingPings, setFindingPings] = useState<FindingPing[]>([]);
  const [connected, setConnected] = useState(false);
  const [streamDead, setStreamDead] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["agents", projectId],
    queryFn: () => agentApi.listAgents(projectId as string),
    enabled: enabled && !!projectId,
    refetchInterval: connected ? 45_000 : 10_000,
  });
  const agents = data?.data ?? [];

  const seq = useRef(0);

  useEffect(() => {
    if (!enabled || !projectId || streamDead) return;
    let stopped = false;
    let failures = 0;
    let retryTimer: ReturnType<typeof setTimeout> | null = null;
    const ctrl = new AbortController();

    const handle = (event: string, d: Record<string, unknown>) => {
      const agentKey = typeof d.agentKey === "string" ? d.agentKey : undefined;
      // The SSE frame is the whole AgentStreamEvent: agentKey/type are top-level, but the
      // type-specific fields (title, findingType, status, findingsCount, …) are nested under `payload`.
      const payload = (d.payload && typeof d.payload === "object" ? d.payload : {}) as Record<
        string,
        unknown
      >;
      if (event === "run_started" && agentKey) {
        setLive((p) => ({
          ...p,
          [agentKey]: {
            status: "RUNNING",
            label: "Working",
            startedAt: Date.now(),
            findingsCount: p[agentKey]?.findingsCount ?? 0,
          },
        }));
      } else if ((event === "gathering" || event === "narrating") && agentKey) {
        const label = event === "gathering" ? "Gathering evidence" : "Writing findings";
        setLive((p) => ({
          ...p,
          [agentKey]: {
            ...(p[agentKey] ?? { startedAt: Date.now(), findingsCount: 0 }),
            status: event.toUpperCase(),
            label,
          },
        }));
      } else if (event === "finding") {
        setFindingPings((p) =>
          [
            {
              key: `${(payload.findingId as string) ?? "f"}-${seq.current++}`,
              findingId: payload.findingId as string | undefined,
              agentKey,
              findingType: payload.findingType as string | undefined,
              severity: payload.severity as string | undefined,
              title: payload.title as string | undefined,
              notifiable: payload.notifiable as boolean | undefined,
              at: Date.now(),
            },
            ...p,
          ].slice(0, MAX_PINGS),
        );
        if (agentKey) {
          setLive((p) => ({
            ...p,
            [agentKey]: {
              ...(p[agentKey] ?? { status: "RUNNING", label: "Working" }),
              findingsCount: (p[agentKey]?.findingsCount ?? 0) + 1,
            },
          }));
        }
      } else if (event === "run_finished" && agentKey) {
        const status = (payload.status as string) || "SUCCEEDED";
        const findingsCount =
          typeof payload.findingsCount === "number" ? payload.findingsCount : undefined;
        setLive((p) => ({
          ...p,
          [agentKey]: {
            status,
            label: status === "SUCCEEDED" ? "Done" : status,
            startedAt: undefined,
            findingsCount: findingsCount ?? p[agentKey]?.findingsCount,
          },
        }));
        qc.invalidateQueries({ queryKey: ["agents", projectId] });
      }
    };

    const connect = async () => {
      if (stopped) return;
      try {
        const gen = agentApi.streamAgents(projectId, ctrl.signal);
        // First `.next()` triggers the fetch — a non-2xx / missing endpoint
        // throws here, before we ever flip `connected`, so there's no flicker.
        let res = await gen.next();
        if (stopped) return;
        failures = 0;
        setConnected(true);
        while (!res.done) {
          handle(res.value.event, res.value.data);
          res = await gen.next();
          if (stopped) return;
        }
        // Server closed the stream cleanly — reconnect after a short delay.
        setConnected(false);
        if (!stopped) retryTimer = setTimeout(connect, 3_000);
      } catch {
        setConnected(false);
        if (stopped) return;
        failures += 1;
        if (failures >= MAX_STREAM_FAILURES) {
          setStreamDead(true); // fall back to polling permanently for this mount
          return;
        }
        const backoff = Math.min(15_000, 1_000 * 2 ** failures);
        retryTimer = setTimeout(connect, backoff);
      }
    };

    connect();
    return () => {
      stopped = true;
      if (retryTimer) clearTimeout(retryTimer);
      ctrl.abort();
      setConnected(false);
    };
  }, [enabled, projectId, streamDead, qc]);

  const runningCount = Object.values(live).filter((e) =>
    RUNNING_STATES.has(e.status),
  ).length;

  return {
    agents,
    live,
    findingPings,
    connected,
    mode: connected ? "stream" : "polling",
    runningCount,
    isLoading,
    refresh: () => qc.invalidateQueries({ queryKey: ["agents", projectId] }),
  };
}
