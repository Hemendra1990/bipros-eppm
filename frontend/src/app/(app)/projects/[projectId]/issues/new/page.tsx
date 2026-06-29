"use client";

import { useParams, useRouter } from "next/navigation";
import { PageHeader } from "@/components/common/PageHeader";
import { IssueForm } from "@/components/dpr/IssueForm";

/**
 * Standalone New-Issue route. The primary creation surface is the drawer on the
 * Issues list page; this route remains for deep links (e.g. the Stock Register
 * "raise issue" shortcut) and reuses the same {@link IssueForm}.
 */
export default function NewProjectIssuePage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const back = () => router.push(`/projects/${projectId}/issues`);

  return (
    <div className="mx-auto max-w-2xl space-y-5 py-2">
      <PageHeader title="New Issue" description="Log a field issue directly against this project." />
      <div className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
        <IssueForm projectId={projectId} issue={null} onSaved={back} onCancel={back} />
      </div>
    </div>
  );
}
