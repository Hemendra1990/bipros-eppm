"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Loader2, Search, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { ApiResponse, NodeSearchResult, PagedResponse } from "@/lib/types";
import { getErrorMessage } from "@/lib/utils/error";

const DEBOUNCE_MS = 300;
const PAGE_SIZE = 25;

type SearchFn = (
  q: string,
  page: number,
  size: number
) => Promise<ApiResponse<PagedResponse<NodeSearchResult>>>;

interface TreeSearchInputProps {
  searchFn: SearchFn;
  onSelect: (result: NodeSearchResult) => void;
  placeholder?: string;
  className?: string;
}

export function TreeSearchInput({
  searchFn,
  onSelect,
  placeholder = "Search by code or name…",
  className,
}: TreeSearchInputProps) {
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [page, setPage] = useState(0);
  const [results, setResults] = useState<NodeSearchResult[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);

  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const requestIdRef = useRef(0);

  // Debounce typing — setState happens inside the timeout callback, not synchronously in the effect body.
  useEffect(() => {
    const t = setTimeout(() => {
      const trimmed = query.trim();
      setDebounced(trimmed);
      if (trimmed) setLoading(true);
    }, DEBOUNCE_MS);
    return () => clearTimeout(t);
  }, [query]);

  // Issue search whenever (debounced, page) changes. All setState is inside promise callbacks.
  useEffect(() => {
    if (!debounced) return;
    const myRequestId = ++requestIdRef.current;
    let cancelled = false;
    searchFn(debounced, page, PAGE_SIZE)
      .then((res) => {
        if (cancelled || myRequestId !== requestIdRef.current) return;
        if (res.error) {
          setError(res.error.message);
          return;
        }
        const pageData = res.data;
        if (!pageData) {
          setResults([]);
          setTotalPages(0);
          setTotalElements(0);
          return;
        }
        setTotalPages(pageData.totalPages);
        setTotalElements(pageData.totalElements);
        setResults((prev) =>
          page === 0 ? pageData.content : [...prev, ...pageData.content]
        );
      })
      .catch((err: unknown) => {
        if (cancelled || myRequestId !== requestIdRef.current) return;
        setError(getErrorMessage(err, "Search failed"));
      })
      .finally(() => {
        if (!cancelled && myRequestId === requestIdRef.current) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [debounced, page, searchFn]);

  // Close panel when clicking outside.
  useEffect(() => {
    const handle = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handle);
    return () => document.removeEventListener("mousedown", handle);
  }, []);

  // Keep highlighted row visible — DOM side effect, not state, so no lint issue.
  useEffect(() => {
    if (open && listRef.current) {
      const item = listRef.current.children[highlight] as HTMLElement | undefined;
      item?.scrollIntoView({ block: "nearest" });
    }
  }, [highlight, open]);

  const handleQueryChange = (next: string) => {
    setQuery(next);
    // Reset transient state synchronously in the event handler — not inside an effect.
    setPage(0);
    setResults([]);
    setTotalPages(0);
    setTotalElements(0);
    setHighlight(0);
    setError(null);
    setOpen(true);
  };

  const handleClear = () => {
    setQuery("");
    setDebounced("");
    setPage(0);
    setResults([]);
    setTotalPages(0);
    setTotalElements(0);
    setHighlight(0);
    setError(null);
    setOpen(false);
  };

  const select = useCallback(
    (r: NodeSearchResult) => {
      onSelect(r);
      setOpen(false);
    },
    [onSelect]
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!open && (e.key === "ArrowDown" || e.key === "Enter")) {
      setOpen(true);
      return;
    }
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        setHighlight((p) => Math.min(p + 1, results.length - 1));
        break;
      case "ArrowUp":
        e.preventDefault();
        setHighlight((p) => Math.max(p - 1, 0));
        break;
      case "Enter":
        e.preventDefault();
        if (results[highlight]) select(results[highlight]);
        break;
      case "Escape":
        setOpen(false);
        break;
    }
  };

  const hasNext = page + 1 < totalPages;
  const showPanel = open && debounced.length > 0;

  return (
    <div ref={containerRef} className={cn("relative", className)}>
      <div className="flex items-center rounded-md border border-border bg-surface-hover focus-within:border-accent focus-within:ring-1 focus-within:ring-accent">
        <Search size={14} className="ml-3 text-text-secondary flex-shrink-0" />
        <input
          type="text"
          value={query}
          onChange={(e) => handleQueryChange(e.target.value)}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          className="w-full bg-transparent px-2 py-2 text-sm text-text-primary placeholder-text-muted focus:outline-none"
        />
        {loading && <Loader2 size={14} className="mr-2 animate-spin text-text-secondary" />}
        {query && (
          <button
            type="button"
            onClick={handleClear}
            className="mr-2 text-text-secondary hover:text-text-primary flex-shrink-0"
            aria-label="Clear search"
          >
            <X size={14} />
          </button>
        )}
      </div>

      {showPanel && (
        <div className="absolute z-50 mt-1 w-full overflow-hidden rounded-md border border-border bg-surface shadow-lg">
          {error && (
            <div className="px-3 py-2 text-sm text-danger">{error}</div>
          )}
          {!error && !loading && results.length === 0 && (
            <div className="px-3 py-2 text-sm text-text-muted">No matches</div>
          )}
          {results.length > 0 && (
            <>
              <div className="border-b border-border px-3 py-1.5 text-xs text-text-muted">
                {totalElements} match{totalElements === 1 ? "" : "es"}
              </div>
              <ul ref={listRef} className="max-h-72 overflow-auto py-1">
                {results.map((r, i) => (
                  <li
                    key={r.id}
                    onClick={() => select(r)}
                    onMouseEnter={() => setHighlight(i)}
                    className={cn(
                      "cursor-pointer px-3 py-2 text-sm",
                      i === highlight ? "bg-accent/20" : "hover:bg-surface-hover/50"
                    )}
                  >
                    <div>
                      <span className="font-medium text-accent">{r.code}</span>
                      <span className="ml-2 text-text-primary">{r.name}</span>
                    </div>
                    {r.pathLabel && (
                      <div className="mt-0.5 text-xs text-text-muted">{r.pathLabel}</div>
                    )}
                  </li>
                ))}
              </ul>
              {hasNext && (
                <button
                  type="button"
                  onClick={() => {
                    setLoading(true);
                    setError(null);
                    setPage((p) => p + 1);
                  }}
                  disabled={loading}
                  className="block w-full border-t border-border px-3 py-2 text-center text-xs font-medium text-accent hover:bg-surface-hover/50 disabled:opacity-50"
                >
                  {loading ? "Loading…" : "Load more"}
                </button>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
