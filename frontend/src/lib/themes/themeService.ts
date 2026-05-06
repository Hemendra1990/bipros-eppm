import type { PaletteValues, ThemeDefinition } from "./definitions";

function hexToRgba(hex: string, alpha: number): string {
  const sanitized = hex.replace("#", "");
  const bigint = parseInt(sanitized, 16);
  const r = (bigint >> 16) & 255;
  const g = (bigint >> 8) & 255;
  const b = bigint & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function hexToRgb(hex: string): { r: number; g: number; b: number } {
  const sanitized = hex.replace("#", "");
  const bigint = parseInt(sanitized, 16);
  return {
    r: (bigint >> 16) & 255,
    g: (bigint >> 8) & 255,
    b: bigint & 255,
  };
}

function relativeLuminance(hex: string): number {
  const { r, g, b } = hexToRgb(hex);
  const [rs, gs, bs] = [r, g, b].map((v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

function accentForeground(accentHex: string): string {
  return relativeLuminance(accentHex) > 0.5 ? "#171717" : "#fafafa";
}

function paletteToCSS(palette: PaletteValues, selector: ":root" | ".dark"): string {
  const isDark = selector === ".dark";
  const glowAlpha = isDark ? 0.2 : 0.12;
  const selectionAlpha = isDark ? 0.3 : 0.22;

  const lines: string[] = [
    `${selector} {`,
    `  --background: ${palette.background};`,
    `  --surface: ${palette.background};`,
    `  --paper: ${palette.background};`,
    `  --surface-hover: ${palette.backgroundSubtle};`,
    `  --ivory: ${palette.backgroundSubtle};`,
    `  --surface-active: ${palette.backgroundMuted};`,
    `  --parchment: ${palette.backgroundMuted};`,
    `  --foreground: ${palette.foreground};`,
    `  --text-primary: ${palette.foreground};`,
    `  --charcoal: ${palette.foreground};`,
    `  --text-secondary: ${palette.foregroundSecondary};`,
    `  --slate: ${palette.foregroundSecondary};`,
    `  --muted-foreground: ${palette.foregroundSecondary};`,
    `  --text-muted: ${palette.foregroundMuted};`,
    `  --ash: ${palette.foregroundMuted};`,
    `  --accent: ${palette.accent};`,
    `  --accent-foreground: ${accentForeground(palette.accent)};`,
    `  --gold: ${palette.accent};`,
    `  --ring: ${palette.accent};`,
    `  --accent-hover: ${palette.accentHover};`,
    `  --gold-deep: ${palette.accentHover};`,
    `  --gold-ink: ${palette.accentSubtle};`,
    `  --gold-tint: ${palette.accentTint};`,
    `  --border: ${palette.border};`,
    `  --hairline: ${palette.border};`,
    `  --border-subtle: ${palette.borderSubtle};`,
    `  --divider: ${palette.borderSubtle};`,
    `  --input: ${palette.borderSubtle};`,
    `  --success: ${palette.success};`,
    `  --emerald: ${palette.success};`,
    `  --warning: ${palette.warning};`,
    `  --bronze-warn: ${palette.warning};`,
    `  --danger: ${palette.danger};`,
    `  --burgundy: ${palette.danger};`,
    `  --info: ${palette.info};`,
    `  --steel: ${palette.info};`,
    `  --amber-flame: ${palette.amberFlame};`,
    `  --accent-glow: ${hexToRgba(palette.accent, glowAlpha)};`,
    `  --glass-bg: ${hexToRgba(palette.background, 0.7)};`,
    `  --glass-border: ${hexToRgba(palette.foreground, 0.06)};`,
    `  --selection-bg: ${hexToRgba(palette.accent, selectionAlpha)};`,
    `  --selection-color: ${palette.foreground};`,
    `  --scrollbar-thumb: ${palette.borderSubtle};`,
    `  --scrollbar-track: ${palette.background};`,
    `  --grid-color: ${hexToRgba(palette.foreground, 0.035)};`,
    `  --logo-primary: ${palette.logoPrimary};`,
    `  --logo-secondary: ${palette.logoSecondary};`,
    `}`,
  ];

  return lines.join("\n");
}

function themeToStyleContent(theme: ThemeDefinition): string {
  return [paletteToCSS(theme.light, ":root"), paletteToCSS(theme.dark, ".dark")].join("\n\n");
}

function injectThemeCSS(theme: ThemeDefinition): void {
  if (typeof document === "undefined") return;
  let style = document.getElementById("bipros-theme-vars") as HTMLStyleElement | null;
  if (!style) {
    style = document.createElement("style");
    style.id = "bipros-theme-vars";
    document.head.appendChild(style);
  }
  style.textContent = themeToStyleContent(theme);
}

function applyTheme(theme: ThemeDefinition): void {
  injectThemeCSS(theme);
  cacheThemeCSS(themeToStyleContent(theme));
}

function previewTheme(theme: ThemeDefinition): void {
  injectThemeCSS(theme);
}

function cacheThemeCSS(css: string): void {
  try {
    localStorage.setItem("bipros-theme-cache", css);
  } catch {
    // ignore storage errors
  }
}

function getCachedThemeCSS(): string | null {
  try {
    return localStorage.getItem("bipros-theme-cache");
  } catch {
    return null;
  }
}

export {
  hexToRgba,
  accentForeground,
  paletteToCSS,
  themeToStyleContent,
  applyTheme,
  previewTheme,
  cacheThemeCSS,
  getCachedThemeCSS,
};
