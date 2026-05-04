"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { useAiStore } from "@/lib/state/store";
import { useAppStore } from "@/lib/state/store";
import { aiApi, type SseEvent } from "@/lib/api/aiApi";
import { Bot, X, Send, Loader2, PanelRightClose, PanelRightOpen, Mic, Image as ImageIcon, Square, Check, Copy, Maximize2, Minimize2 } from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";
import { Children, isValidElement, type ReactElement } from "react";
import { ChatChart } from "@/components/ai/charts/chatChart";

interface ChatMessage {
  id: string;
  role: "user" | "assistant" | "tool_call" | "tool_result";
  content: string;
  imageUrl?: string;
  meta?: Record<string, unknown>;
}

const markdownComponents: Components = {
  p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
  ul: ({ children }) => <ul className="list-disc pl-4 mb-2">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal pl-4 mb-2">{children}</ol>,
  li: ({ children }) => <li className="mb-1">{children}</li>,
  strong: ({ children }) => <strong className="font-bold">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  code: ({ children, className }) => (
    <code className={`bg-black/20 px-1 py-0.5 rounded text-sm font-mono ${className || ""}`}>
      {children}
    </code>
  ),
  pre: ({ children }) => {
    // Detect ```chart fenced blocks and swap in an inline ECharts viz.
    // react-markdown nests <code class="language-chart"> inside <pre>.
    const only = Children.toArray(children).find(isValidElement) as
      | ReactElement<{ className?: string; children?: unknown }>
      | undefined;
    if (only?.props?.className === "language-chart") {
      const raw = String(only.props.children ?? "").trim();
      return <ChatChart raw={raw} />;
    }
    return <pre className="bg-black/20 p-2 rounded-lg overflow-x-auto mb-2">{children}</pre>;
  },
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer" className="text-accent underline">
      {children}
    </a>
  ),
  h1: ({ children }) => <h1 className="text-lg font-bold mb-2">{children}</h1>,
  h2: ({ children }) => <h2 className="text-base font-bold mb-2">{children}</h2>,
  h3: ({ children }) => <h3 className="text-sm font-bold mb-1">{children}</h3>,
  blockquote: ({ children }) => (
    <blockquote className="border-l-2 border-border pl-3 italic text-text-muted mb-2">
      {children}
    </blockquote>
  ),
};

// Friendly progress labels — keep tool-name plumbing out of the UI while still
// telling the user what's underway. Unknown tools fall back to "Working".
const TOOL_PROGRESS_LABELS: Record<string, string> = {
  list_projects: "Looking up projects",
  list_activities: "Checking activities",
  list_activity_resources: "Checking activity resources",
  find_resource_deployment: "Checking resource deployment",
  summarize_activity_resources: "Rolling up resource costs by type",
  analyze_schedule: "Reading schedule health",
  analyze_cost: "Reading cost performance",
  analyze_risk: "Reading risk register",
  forecast_completion: "Running forecast",
  portfolio_kpi: "Reading portfolio KPIs",
  read_dpr_summary: "Reading daily progress",
  query_clickhouse: "Querying analytics",
  describe_schema: "Inspecting data shape",
};
function friendlyToolLabel(name: string): string {
  return TOOL_PROGRESS_LABELS[name] ?? "Working";
}

function inferModule(pathname: string): string {
  if (pathname.includes("/cost")) return "cost";
  if (pathname.includes("/schedule")) return "schedule";
  if (pathname.includes("/risk")) return "risk";
  if (pathname.includes("/evm")) return "evm";
  if (pathname.includes("/dpr")) return "dpr";
  if (pathname.includes("/activity")) return "activity";
  return "general";
}

// Pull a project UUID out of /projects/<uuid>/... so the AI panel can scope to
// the project the user is currently viewing, even on pages that haven't called
// setCurrentProjectId(). Fallback only — useAppStore wins when set.
const PROJECT_PATH_RE = /\/projects\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:\/|$)/i;
function inferProjectIdFromPath(pathname: string): string | null {
  const m = pathname.match(PROJECT_PATH_RE);
  return m ? m[1] : null;
}

