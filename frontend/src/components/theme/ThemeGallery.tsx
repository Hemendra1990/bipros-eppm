import type { ThemeDefinition } from "@/lib/themes/definitions";

interface ThemeGalleryProps {
  themes: ThemeDefinition[];
  activeThemeId: string;
  onSelect: (id: string) => void;
  isAdmin: boolean;
}

export function ThemeGallery({ themes, activeThemeId, onSelect, isAdmin }: ThemeGalleryProps) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
      {themes.map((theme) => {
        const isActive = theme.id === activeThemeId;
        const l = theme.light;
        const d = theme.dark;

        return (
          <div
            key={theme.id}
            className={`group relative rounded-xl border bg-surface overflow-hidden transition-all duration-200 ${
              isActive
                ? "ring-2 ring-offset-1 shadow-md"
                : "hover:-translate-y-0.5 hover:shadow-lg hover:border-accent/40"
            }`}
            style={{
              ringColor: isActive ? l.accent : undefined,
            } as React.CSSProperties}
          >
            {/* ── Mini UI Mock (~60% of card) ── */}
            <div
              className="relative h-[104px] p-2.5 flex gap-2"
              style={{ backgroundColor: l.background }}
            >
              {/* Sidebar strip */}
              <div
                className="w-5 rounded-md shrink-0"
                style={{ backgroundColor: l.backgroundSubtle }}
              />

              {/* Main mock area */}
              <div className="flex-1 flex flex-col gap-1.5 min-w-0">
                {/* Button row */}
                <div className="flex gap-1.5">
                  <div
                    className="h-3.5 rounded px-2 text-[7px] font-semibold flex items-center justify-center leading-none"
                    style={{ backgroundColor: l.accent, color: "#ffffff" }}
                  >
                    Primary
                  </div>
                  <div
                    className="h-3.5 rounded px-2 text-[7px] font-medium flex items-center justify-center leading-none border"
                    style={{
                      backgroundColor: "transparent",
                      color: l.foregroundSecondary,
                      borderColor: l.border,
                    }}
                  >
                    Ghost
                  </div>
                </div>

                {/* Mini card */}
                <div
                  className="rounded-md border p-1.5 flex flex-col gap-1"
                  style={{
                    backgroundColor: l.backgroundSubtle,
                    borderColor: l.borderSubtle,
                  }}
                >
                  <div className="flex items-center justify-between">
                    <span
                      className="text-[7px] font-semibold leading-none"
                      style={{ color: l.foreground }}
                    >
                      Card
                    </span>
                    <span
                      className="text-[6px] px-1 py-0.5 rounded-full font-medium leading-none"
                      style={{
                        backgroundColor: l.accentTint,
                        color: l.accentSubtle,
                      }}
                    >
                      Badge
                    </span>
                  </div>
                  <span
                    className="text-[6px] leading-tight"
                    style={{ color: l.foregroundSecondary }}
                  >
                    Body text preview
                  </span>
                </div>

                {/* Status dots */}
                <div className="flex gap-1">
                  <div
                    className="w-2 h-2 rounded-full"
                    style={{ backgroundColor: l.success }}
                    title="success"
                  />
                  <div
                    className="w-2 h-2 rounded-full"
                    style={{ backgroundColor: l.warning }}
                    title="warning"
                  />
                  <div
                    className="w-2 h-2 rounded-full"
                    style={{ backgroundColor: l.danger }}
                    title="danger"
                  />
                  <div
                    className="w-2 h-2 rounded-full"
                    style={{ backgroundColor: l.info }}
                    title="info"
                  />
                </div>
              </div>

              {/* Dark mode mini swatch — top-right corner */}
              <div className="absolute top-1.5 right-1.5 flex items-center gap-0.5">
                <div
                  className="w-2.5 h-2.5 rounded-full border"
                  style={{
                    backgroundColor: d.background,
                    borderColor: l.border,
                  }}
                  title="Dark background"
                />
                <div
                  className="w-2.5 h-2.5 rounded-full border"
                  style={{
                    backgroundColor: d.accent,
                    borderColor: l.border,
                  }}
                  title="Dark accent"
                />
              </div>
            </div>

            {/* ── Info strip (~40% of card) ── */}
            <div className="p-2.5 pt-2">
              <div className="flex items-center justify-between gap-2 mb-0.5">
                <h3 className="text-xs font-semibold text-text-primary truncate">
                  {theme.name}
                </h3>
                {isActive && (
                  <span
                    className="shrink-0 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium"
                    style={{
                      backgroundColor: `${l.accent}18`,
                      color: l.accent,
                    }}
                  >
                    Active
                  </span>
                )}
              </div>

              {theme.description && (
                <p className="text-[10px] text-text-muted truncate mb-2">
                  {theme.description}
                </p>
              )}

              {isAdmin && (
                <button
                  onClick={() => onSelect(theme.id)}
                  disabled={isActive}
                  className={`w-full mt-1 rounded-md px-2 py-1 text-[11px] font-medium transition-colors ${
                    isActive
                      ? "cursor-default"
                      : "hover:opacity-90"
                  }`}
                  style={{
                    backgroundColor: isActive ? l.border : l.accent,
                    color: isActive ? l.foregroundMuted : "#ffffff",
                  }}
                >
                  {isActive ? "Applied" : "Apply"}
                </button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
