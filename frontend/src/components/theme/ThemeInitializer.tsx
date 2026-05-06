"use client";

import { useThemeManager } from "@/hooks/useThemeManager";

export function ThemeInitializer() {
  // Initialise theme system: restores cached CSS immediately and triggers
  // backend sync via useQuery inside useThemeManager.
  useThemeManager();
  return null;
}
