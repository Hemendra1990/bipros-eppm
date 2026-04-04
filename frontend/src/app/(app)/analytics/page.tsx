"use client";

import { useState, useRef, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { analyticsApi, type AnalyticsQueryDto } from "@/lib/api/analyticsApi";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Send, Zap, MessageCircle } from "lucide-react";

const SUGGESTED_QUERIES = [
  "What is the cost overrun risk?",
  "Which projects are delayed?",
  "Show schedule summary",
  "What are the top risks?",
  "How are resources allocated?",
];

export default function AnalyticsPage() {
  const queryClient = useQueryClient();
  const [queryText, setQueryText] = useState("");
  const [messages, setMessages] = useState<AnalyticsQueryDto[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const { data: historyData, isLoading: isLoadingHistory } = useQuery({
    queryKey: ["analytics-history"],
    queryFn: () => analyticsApi.getQueryHistory(20),
  });

  const submitQueryMutation = useMutation({
    mutationFn: (text: string) =>
      analyticsApi.submitQuery({ queryText: text }),
    onSuccess: (data) => {
      setMessages((prev) => [data, ...prev]);
      setQueryText("");
      queryClient.invalidateQueries({ queryKey: ["analytics-history"] });
    },
  });

  // Load initial history
  useEffect(() => {
    if (historyData?.data) {
      setMessages(
        historyData.data.sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )
      );
    }
  }, [historyData]);

  // Scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!queryText.trim()) return;
    submitQueryMutation.mutate(queryText);
  };

  const handleSuggestedQuery = (query: string) => {
    setQueryText(query);
    submitQueryMutation.mutate(query);
  };

  return (
    <div className="flex flex-col h-[calc(100vh-80px)] bg-gray-50">
      {/* Header */}
      <div className="border-b bg-white p-6">
        <div className="max-w-4xl mx-auto">
          <div className="flex items-center gap-3 mb-2">
            <Zap className="text-blue-600" size={28} />
            <h1 className="text-3xl font-bold">Analytics Assistant</h1>
          </div>
          <p className="text-gray-600">
            Ask natural language questions about your projects. I can help with cost,
            schedule, risk, resource, and project status queries.
          </p>
        </div>
      </div>

      {/* Chat Area */}
      <div className="flex-1 overflow-y-auto p-6">
        <div className="max-w-4xl mx-auto space-y-4">
          {messages.length === 0 && !submitQueryMutation.isPending ? (
            <div className="text-center py-12">
              <MessageCircle className="mx-auto text-gray-300 mb-4" size={48} />
              <h2 className="text-xl font-semibold text-gray-700 mb-2">
                No queries yet
              </h2>
              <p className="text-gray-500 mb-6">
                Ask me anything about your projects and I'll help with insights
              </p>

              <div className="space-y-3">
                <p className="text-sm font-semibold text-gray-600 mb-3">
                  Try one of these queries:
                </p>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                  {SUGGESTED_QUERIES.map((query) => (
                    <button
                      key={query}
                      onClick={() => handleSuggestedQuery(query)}
                      className="text-left p-3 rounded-lg border border-gray-200 hover:border-blue-400 hover:bg-blue-50 transition text-sm text-gray-700 hover:text-blue-700"
                    >
                      {query}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <>
              {messages.map((msg, idx) => (
                <div key={msg.id} className="space-y-2">
                  {/* User Query Bubble */}
                  <div className="flex justify-end">
                    <div className="max-w-2xl bg-blue-600 text-white rounded-lg p-4 rounded-br-none">
                      <p className="text-sm">{msg.queryText}</p>
                      <p className="text-xs text-blue-100 mt-1">
                        {new Date(msg.createdAt).toLocaleTimeString()}
                      </p>
                    </div>
                  </div>

                  {/* Assistant Response Bubble */}
                  <div className="flex justify-start">
                    <div className="max-w-2xl bg-white border border-gray-200 rounded-lg p-4 rounded-bl-none shadow-sm">
                      <div className="text-sm text-gray-800 whitespace-pre-wrap leading-relaxed">
                        {msg.responseText}
                      </div>
                      <div className="mt-3 flex items-center justify-between">
                        <span className="text-xs text-gray-500">
                          Type: {msg.queryType}
                        </span>
                        {msg.responseTimeMs && (
                          <span className="text-xs text-gray-400">
                            {msg.responseTimeMs}ms
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              ))}

              {submitQueryMutation.isPending && (
                <div className="space-y-2">
                  <div className="flex justify-end">
                    <div className="max-w-2xl bg-blue-600 text-white rounded-lg p-4 rounded-br-none">
                      <p className="text-sm">{queryText}</p>
                    </div>
                  </div>
                  <div className="flex justify-start">
                    <div className="max-w-2xl bg-white border border-gray-200 rounded-lg p-4 rounded-bl-none shadow-sm">
                      <div className="flex items-center gap-2">
                        <div className="flex gap-1">
                          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                          <div
                            className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                            style={{ animationDelay: "0.2s" }}
                          ></div>
                          <div
                            className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                            style={{ animationDelay: "0.4s" }}
                          ></div>
                        </div>
                        <span className="text-sm text-gray-500">Thinking...</span>
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
      <div className="border-t bg-white p-6">
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
                className="text-xs px-3 py-1 rounded-full border border-gray-300 text-gray-600 hover:border-blue-400 hover:bg-blue-50 transition"
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
