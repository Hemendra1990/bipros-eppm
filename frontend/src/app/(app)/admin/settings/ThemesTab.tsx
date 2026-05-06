"use client";

import { useState, useCallback, useRef } from "react";
import { Plus, ArrowLeft } from "lucide-react";
import { useThemeManager } from "@/hooks/useThemeManager";
import { ThemeGallery } from "@/components/theme/ThemeGallery";
import { SavedThemesList } from "@/components/theme/SavedThemesList";
import { ThemeBuilder } from "@/components/theme/ThemeBuilder";
import { PREDEFINED_THEMES, type ThemeDefinition } from "@/lib/themes/definitions";

export function ThemesTab() {
  const {
    activeThemeId,
    activeTheme,
    customThemes,
    isAdmin,
    isLoading,
    switchTheme,
    saveCustomTheme,
    deleteCustomTheme,
    previewTheme,
    cancelPreview,
  } = useThemeManager();

  const [view, setView] = useState<"gallery" | "builder">("gallery");
  const [editingTheme, setEditingTheme] = useState<ThemeDefinition | undefined>(undefined);

  // Capture snapshot of active theme when opening builder so cancel restores exactly that
  const snapshotRef = useRef<ThemeDefinition | null>(null);

  const openBuilder = useCallback(
    (theme?: ThemeDefinition) => {
      snapshotRef.current = activeTheme;
      if (!theme) {
        // Seed a new custom theme from the currently active palette so the
        // builder starts from a familiar baseline instead of an empty default.
        setEditingTheme({
          ...activeTheme,
          id: "",
          name: "",
          isCustom: true,
          createdAt: undefined,
        });
      } else {
        setEditingTheme(theme);
      }
      setView("builder");
    },
    [activeTheme]
  );

  const handleCancelBuilder = useCallback(() => {
    setView("gallery");
    setEditingTheme(undefined);
    if (snapshotRef.current) {
      previewTheme(snapshotRef.current);
    } else {
      cancelPreview();
    }
  }, [previewTheme, cancelPreview]);

  const handleSaveBuilder = useCallback(
    (theme: ThemeDefinition) => {
      saveCustomTheme(theme);
      // Also make the saved theme active so the user sees their creation immediately.
      switchTheme(theme.id);
      setView("gallery");
      setEditingTheme(undefined);
    },
    [saveCustomTheme, switchTheme]
  );

  if (isLoading && view === "gallery") {
    return <div className="text-center text-text-muted py-12">Loading themes...</div>;
  }

  return (
    <div className="space-y-6">
      {view === "gallery" ? (
        <>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-text-primary">Predefined Themes</h2>
            {isAdmin && (
              <button
                onClick={() => openBuilder()}
                disabled={customThemes.length >= 20}
                className="flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:bg-border disabled:text-text-muted"
              >
                <Plus size={16} />
                Create Custom Theme
              </button>
            )}
          </div>

          <ThemeGallery
            themes={PREDEFINED_THEMES}
            activeThemeId={activeThemeId}
            onSelect={switchTheme}
            isAdmin={isAdmin}
          />

          <div className="border-t border-border pt-6">
            <SavedThemesList
              themes={customThemes}
              activeThemeId={activeThemeId}
              onSelect={switchTheme}
              onEdit={openBuilder}
              onDelete={deleteCustomTheme}
              isAdmin={isAdmin}
            />
          </div>
        </>
      ) : (
        <div className="space-y-4">
          <button
            onClick={handleCancelBuilder}
            className="flex items-center gap-2 text-sm text-text-secondary hover:text-text-primary"
          >
            <ArrowLeft size={16} />
            Back to Gallery
          </button>

          <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
            <h3 className="mb-4 text-lg font-semibold text-text-primary">
              {editingTheme?.id ? "Edit Custom Theme" : "Create Custom Theme"}
            </h3>
            <ThemeBuilder
              initialTheme={editingTheme}
              onSave={handleSaveBuilder}
              onCancel={handleCancelBuilder}
              onPreview={previewTheme}
            />
          </div>
        </div>
      )}
    </div>
  );
}
