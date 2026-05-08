"use client";

import { useEffect } from "react";
import { useParams, useRouter } from "next/navigation";

/**
 * Bare /projects/{id}/insights → land on the Operational sub-view by default.
 */
export default function InsightsIndexPage() {
  const router = useRouter();
  const params = useParams();
  const projectId = params.projectId as string;

  useEffect(() => {
    router.replace(`/projects/${projectId}/insights/operational`);
  }, [projectId, router]);

  return (
    <div className="p-6 text-sm text-text-muted">Loading insights…</div>
  );
}
