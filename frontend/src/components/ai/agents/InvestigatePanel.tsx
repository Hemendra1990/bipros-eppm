"use client";

import { useCallback, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";
import { Loader2, Search, Sparkles } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { agentApi } from "@/lib/api/agentApi";
import { severityMeta, humanizeType } from "./agentMeta";

// Compact markdown renderer — same idiom as AiChatPanel's `markdownComponents`.
const md: Components = {
  p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
  ul: ({ children }) => <ul className="mb-2 list-disc pl-4">{children}</ul>,
  ol: ({ children }) => <ol className="mb-2 list-decimal pl-4">{children}</ol>,
  li: ({ children }) => <li className="mb-1">{children}</li>,
  strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  code: ({ children }) => (
    <code className="rounded bg-ivory px-1 py-0.5 font-mono text-[13px] text-gold-ink">
      {children}
    </code>
  ),
  h1: ({ children }) => <h2 className="mb-2 text-base font-bold">{children}</h2>,
  h2: ({ children }) => <h2 className="mb-2 text-base font-bold">{children}</h2>,
  h3: ({ children }) => <h3 className="mb-1 text-sm font-bold">{children}</h3>,
  a: ({ href, children }) => (
    <a href={href} className="text-gold-deep underline">
      {children}
    </a>
  ),
};

interface Ping {
  key: string;
  title?: string;
  severity?: string;
  findingType?: string;
}

export function InvestigatePanel({ projectId }: { projectId: string }) {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [pings, setPings] = useState<Ping[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const seq = useRef(0);

  const ask = useCallback(async () => {
    const q = question.trim();
    if (!q || streaming) return;
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setStreaming(true);
    setAnswer("");
    setPings([]);
    setError(null);
    setStatus("Investigating…");

    try {
      for await (const ev of agentApi.investigate(projectId, q, ctrl.signal)) {
        const d = ev.data as Record<string, unknown>;
        switch (ev.event) {
          case "token":
            setAnswer((a) => a + ((d.delta as string) || (d.token as string) || ""));
            break;
          case "gathering":
            setStatus("Gathering evidence…");
            break;
          case "narrating":
            setStatus("Writing up the answer…");
            break;
          case "finding":
            setPings((p) => [
              ...p,
              {
                key: `${(d.findingId as string) ?? "f"}-${seq.current++}`,
                title: d.title as string | undefined,
                severity: d.severity as string | undefined,
                findingType: d.findingType as string | undefined,
              },
            ]);
            break;
          case "done":
          case "final_answer":
          case "run_finished": {
            const text = (d.text as string) || (d.answer as string) || "";
            if (text) setAnswer(text);
            setStatus(null);
            break;
          }
          case "error":
            setError((d.message as string) || "Something went wrong.");
            setStatus(null);
            break;
          default: {
            // Tolerate unknown event names that still carry a delta/text.
            const delta = (d.delta as string) || (d.token as string) || "";
            if (delta) setAnswer((a) => a + delta);
          }
        }
      }
    } catch {
      // Endpoint may not exist yet, or the network dropped.
      if (!answer) {
        setError(
          "The Investigate service isn't available yet. Once the supervisor endpoint is live, answers will stream here.",
        );
      }
    } finally {
      setStreaming(false);
      setStatus(null);
      abortRef.current = null;
    }
  }, [question, streaming, projectId, answer]);

  return (
    <Card variant="flat">
      <div className="mb-3 flex items-center gap-2">
        <Search size={15} className="text-gold-deep" />
        <h3 className="font-display text-sm font-semibold text-charcoal">Investigate</h3>
        <span className="text-[11px] text-text-muted">Ask the supervisor agent anything about this project</span>
      </div>

      <div className="flex items-end gap-2">
        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              ask();
            }
          }}
          rows={1}
          placeholder="e.g. Why is the cost forecast worse than last week?"
          disabled={streaming}
          className="max-h-[120px] min-h-[42px] flex-1 resize-none rounded-lg border border-hairline bg-ivory px-3 py-2.5 text-sm text-text-primary placeholder-text-muted focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold"
        />
        <Button onClick={ask} disabled={!question.trim() || streaming}>
          {streaming ? <Loader2 size={15} className="animate-spin" /> : <Sparkles size={15} />}
          Ask
        </Button>
      </div>

      {(answer || status || error || pings.length > 0) && (
        <div className="mt-4 rounded-xl border border-hairline bg-ivory/60 p-4">
          {pings.length > 0 && (
            <div className="mb-3 flex flex-wrap gap-1.5">
              {pings.map((p) => {
                const sev = severityMeta(p.severity ?? "INFO");
                return (
                  <span
                    key={p.key}
                    className="inline-flex items-center gap-1.5 rounded-md border border-hairline bg-paper px-2 py-1 text-[11px] text-text-secondary"
                  >
                    <span
                      className="h-1.5 w-1.5 rounded-full"
                      style={{ background: sev.hue }}
                      aria-hidden
                    />
                    {p.title ?? humanizeType(p.findingType ?? "Finding")}
                  </span>
                );
              })}
            </div>
          )}

          {error ? (
            <p className="text-sm text-burgundy">{error}</p>
          ) : (
            <div className="text-sm leading-relaxed text-text-primary">
              {answer && (
                <ReactMarkdown remarkPlugins={[remarkGfm]} components={md}>
                  {answer}
                </ReactMarkdown>
              )}
              {status && (
                <div className="mt-2 flex items-center gap-2 text-xs text-text-muted">
                  <Loader2 size={12} className="animate-spin" />
                  {status}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
