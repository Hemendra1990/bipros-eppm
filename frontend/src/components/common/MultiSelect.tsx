"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import { ChevronDown, Search, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface MultiSelectOption {
  value: string;
  label: string;
}

interface MultiSelectProps {
  options: MultiSelectOption[];
  value: string[];
  onChange: (value: string[]) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
}

/**
 * Multi-select chooser with chips. Style mirrors {@link SearchableSelect}: search-driven
 * dropdown of unselected options, keyboard nav (arrows / Enter / Backspace / Escape).
 * Selected items render as removable chips above the input.
 *
 * Used for Primary Skills + Secondary Skills on the Manpower resource form (replaces the
 * legacy JSON textarea pattern).
 */
export function MultiSelect({
  options,
  value,
  onChange,
  placeholder = "Search...",
  disabled = false,
  className,
}: MultiSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const selected = value
    .map((v) => options.find((o) => o.value === v) ?? { value: v, label: v })
    .filter(Boolean) as MultiSelectOption[];

  const unselected = options.filter((o) => !value.includes(o.value));
  const filtered = search
    ? unselected.filter((o) => o.label.toLowerCase().includes(search.toLowerCase()))
    : unselected;

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
        setSearch("");
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    if (isOpen && listRef.current) {
      const item = listRef.current.children[highlightedIndex] as HTMLElement;
      item?.scrollIntoView({ block: "nearest" });
    }
  }, [highlightedIndex, isOpen]);

  const handleAdd = useCallback(
    (val: string) => {
      if (!value.includes(val)) onChange([...value, val]);
      setSearch("");
      setHighlightedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 0);
    },
    [onChange, value],
  );

  const handleRemove = (val: string) => {
    onChange(value.filter((v) => v !== val));
    setHighlightedIndex(0);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!isOpen && (e.key === "ArrowDown" || e.key === "Enter")) {
      e.preventDefault();
      setIsOpen(true);
      return;
    }
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        setHighlightedIndex((prev) => Math.min(prev + 1, filtered.length - 1));
        break;
      case "ArrowUp":
        e.preventDefault();
        setHighlightedIndex((prev) => Math.max(prev - 1, 0));
        break;
      case "Enter":
        e.preventDefault();
        if (filtered[highlightedIndex]) handleAdd(filtered[highlightedIndex].value);
        break;
      case "Backspace":
        if (search === "" && value.length > 0) {
          handleRemove(value[value.length - 1]);
        }
        break;
      case "Escape":
        setIsOpen(false);
        setSearch("");
        break;
    }
  };

  const handleOpen = () => {
    if (disabled) return;
    setIsOpen(true);
    setTimeout(() => inputRef.current?.focus(), 0);
  };

  return (
    <div ref={containerRef} className={cn("relative", className)}>
      <div
        onClick={handleOpen}
        className={cn(
          "flex min-h-[40px] w-full flex-wrap items-center gap-1.5 rounded-md border border-border bg-surface-hover px-2 py-1.5 text-sm",
          isOpen
            ? "border-accent ring-1 ring-blue-500"
            : "focus-within:border-accent focus-within:ring-1 focus-within:ring-accent",
          disabled ? "opacity-50 cursor-not-allowed" : "cursor-text",
        )}
      >
        {selected.map((s) => (
          <span
            key={s.value}
            className="inline-flex items-center gap-1 rounded-md bg-accent/15 px-2 py-0.5 text-xs text-text-primary"
          >
            {s.label}
            {!disabled && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleRemove(s.value);
                }}
                className="text-text-secondary hover:text-text-primary"
                aria-label={`Remove ${s.label}`}
              >
                <X size={11} />
              </button>
            )}
          </span>
        ))}

        {isOpen ? (
          <div className="flex flex-1 items-center gap-1 min-w-[120px]">
            <Search size={13} className="text-text-secondary flex-shrink-0" />
            <input
              ref={inputRef}
              type="text"
              value={search}
              disabled={disabled}
              onChange={(e) => {
                setSearch(e.target.value);
                setHighlightedIndex(0);
              }}
              onKeyDown={handleKeyDown}
              placeholder={selected.length === 0 ? placeholder : "Add another…"}
              className="flex-1 bg-transparent px-1 py-0.5 text-sm text-text-primary placeholder-text-muted focus:outline-none"
            />
            {(search || selected.length > 0) && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  if (search) setSearch("");
                  else onChange([]);
                }}
                className="text-text-secondary hover:text-text-primary flex-shrink-0"
                aria-label="Clear"
              >
                <X size={13} />
              </button>
            )}
          </div>
        ) : (
          <span className="ml-auto inline-flex items-center text-text-secondary">
            {selected.length === 0 && (
              <span className="mr-2 text-sm text-text-muted">{placeholder}</span>
            )}
            <ChevronDown size={15} />
          </span>
        )}
      </div>

      {isOpen && (
        <ul
          ref={listRef}
          className="absolute z-50 mt-1 max-h-60 w-full overflow-auto rounded-md border border-border bg-surface-hover py-1 shadow-lg"
        >
          {filtered.length === 0 ? (
            <li className="px-3 py-2 text-sm text-text-muted">
              {unselected.length === 0 ? "All options selected" : "No matches found"}
            </li>
          ) : (
            filtered.map((option, index) => (
              <li
                key={option.value}
                onClick={() => handleAdd(option.value)}
                onMouseEnter={() => setHighlightedIndex(index)}
                className={cn(
                  "cursor-pointer px-3 py-2 text-sm transition-colors",
                  index === highlightedIndex
                    ? "bg-accent text-text-primary"
                    : "text-text-secondary hover:bg-surface-active",
                )}
              >
                {option.label}
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
