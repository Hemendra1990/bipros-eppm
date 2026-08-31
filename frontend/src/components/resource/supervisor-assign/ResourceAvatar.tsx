"use client";

import { useMemo } from "react";

const PALETTE = [
  { bg: "#E8DCC4", fg: "#5A4A1F" }, // sand
  { bg: "#D9E2D6", fg: "#2F4A2C" }, // sage
  { bg: "#DDE2EC", fg: "#2C3E5A" }, // slate-blue
  { bg: "#EAD9D6", fg: "#5A2F2C" }, // dusty-rose
  { bg: "#E5DCE8", fg: "#4A2C5A" }, // muted-violet
  { bg: "#D6E5E2", fg: "#1F4A4A" }, // teal
  { bg: "#E8E0D2", fg: "#5A4A1F" }, // cream
  { bg: "#DCD9E8", fg: "#2F2C5A" }, // periwinkle
];

function hashId(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i++) {
    h = (h * 31 + id.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

function initialsOf(name: string): string {
  const parts = name
    .replace(/[^\p{L}\p{N}\s.]/gu, " ")
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  const first = parts[0][0] ?? "";
  const last = parts[parts.length - 1][0] ?? "";
  return (first + last).toUpperCase();
}

interface Props {
  id: string;
  name: string;
  size?: "sm" | "md";
  className?: string;
}

export function ResourceAvatar({ id, name, size = "md", className }: Props) {
  const { bg, fg, initials } = useMemo(() => {
    const idx = hashId(id) % PALETTE.length;
    return {
      bg: PALETTE[idx].bg,
      fg: PALETTE[idx].fg,
      initials: initialsOf(name),
    };
  }, [id, name]);

  const dim = size === "sm" ? "h-5 w-5 text-[10px]" : "h-7 w-7 text-[11px]";

  return (
    <span
      className={`inline-flex shrink-0 items-center justify-center rounded-full font-semibold tracking-tight ${dim} ${className ?? ""}`}
      style={{ backgroundColor: bg, color: fg }}
      aria-hidden
    >
      {initials}
    </span>
  );
}
