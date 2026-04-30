"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { useAiStore } from "@/lib/state/store";
import { useAppStore } from "@/lib/state/store";
import { aiApi, type SseEvent } from "@/lib/api/aiApi";
import { Bot, X, Send, Loader2, PanelRightClose, PanelRightOpen, Mic, Image as ImageIcon, Square, Check } from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";

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
  pre: ({ children }) => (
    <pre className="bg-black/20 p-2 rounded-lg overflow-x-auto mb-2">{children}</pre>
  ),
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

function inferModule(pathname: string): string {
  if (pathname.includes("/cost")) return "cost";
  if (pathname.includes("/schedule")) return "schedule";
  if (pathname.includes("/risk")) return "risk";
  if (pathname.includes("/evm")) return "evm";
  if (pathname.includes("/dpr")) return "dpr";
  if (pathname.includes("/activity")) return "activity";
  return "general";
}

export function AiChatPanel() {
  const open = useAiStore((s) => s.open);
  const toggle = useAiStore((s) => s.toggle);
  const setOpen = useAiStore((s) => s.setOpen);
  const projectId = useAppStore((s) => s.currentProjectId);
  const pathname = usePathname();
  const activeModule = inferModule(pathname);

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingAssistantId, setStreamingAssistantId] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [pendingImage, setPendingImage] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isStreaming]);

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
    if ((!input.trim() && !pendingImage) || isStreaming || !projectId) return;
    const userMsg = input.trim();
    setInput("");

    const msgId = crypto.randomUUID();
    setMessages((prev) => [
      ...prev,
      { id: msgId, role: "user", content: userMsg, imageUrl: pendingImage || undefined },
    ]);
    setPendingImage(null);
    setIsStreaming(true);

    abortRef.current = new AbortController();
    const assistantId = crypto.randomUUID();
    setStreamingAssistantId(assistantId);
    setMessages((prev) => [
      ...prev,
      { id: assistantId, role: "assistant", content: "" },
    ]);

    try {
      const chatReq: import("@/lib/api/aiApi").ChatRequest = {
        projectId,
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
      setMessages((prev) =>
        prev.map((m) =>
          m.role === "tool_call" ? { ...m, meta: { ...m.meta, completed: true } } : m
        )
      );
      abortRef.current = null;
    }
  }, [input, isStreaming, projectId, activeModule, pendingImage]);

  const handleEvent = (ev: SseEvent, assistantId: string) => {
    if (ev.event === "token") {
      const delta = (ev.data.delta as string) || "";
      setMessages((prev) =>
        prev.map((m) => (m.id === assistantId ? { ...m, content: m.content + delta } : m))
      );
    } else if (ev.event === "tool_call") {
      const name = (ev.data.name as string) || "";
      setMessages((prev) => [
        ...prev,
        { id: crypto.randomUUID(), role: "tool_call", content: `Running ${name}...`, meta: ev.data },
      ]);
    } else if (ev.event === "tool_result") {
      const summary = (ev.data.summary as string) || "";
      const success = ev.data.success as boolean;
      setMessages((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          role: "tool_result",
          content: summary,
          meta: { ...ev.data, success },
        },
      ]);
    } else if (ev.event === "done") {
      const text = (ev.data.text as string) || "";
      setMessages((prev) =>
        prev.map((m) => {
          if (m.id === assistantId) return { ...m, content: text || m.content };
          if (m.role === "tool_call") return { ...m, meta: { ...m.meta, completed: true } };
          return m;
        })
      );
    } else if (ev.event === "error") {
      const message = (ev.data.message as string) || "Unknown error";
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId ? { ...m, content: m.content + "\n[Error: " + message + "]" } : m
        )
      );
    }
  };

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream, { mimeType: "audio/webm" });
      const chunks: BlobPart[] = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunks.push(e.data);
      };
      recorder.onstop = async () => {
        const blob = new Blob(chunks, { type: "audio/webm" });
        try {
          const text = await aiApi.speechToText(blob);
          setInput((prev) => prev + (prev ? " " : "") + text);
        } catch (err) {
          console.error("STT failed", err);
        }
        setIsRecording(false);
        stream.getTracks().forEach((t) => t.stop());
      };
      mediaRecorderRef.current = recorder;
      recorder.start();
      setIsRecording(true);
    } catch (err) {
      console.error("Microphone access denied", err);
    }
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

  return (
    <div className="fixed inset-y-0 right-0 z-50 flex">
      <div className="absolute inset-0 bg-black/20 sm:hidden" onClick={() => setOpen(false)} />
      <div
        className={`relative flex flex-col bg-surface border-l border-border shadow-xl transition-all duration-200 ${
          collapsed ? "w-16" : "w-full sm:w-[420px]"
        }`}
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
                  <p className="text-xs mt-2 text-text-muted/70">
                    Use the microphone for voice input or attach images for analysis.
                  </p>
                </div>
              )}
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex ${
                    msg.role === "user" ? "justify-end" : "justify-start"
                  }`}
                >
                  <div
                    className={`max-w-[90%] rounded-xl px-3 py-2 text-sm ${
                      msg.role === "user"
                        ? "bg-accent text-text-primary"
                        : msg.role === "tool_call"
                        ? "bg-info/10 text-info border border-info/20"
                        : msg.role === "tool_result"
                        ? "bg-success/10 text-success border border-success/20"
                        : "bg-surface-hover text-text-primary border border-border"
                    }`}
                  >
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
                        <div className="font-medium mb-1">
                          {msg.meta?.success ? "Result" : "Failed"}
                        </div>
                        <div className="whitespace-pre-wrap">{msg.content}</div>
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
                            <div className="whitespace-pre-wrap">{msg.content}</div>
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
              {isStreaming && (
                <div className="flex justify-start">
                  <div className="bg-surface-hover text-text-primary border border-border rounded-xl px-3 py-2 text-sm">
                    <span className="inline-block w-1.5 h-1.5 bg-text-muted rounded-full animate-pulse" />
                  </div>
                </div>
              )}
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
                  disabled={!projectId || isStreaming}
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
                  placeholder={projectId ? "Ask anything..." : "Select a project first"}
                  disabled={!projectId || isStreaming}
                  rows={1}
                  className="flex-1 resize-none rounded-lg border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent min-h-[40px] max-h-[120px]"
                />
                <button
                  onClick={sendMessage}
                  disabled={(!input.trim() && !pendingImage) || !projectId || isStreaming}
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
