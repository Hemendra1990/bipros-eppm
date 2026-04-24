"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

/**
 * Moved. Portfolio analytics is a dashboard, not a report — live at /dashboards/portfolio.
 * This stub preserves any stale bookmarks pointing at /reports/portfolio.
 */
export default function DeprecatedPortfolioReportsPage() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/dashboards/portfolio");
  }, [router]);
  return (
    <div className="p-6 text-sm text-text-muted">
      Redirecting to Portfolio Dashboard…
    </div>
  );
}