function ToolDataExpander({ data }: { data: unknown }) {
  const [expanded, setExpanded] = useState(false);
  const json = typeof data === "string" ? data : JSON.stringify(data, null, 2);
  const isLong = json.length > 200;
  return (
    <div className="mt-2">
      <button
        onClick={() => setExpanded(!expanded)}
        className="text-accent hover:underline text-xs font-medium"
      >
        {expanded ? "Hide data" : "View data"}
      </button>
      {expanded && (
        <pre className="mt-1 bg-black/20 p-2 rounded-lg overflow-x-auto max-h-[300px] overflow-y-auto text-xs">
          {json}
        </pre>
      )}
      {!expanded && isLong && (
        <div className="mt-1 text-text-muted text-xs">{json.substring(0, 200)}...</div>
      )}
    </div>
  );
}

function CopyButton({ text, dark }: { text: string; dark?: boolean }) {
  const [copied, setCopied] = useState(false);
  const onClick = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Some older browsers / insecure contexts deny clipboard writes — fall
      // back to a hidden textarea + execCommand so the action still succeeds.
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.setAttribute("readonly", "");
      ta.style.position = "absolute";
      ta.style.left = "-9999px";
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand("copy"); setCopied(true); setTimeout(() => setCopied(false), 1500); } catch {}
      document.body.removeChild(ta);
    }
  };
  return (
    <button
      type="button"
      onClick={onClick}
      title={copied ? "Copied" : "Copy"}
      aria-label={copied ? "Copied" : "Copy message"}
      className={`absolute -bottom-2 ${dark ? "right-2 text-text-primary/70 hover:text-text-primary" : "left-2 text-text-secondary hover:text-text-primary"} bg-surface border border-border rounded-md p-1 opacity-0 group-hover:opacity-100 transition-opacity shadow-sm`}
    >
      {copied ? <Check size={12} /> : <Copy size={12} />}
    </button>
  );
}

