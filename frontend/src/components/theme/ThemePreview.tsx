"use client";

import type { PaletteValues } from "@/lib/themes/definitions";

interface ThemePreviewProps {
  palette: PaletteValues;
  mode: "light" | "dark";
  logoSrc?: string;
  appNamePrimary?: string;
  appNameSecondary?: string;
}

export function ThemePreview({ palette, mode, logoSrc, appNamePrimary, appNameSecondary }: ThemePreviewProps) {
  const isDark = mode === "dark";

  return (
    <div
      className="rounded-xl border p-4 space-y-4"
      style={{
        background: palette.background,
        borderColor: palette.border,
      }}
    >
      {/* Buttons */}
      <div className="flex flex-wrap gap-2">
        <button
          className="px-3 py-1.5 rounded-md text-sm font-medium"
          style={{ background: palette.accent, color: isDark ? "#0a0a0a" : "#ffffff" }}
        >
          Primary
        </button>
        <button
          className="px-3 py-1.5 rounded-md text-sm font-medium"
          style={{ background: palette.backgroundMuted, color: palette.foreground }}
        >
          Secondary
        </button>
        <button
          className="px-3 py-1.5 rounded-md text-sm font-medium border"
          style={{ background: "transparent", color: palette.foregroundSecondary, borderColor: palette.border }}
        >
          Ghost
        </button>
        <button
          className="px-3 py-1.5 rounded-md text-sm font-medium"
          style={{ background: palette.danger, color: "#ffffff" }}
        >
          Danger
        </button>
      </div>

      {/* Card */}
      <div
        className="rounded-lg border p-3 space-y-2"
        style={{ background: palette.backgroundSubtle, borderColor: palette.borderSubtle }}
      >
        <div className="flex items-center justify-between">
          <span className="text-sm font-semibold" style={{ color: palette.foreground }}>
            Sample Card
          </span>
          <span
            className="text-xs px-2 py-0.5 rounded-full font-medium"
            style={{ background: palette.accentTint, color: palette.accentSubtle }}
          >
            Badge
          </span>
        </div>
        <p className="text-xs" style={{ color: palette.foregroundSecondary }}>
          This is how body text looks inside a card component.
        </p>
      </div>

      {/* Input */}
      <input
        type="text"
        readOnly
        value="Input field"
        className="w-full rounded-md border px-3 py-2 text-sm"
        style={{
          background: palette.background,
          borderColor: palette.borderSubtle,
          color: palette.foreground,
        }}
      />

      {/* Status badges */}
      <div className="flex flex-wrap gap-2">
        <span
          className="text-xs px-2 py-0.5 rounded-md font-medium"
          style={{ background: `${palette.success}20`, color: palette.success }}
        >
          Success
        </span>
        <span
          className="text-xs px-2 py-0.5 rounded-md font-medium"
          style={{ background: `${palette.warning}20`, color: palette.warning }}
        >
          Warning
        </span>
        <span
          className="text-xs px-2 py-0.5 rounded-md font-medium"
          style={{ background: `${palette.danger}20`, color: palette.danger }}
        >
          Danger
        </span>
        <span
          className="text-xs px-2 py-0.5 rounded-md font-medium"
          style={{ background: `${palette.info}20`, color: palette.info }}
        >
          Info
        </span>
      </div>

      {/* Logo preview */}
      <div className="flex items-center gap-2 pt-1 border-t" style={{ borderColor: palette.borderSubtle }}>
        {logoSrc ? (
          <img src={logoSrc} alt="Logo" className="w-6 h-6 rounded-md object-contain" />
        ) : (
          <div className="w-6 h-6 rounded-md flex items-center justify-center text-[8px] font-bold" style={{ background: palette.accent, color: isDark ? "#0a0a0a" : "#ffffff" }}>
            B
          </div>
        )}
        <div className="flex flex-col leading-none">
          <span className="text-xs font-semibold" style={{ color: palette.logoPrimary }}>
            {appNamePrimary || "Bipros"}
          </span>
          <span className="text-[7px] font-semibold uppercase tracking-[0.15em]" style={{ color: palette.logoSecondary }}>
            {appNameSecondary || "EPPM"}
          </span>
        </div>
      </div>
    </div>
  );
}
