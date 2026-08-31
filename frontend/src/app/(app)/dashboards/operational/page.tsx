"use client";

import Link from "next/link";
import { ArrowLeft, FolderKanban } from "lucide-react";

/**
 * The Operational dashboard is now a project-scoped view living at
 * /projects/{projectId}/insights/operational. This stub exists so old bookmarks
 * don't 404 — it routes the user to the project picker with a hint.
 */
export default function MovedOperationalDashboardStub() {
  return (
    <div className="mx-auto max-w-2xl py-12 text-center">
      <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gold-tint text-gold-deep ring-1 ring-gold/30">
        <FolderKanban size={20} strokeWidth={1.75} />
      </div>
      <h1
        className="font-display text-[28px] font-semibold leading-[1.1] tracking-tight text-charcoal"
        style={{ fontVariationSettings: "'opsz' 144" }}
      >
        Operational dashboard moved
      </h1>
      <p className="mt-2 text-sm leading-relaxed text-slate">
        The Operational dashboard is now a project-scoped view. Open a project and use the{" "}
        <span className="font-semibold text-charcoal">Insights</span> tab.
      </p>
      <div className="mt-6 flex items-center justify-center gap-3">
        <Link
          href="/projects"
          className="inline-flex items-center gap-2 rounded-lg border border-gold/45 bg-gold-tint/40 px-4 py-2 text-sm font-semibold text-gold-deep transition-colors hover:border-gold hover:bg-gold-tint"
        >
          Pick a project →
        </Link>
        <Link
          href="/dashboards"
          className="inline-flex items-center gap-2 rounded-lg border border-hairline bg-paper px-4 py-2 text-sm text-slate transition-colors hover:border-gold/40 hover:text-charcoal"
        >
          <ArrowLeft size={14} strokeWidth={1.75} />
          Back to dashboards
        </Link>
      </div>
    </div>
  );
}