export function AiChatPanel() {
  const open = useAiStore((s) => s.open);
  const toggle = useAiStore((s) => s.toggle);
  const setOpen = useAiStore((s) => s.setOpen);
  const conversationId = useAiStore((s) => s.currentConversationId);
  const setConversationId = useAiStore((s) => s.setConversationId);
  const storeProjectId = useAppStore((s) => s.currentProjectId);
  const pathname = usePathname();
  const activeModule = inferModule(pathname);
  // Prefer the store's currentProjectId; fall back to the URL when the user
  // landed on a project page directly (most pages don't set the store).
  const projectId = storeProjectId ?? inferProjectIdFromPath(pathname);

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingAssistantId, setStreamingAssistantId] = useState<string | null>(null);
  const [streamingStatus, setStreamingStatus] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [maximized, setMaximized] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [micError, setMicError] = useState<string | null>(null);
  const [pendingImage, setPendingImage] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const lastScopeRef = useRef<string | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isStreaming]);

  // Reset the chat when the user moves to a different scope (project or
  // module). The persisted conversationId belongs to the previous scope —
  // continuing it would stitch unrelated turns together. Skip the very first
  // run so a page reload keeps the existing conversation intact.
  useEffect(() => {
    const scopeKey = `${projectId ?? "general"}|${activeModule}`;
    if (lastScopeRef.current === null) {
      lastScopeRef.current = scopeKey;
      return;
    }
    if (lastScopeRef.current !== scopeKey) {
      lastScopeRef.current = scopeKey;
      setConversationId(null);
      setMessages([]);
    }
  }, [projectId, activeModule, setConversationId]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        toggle();
      }
      if (e.key === "Escape" && open) {
        setOpen(false);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, toggle, setOpen]);

  const sendMessage = useCallback(async () => {
    if ((!input.trim() && !pendingImage) || isStreaming) return;
    const userMsg = input.trim();
    setInput("");

    const msgId = crypto.randomUUID();
    setMessages((prev) => [
      ...prev,
      { id: msgId, role: "user", content: userMsg, imageUrl: pendingImage || undefined },
    ]);
    setPendingImage(null);
    setIsStreaming(true);
    setStreamingStatus(null);

    abortRef.current = new AbortController();
    const assistantId = crypto.randomUUID();
    setStreamingAssistantId(assistantId);
    setMessages((prev) => [
      ...prev,
      { id: assistantId, role: "assistant", content: "" },
    ]);

    try {
      const chatReq: import("@/lib/api/aiApi").ChatRequest = {
        conversationId: conversationId ?? null,
        projectId: projectId ?? null,
        module: activeModule,
        message: userMsg,
        imageUrl: pendingImage,
      };
      for await (const ev of aiApi.streamChat(
        chatReq,
        abortRef.current.signal
      )) {
        handleEvent(ev, assistantId);
      }
    } catch (err) {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId
            ? { ...m, content: m.content + "\n[Error: " + (err instanceof Error ? err.message : String(err)) + "]" }
            : m
        )
      );
    } finally {
      setIsStreaming(false);
      setStreamingAssistantId(null);
      setStreamingStatus(null);
      setMessages((prev) =>
        prev.map((m) =>
          m.role === "tool_call" ? { ...m, meta: { ...m.meta, completed: true } } : m
        )
      );
      abortRef.current = null;
    }
  }, [input, isStreaming, projectId, activeModule, pendingImage, conversationId]);

  const handleEvent = (ev: SseEvent, assistantId: string) => {
    if (ev.event === "conversation_started") {
      const id = ev.data.conversationId as string | undefined;
      if (id && id !== conversationId) setConversationId(id);
      return;
    }
    if (ev.event === "token") {
      const delta = (ev.data.delta as string) || "";
      setMessages((prev) =>
        prev.map((m) => (m.id === assistantId ? { ...m, content: m.content + delta } : m))
      );
    } else if (ev.event === "tool_call") {
      // Show a friendly progress line inside the assistant bubble so the user
      // sees something happening during multi-round agentic work. We map the
      // tool name to a business label — never leak the raw internal name.
      const name = (ev.data.name as string) || "";
      setStreamingStatus(friendlyToolLabel(name));
    } else if (ev.event === "tool_result") {
      // Keep the last tool_call's label visible until the next tool_call or
      // the final answer arrives — gives a steady "still working" signal
      // without flickering between rounds.
    } else if (ev.event === "done" || ev.event === "final_answer") {
      const text = (ev.data.text as string) || "";
      setStreamingStatus(null);
      setMessages((prev) =>
        prev.map((m) => {
          if (m.id === assistantId) return { ...m, content: text || m.content };
          if (m.role === "tool_call") return { ...m, meta: { ...m.meta, completed: true } };
          return m;
        })
      );
    } else if (ev.event === "max_rounds_exceeded") {
      const rounds = (ev.data.rounds as number) ?? 0;
      setStreamingStatus(null);
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId
            ? {
                ...m,
                content:
                  m.content +
                  `\n\n_(Agent stopped after ${rounds} steps — refine your question or pick a specific project to drill into.)_`,
              }
            : m
        )
      );
    } else if (ev.event === "error") {
      const message = (ev.data.message as string) || "Unknown error";
      setStreamingStatus(null);
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId ? { ...m, content: m.content + "\n[Error: " + message + "]" } : m
        )
      );
    }
  };

  const startRecording = async () => {
    setMicError(null);

    if (typeof window === "undefined" || !navigator.mediaDevices?.getUserMedia) {
      setMicError(
        window.isSecureContext
          ? "Microphone API is not available in this browser."
          : "Microphone requires a secure context (HTTPS or localhost)."
      );
      return;
    }

    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (err) {
      const name = (err as DOMException)?.name;
      if (name === "NotAllowedError" || name === "SecurityError") {
        setMicError(
          "Microphone permission denied. Click the lock icon in the address bar to allow it, then try again."
        );
      } else if (name === "NotFoundError" || name === "OverconstrainedError") {
        setMicError("No microphone device was found.");
      } else if (name === "NotReadableError") {
        setMicError("Microphone is in use by another application.");
      } else {
        setMicError("Could not start the microphone. Check browser settings.");
      }
      console.error("Microphone access failed", err);
      return;
    }

    const mimeType = MediaRecorder.isTypeSupported("audio/webm")
      ? "audio/webm"
      : MediaRecorder.isTypeSupported("audio/mp4")
      ? "audio/mp4"
      : "";
    let recorder: MediaRecorder;
    try {
      recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
    } catch (err) {
      console.error("MediaRecorder init failed", err);
      setMicError("Audio recording is not supported in this browser.");
      stream.getTracks().forEach((t) => t.stop());
      return;
    }

    const chunks: BlobPart[] = [];
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data);
    };
    recorder.onstop = async () => {
      const blob = new Blob(chunks, { type: recorder.mimeType || "audio/webm" });
      try {
        const text = await aiApi.speechToText(blob);
        setInput((prev) => prev + (prev ? " " : "") + text);
      } catch (err) {
        console.error("STT failed", err);
        setMicError("Speech-to-text failed. Please try again.");
      }
      setIsRecording(false);
      stream.getTracks().forEach((t) => t.stop());
    };
    mediaRecorderRef.current = recorder;
    recorder.start();
    setIsRecording(true);
  };

  const stopRecording = () => {
    mediaRecorderRef.current?.stop();
  };

  const handleImageSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !projectId) return;
    try {
      const convId = "temp-conv"; // Will be replaced with actual conversation ID
      const result = await aiApi.uploadImage(convId, file);
      setPendingImage(`${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"}${result.url}`);
    } catch (err) {
      console.error("Image upload failed", err);
    }
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  if (!open) {
    return (
      <button
        onClick={toggle}
        className="fixed bottom-6 right-6 z-50 flex items-center gap-2 rounded-full bg-accent px-4 py-3 text-sm font-medium text-text-primary shadow-lg hover:bg-accent-hover transition-colors"
        title="Open AI chat (Ctrl+K)"
      >
        <Bot size={18} />
        <span className="hidden sm:inline">Ask AI</span>
      </button>
    );
  }

  const widthClass = collapsed
    ? "w-16"
    : maximized
      ? "w-screen sm:w-[min(96vw,1200px)]"
      : "w-full sm:w-[420px]";

  return (
    <div className="fixed inset-y-0 right-0 z-50 flex">
      <div className="absolute inset-0 bg-black/20 sm:hidden" onClick={() => setOpen(false)} />
      <div
        className={`relative flex flex-col bg-surface border-l border-border shadow-xl transition-all duration-200 ${widthClass}`}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          {!collapsed && (
            <div className="flex items-center gap-2">
              <Bot size={18} className="text-accent" />
              <span className="text-sm font-semibold text-text-primary">Bipros AI</span>
              {projectId && (
                <span className="text-xs text-text-muted bg-surface-hover px-2 py-0.5 rounded">
                  {activeModule}
                </span>
              )}
            </div>
          )}
          <div className="flex items-center gap-1 ml-auto">
            {!collapsed && (
              <button
                onClick={() => setMaximized((m) => !m)}
                className="p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-hover"
                title={maximized ? "Restore size" : "Maximize"}
                aria-label={maximized ? "Restore panel size" : "Maximize panel"}
              >
                {maximized ? <Minimize2 size={16} /> : <Maximize2 size={16} />}
              </button>
            )}
            <button
              onClick={() => setCollapsed(!collapsed)}
              className="p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-hover"
              title={collapsed ? "Expand" : "Collapse"}
            >
              {collapsed ? <PanelRightOpen size={16} /> : <PanelRightClose size={16} />}
            </button>
            {!collapsed && (
              <button
                onClick={() => setOpen(false)}
                className="p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-hover"
                title="Close (Esc)"
              >
                <X size={16} />
              </button>
            )}
          </div>
        </div>

        {!collapsed && (
          <>
            {/* Messages */}
            <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
              {messages.length === 0 && (
                <div className="text-center text-text-muted py-12">
                  <Bot size={32} className="mx-auto mb-3 opacity-50" />
                  <p className="text-sm">Ask me about cost variance, schedule health, DPR summaries, or EVM forecasts.</p>
                  {!projectId && (
                    <p className="text-xs mt-3 text-text-muted/80">
                      No project selected — try portfolio questions like
                      <span className="block italic mt-1">
                        &ldquo;Which projects have the worst CPI this month?&rdquo;
                      </span>
                    </p>
                  )}
                  <p className="text-xs mt-2 text-text-muted/70">
                    Use the microphone for voice input or attach images for analysis.
                  </p>
                </div>
              )}
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`group relative flex ${
                    msg.role === "user" ? "justify-end" : "justify-start"
                  }`}
                >
                  <div
                    className={`relative max-w-[90%] rounded-xl px-3 py-2 text-sm ${
                      msg.role === "user"
                        ? "bg-accent text-text-primary"
                        : msg.role === "tool_call"
                        ? "bg-info/10 text-info border border-info/20"
                        : msg.role === "tool_result"
                        ? "bg-success/10 text-success border border-success/20"
                        : "bg-surface-hover text-text-primary border border-border"
                    }`}
                  >
                    {(msg.role === "user" || msg.role === "assistant") && msg.content && (
                      <CopyButton text={msg.content} dark={msg.role === "user"} />
                    )}
                    {msg.role === "tool_call" && (
                      <div className="flex items-center gap-2 text-xs font-medium">
                        {msg.meta?.completed ? (
                          <Check size={12} className="text-success" />
                        ) : (
                          <Loader2 size={12} className="animate-spin" />
                        )}
                        {msg.content}
                      </div>
                    )}
                    {msg.role === "tool_result" && (
                      <div className="text-xs">
                        <div className={`font-medium mb-1 ${msg.meta?.success === false ? "text-danger" : ""}`}>
                          {msg.meta?.success === false ? "Failed" : "Result"}
                        </div>
                        <div className="whitespace-pre-wrap">{msg.content}</div>
                        {msg.meta?.data != null && (
                          <ToolDataExpander data={msg.meta.data} />
                        )}
                      </div>
                    )}
                    {(msg.role === "user" || msg.role === "assistant") && (
                      <div>
                        {msg.imageUrl && (
                          <img
                            src={msg.imageUrl}
                            alt="Uploaded"
                            className="max-w-full max-h-[200px] rounded-lg mb-2 object-cover"
                          />
                        )}
                        {msg.role === "assistant" ? (
                          msg.id === streamingAssistantId ? (
                            <div>
                              {msg.content && (
                                <div className="whitespace-pre-wrap">{msg.content}</div>
                              )}
                              <div
                                className={`flex items-center gap-2 text-xs text-text-muted ${
                                  msg.content ? "mt-2" : ""
                                }`}
                              >
                                <Loader2 size={11} className="animate-spin" />
                                <span>{streamingStatus ?? "Thinking"}…</span>
                              </div>
                            </div>
                          ) : (
                            <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
                              {msg.content}
                            </ReactMarkdown>
                          )
                        ) : (
                          <div className="whitespace-pre-wrap">{msg.content}</div>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ))}
              <div ref={bottomRef} />
            </div>

            {/* Pending image preview */}
            {pendingImage && (
              <div className="px-4 py-2 border-t border-border">
                <div className="relative inline-block">
                  <img
                    src={pendingImage}
                    alt="Pending"
                    className="h-16 w-16 rounded-lg object-cover border border-border"
                  />
                  <button
                    onClick={() => setPendingImage(null)}
                    className="absolute -top-1 -right-1 bg-danger text-white rounded-full p-0.5"
                  >
                    <X size={10} />
                  </button>
                </div>
              </div>
            )}

            {/* Mic error */}
            {micError && (
              <div className="px-4 pt-2">
                <div className="flex items-start gap-2 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">
                  <span className="flex-1">{micError}</span>
                  <button
                    onClick={() => setMicError(null)}
                    className="text-danger/70 hover:text-danger"
                    title="Dismiss"
                  >
                    <X size={12} />
                  </button>
                </div>
              </div>
            )}

            {/* Input */}
            <div className="border-t border-border px-4 py-3">
              <div className="flex items-end gap-2">
                <button
                  onClick={() => fileInputRef.current?.click()}
                  disabled={!projectId || isStreaming}
                  className="p-2 rounded-lg text-text-secondary hover:text-text-primary hover:bg-surface-hover transition-colors"
                  title="Attach image"
                >
                  <ImageIcon size={16} />
                </button>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={handleImageSelect}
                />
                <button
                  onClick={isRecording ? stopRecording : startRecording}
                  disabled={isStreaming}
                  className={`p-2 rounded-lg transition-colors ${
                    isRecording
                      ? "bg-danger text-white animate-pulse"
                      : "text-text-secondary hover:text-text-primary hover:bg-surface-hover"
                  }`}
                  title={isRecording ? "Stop recording" : "Voice input"}
                >
                  {isRecording ? <Square size={16} /> : <Mic size={16} />}
                </button>
                <textarea
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      sendMessage();
                    }
                  }}
                  placeholder={projectId ? "Ask anything..." : "Ask anything (portfolio mode)..."}
                  disabled={isStreaming}
                  rows={1}
                  className="flex-1 resize-none rounded-lg border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent min-h-[40px] max-h-[120px]"
                />
                <button
                  onClick={sendMessage}
                  disabled={(!input.trim() && !pendingImage) || isStreaming}
                  className="rounded-lg bg-accent p-2 text-text-primary hover:bg-accent-hover disabled:bg-border disabled:text-text-muted transition-colors"
                >
                  {isStreaming ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
