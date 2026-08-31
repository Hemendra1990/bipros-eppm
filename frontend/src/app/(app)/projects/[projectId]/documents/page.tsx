"use client";

import { useParams } from "next/navigation";
import { ProjectDocumentsPanel } from "@/components/document/ProjectDocumentsPanel";

export default function DocumentsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  return <ProjectDocumentsPanel projectId={projectId} />;
}
