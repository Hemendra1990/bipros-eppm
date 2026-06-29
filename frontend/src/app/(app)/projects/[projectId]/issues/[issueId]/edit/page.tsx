"use client";

import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { dprIssueApi } from "@/lib/api/dprIssueApi";
import { PageHeader } from "@/components/common/PageHeader";
import { IssueForm } from "@/components/dpr/IssueForm";

/**
 * Standalone Edit-Issue route. The list page edits in a drawer; this route is the
 * deep-link / direct-URL fallback and reuses the same {@link IssueForm}.
 */
export default function EditIssuePage() {
  const params = useParams<{ projectId: string; issueId: string }>();
  const { projectId, issueId } = params;
  const router = useRouter();
  const back = () => router.push(`/projects/${projectId}/issues`);

  const { data, isLoading } = useQuery({
    queryKey: ["dpr-issue", projectId, issueId],
    queryFn: () => dprIssueApi.get(projectId, issueId),
    enabled: !!projectId && !!issueId,
  });

  const issue = data?.data ?? null;

  return (
    <div className="mx-auto max-w-2xl space-y-5 py-2">
      <PageHeader title="Edit Issue" description="Update the details of this issue." />
      {isLoading || !issue ? (
        <div className="rounded-2xl border border-border bg-surface p-6 text-sm text-text-muted shadow-sm">
          Loading…
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          <IssueForm projectId={projectId} issue={issue} onSaved={back} onCancel={back} />
        </div>
      )}
    </div>
  );
}
