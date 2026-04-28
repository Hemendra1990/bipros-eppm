"use client";

import { useState, useRef, useEffect, useMemo } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Send, Zap, MessageCircle, Plug } from "lucide-react";

import {
  analyticsApi,
  isLlmNotConfigured,
  type AnalyticsAssistantResponse,
  type AnalyticsQueryDto,
} from "@/lib/api/analyticsApi";
import { llmProvidersApi } from "@/lib/api/llmProvidersApi";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { TabTip } from "@/components/common/TabTip";
import { AssistantMessage } from "@/components/analytics/AssistantMessage";

// Two flavours of bubble: live submissions get the rich AssistantMessage
// renderer; old history rows lack the structured fields so we render their
// plain `responseText` in compact mode.
type ChatTurn =
  | {
      kind: "live";
      id: string;
      queryText: string;
      createdAt: string;
      response: AnalyticsAssistantResponse;
    }
  | {
      kind: "history";
      id: string;
      queryText: string;
      createdAt: string;
      legacy: AnalyticsQueryDto;
    };

const SUGGESTED_QUERIES = [
  "Show schedule variance for the NHAI project",
  "What is the cost overrun risk?",
  "Which projects are delayed?",
  "What are the top risks?",
  "How are resources allocated?",
];

export default function AnalyticsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [queryText, setQueryText] = useState("");
  // Live turns are session-only; history turns come from the server via
  // useQuery and are derived below. Combining them in render avoids the
  // setState-in-effect cascade pattern.
  const [liveTurns, setLiveTurns] = useState<ChatTurn[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const { data: providers, isLoading: isLoadingProviders } = useQuery({
    queryKey: ["llm-providers"],
    queryFn: async () => (await llmProvidersApi.list()).data ?? [],
  });

  const hasUsableProvider = !!providers?.some(
    (p) => p.isDefault && p.status === "ACTIVE"
  );

  const { data: historyData } = useQuery({
    queryKey: ["analytics-history"],
    queryFn: () => analyticsApi.getQueryHistory(20),
    enabled: hasUsableProvider,
  });

  const submitQueryMutation = useMutation({
    mutationFn: (text: string) =>
      analyticsApi.submitQuery({ queryText: text }),
    onSuccess: (response, text) => {
      const turn: ChatTurn = {
        kind: "live",
        id: `live-${Date.now()}`,
        queryText: text,
        createdAt: new Date().toISOString(),
        response,
      };
      setLiveTurns((prev) => [turn, ...prev]);
      setQueryText("");
    },
    onError: (err) => {
      if (isLlmNotConfigured(err)) {
        // Default provider just got disabled or unset — drop into the
        // empty-state by re-fetching providers.
        queryClient.invalidateQueries({ queryKey: ["llm-providers"] });
        return;
      }
      const msg =
        (err as { response?: { data?: { error?: { message?: string } } } })
          ?.response?.data?.error?.message ?? "Query failed. Please try again.";
      toast.error(msg);
    },
  });

  const historyTurns = useMemo<ChatTurn[]>(() => {
    if (!historyData?.data) return [];
    return historyData.data
      .slice()
      .sort(
        (a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )
      .map((dto) => ({
        kind: "history" as const,
        id: dto.id,
        queryText: dto.queryText,
        createdAt: dto.createdAt,
        legacy: dto,
      }));
  }, [historyData]);

  // Live turns are newer than anything that came back from history.
  const turns = useMemo<ChatTurn[]>(
    () => [...liveTurns, ...historyTurns],
    [liveTurns, historyTurns]
  );

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [turns]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!queryText.trim() || submitQueryMutation.isPending) return;
    submitQueryMutation.mutate(queryText);
  };

  const handleSuggestedQuery = (query: string) => {
    if (submitQueryMutation.isPending) return;
    setQueryText(query);
    submitQueryMutation.mutate(query);
  };

  // ---- Empty state: no LLM provider configured ----
  if (!isLoadingProviders && !hasUsableProvider) {
    return (
      <div className="flex flex-col h-[calc(100vh-80px)] bg-surface/80">
        <div className="border-b bg-surface/50 p-6">
          <div className="max-w-4xl mx-auto">
            <div className="flex items-center gap-3 mb-2">
              <Zap className="text-accent" size={28} />
              <h1 className="text-3xl font-bold">Analytics Assistant</h1>
            </div>
            <p className="text-text-secondary">
              Ask natural language questions about your projects. I can help with
              cost, schedule, risk, resource, and project status queries.
            </p>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto p-6">
          <Card className="max-w-xl mx-auto mt-12" variant="elevated">
            <CardHeader>
              <CardTitle>Configure your LLM provider</CardTitle>
              <CardDescription>
                The Analytics Assistant uses your own LLM key (BYOK). Add a
                provider and mark it as default to start asking questions.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button
                onClick={() => router.push("/settings/llm-providers")}
                className="gap-2"
              >
                <Plug size={16} /> Configure LLM provider
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-[calc(100vh-80px)] bg-surface/80">
      {/* Header */}
      <div className="border-b bg-surface/50 p-6">
        <div className="max-w-4xl mx-auto">
          <div className="flex items-center gap-3 mb-2">
            <Zap className="text-accent" size={28} />
            <h1 className="text-3xl font-bold">Analytics Assistant</h1>
          </div>
          <p className="text-text-secondary">
            Ask natural language questions about your projects. I can help with cost,
            schedule, risk, resource, and project status queries.
          </p>
          <TabTip
            title="Analytics & Insights"
            description="Advanced analytics powered by AI. Ask natural language questions about your project data and get insights on schedule, cost, and risk trends."
          />
        </div>
      </div>

      {/* Chat Area */}
      <div className="flex-1 overflow-y-auto p-6">
        <div className="max-w-4xl mx-auto space-y-4">
          {turns.length === 0 && !submitQueryMutation.isPending ? (
            <div className="text-center py-12">
              <MessageCircle className="mx-auto text-text-muted mb-4" size={48} />
              <h2 className="text-xl font-semibold text-text-secondary mb-2">
                No queries yet
              </h2>
              <p className="text-text-muted mb-6">
                Ask me anything about your projects and I&apos;ll help with insights
              </p>

              <div className="space-y-3">
                <p className="text-sm font-semibold text-text-secondary mb-3">
                  Try one of these queries:
                </p>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                  {SUGGESTED_QUERIES.map((query) => (
                    <button
                      key={query}
                      onClick={() => handleSuggestedQuery(query)}
                      className="text-left p-3 rounded-lg border border-border hover:border-blue-400 hover:bg-accent-hover/10 transition text-sm text-text-secondary hover:text-accent"
                    >
                      {query}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <>
              {turns.map((turn) => (
                <div key={turn.id} className="space-y-2">
                  {/* User Query Bubble */}
                  <div className="flex justify-end">
                    <div className="max-w-2xl bg-accent text-text-primary rounded-lg p-4 rounded-br-none">
                      <p className="text-sm">{turn.queryText}</p>
                      <p className="text-xs text-blue-100 mt-1">
                        {new Date(turn.createdAt).toLocaleTimeString()}
                        {turn.kind === "history" && " · earlier session"}
                      </p>
                    </div>
                  </div>

                  {/* Assistant Response Bubble */}
                  <div className="flex justify-start">
                    <div className="max-w-2xl w-full bg-surface/50 border border-border rounded-lg p-4 rounded-bl-none shadow-sm">
                      {turn.kind === "live" ? (
                        <AssistantMessage response={turn.response} />
                      ) : (
                        <div className="text-sm text-text-primary whitespace-pre-wrap leading-relaxed">
                          {turn.legacy.responseText}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))}

              {submitQueryMutation.isPending && (
                <div className="space-y-2">
                  <div className="flex justify-end">
                    <div className="max-w-2xl bg-accent text-text-primary rounded-lg p-4 rounded-br-none">
                      <p className="text-sm">{queryText}</p>
                    </div>
                  </div>
                  <div className="flex justify-start">
                    <div className="max-w-2xl bg-surface/50 border border-border rounded-lg p-4 rounded-bl-none shadow-sm">
                      <div className="flex items-center gap-2">
                        <div className="flex gap-1">
                          <div className="w-2 h-2 bg-border rounded-full animate-bounce"></div>
                          <div
                            className="w-2 h-2 bg-border rounded-full animate-bounce"
                            style={{ animationDelay: "0.2s" }}
                          ></div>
                          <div
                            className="w-2 h-2 bg-border rounded-full animate-bounce"
                            style={{ animationDelay: "0.4s" }}
                          ></div>
                        </div>
                        <span className="text-sm text-text-muted">Thinking...</span>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </>
          )}
        </div>
      </div>

      {/* Input Area */}
      <div className="border-t bg-surface/50 p-6">
        <div className="max-w-4xl mx-auto">
          <form onSubmit={handleSubmit} className="flex gap-3">
            <Input
              type="text"
              placeholder="Ask me about your projects..."
              value={queryText}
              onChange={(e) => setQueryText(e.target.value)}
              disabled={submitQueryMutation.isPending}
              className="flex-1"
            />
            <Button
              type="submit"
              disabled={!queryText.trim() || submitQueryMutation.isPending}
              className="gap-2"
            >
              <Send size={18} />
              Send
            </Button>
          </form>

          <div className="mt-3 flex flex-wrap gap-2">
            {SUGGESTED_QUERIES.slice(0, 3).map((query) => (
              <button
                key={query}
                onClick={() => handleSuggestedQuery(query)}
                className="text-xs px-3 py-1 rounded-full border border-border text-text-secondary hover:border-blue-400 hover:bg-accent-hover/10 transition"
              >
                {query}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
