"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Trash2, MessageSquare, Search } from "lucide-react";
import { aiApi, type ConversationSummary } from "@/lib/api/aiApi";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";

interface AiHistoryViewProps {
  currentConversationId: string | null;
  onSelect: (id: string) => void;
}

function relativeTime(iso: string): string {
  if (!iso) return "";
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return "";
  const diff = Date.now() - t;
  const sec = Math.round(diff / 1000);
  if (sec < 60) return "just now";
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const day = Math.round(hr / 24);
  if (day < 7) return `${day}d ago`;
  return new Date(iso).toLocaleDateString();
}

export function AiHistoryView({ currentConversationId, onSelect }: AiHistoryViewProps) {
  const [items, setItems] = useState<ConversationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [pendingDelete, setPendingDelete] = useState<ConversationSummary | null>(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await aiApi.listConversations({ limit: 50 });
      setItems(res.data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load history");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (c) =>
        (c.title || "").toLowerCase().includes(q) ||
        (c.module || "").toLowerCase().includes(q),
    );
  }, [items, query]);

  const handleDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await aiApi.deleteConversation(pendingDelete.id);
      setItems((prev) => prev.filter((c) => c.id !== pendingDelete.id));
      setPendingDelete(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to delete");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-border px-4 py-3">
        <div className="relative">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search conversations…"
            className="w-full rounded-md border border-border bg-surface pl-9 pr-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-1 focus:ring-accent"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {loading && (
          <div className="flex items-center justify-center py-12 text-text-muted">
            <Loader2 size={20} className="animate-spin" />
          </div>
        )}

        {!loading && error && (
          <div className="px-4 py-8 text-center text-sm text-danger">
            {error}
            <div>
              <button onClick={load} className="mt-3 text-xs text-accent underline">
                Retry
              </button>
            </div>
          </div>
        )}

        {!loading && !error && filtered.length === 0 && (
          <div className="flex flex-col items-center gap-2 px-4 py-12 text-center text-text-muted">
            <MessageSquare size={28} className="opacity-40" />
            <div className="text-sm">
              {items.length === 0 ? "No past conversations yet." : "No matches."}
            </div>
          </div>
        )}

        {!loading && !error && filtered.length > 0 && (
          <ul className="divide-y divide-border">
            {filtered.map((c) => {
              const active = c.id === currentConversationId;
              return (
                <li key={c.id}>
                  <div
                    className={`group flex items-start gap-3 px-4 py-3 transition-colors ${
                      active ? "bg-surface-hover" : "hover:bg-surface-hover"
                    }`}
                  >
                    <button
                      type="button"
                      onClick={() => onSelect(c.id)}
                      className="flex flex-1 flex-col items-start text-left"
                    >
                      <div className="line-clamp-2 text-sm font-medium text-text-primary">
                        {c.title || "Chat"}
                      </div>
                      <div className="mt-1 flex items-center gap-2 text-xs text-text-muted">
                        <span className="rounded bg-surface px-1.5 py-0.5 text-[10px] uppercase tracking-wide">
                          {c.module || "general"}
                        </span>
                        <span>{relativeTime(c.lastMessageAt)}</span>
                      </div>
                    </button>
                    <button
                      type="button"
                      onClick={() => setPendingDelete(c)}
                      title="Delete conversation"
                      aria-label="Delete conversation"
                      className="opacity-0 group-hover:opacity-100 transition-opacity rounded-md p-1.5 text-text-muted hover:bg-danger/10 hover:text-danger"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete conversation?"
        message={
          pendingDelete
            ? `"${pendingDelete.title || "Chat"}" and all its messages will be removed from your history.`
            : ""
        }
        confirmLabel={deleting ? "Deleting…" : "Delete"}
        variant="danger"
        onConfirm={handleDelete}
        onCancel={() => (deleting ? null : setPendingDelete(null))}
      />
    </div>
  );
}
