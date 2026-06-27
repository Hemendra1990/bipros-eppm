"use client";

import { useParams } from "next/navigation";
import { BaselinesPanel } from "@/components/baseline/BaselinesPanel";

export default function BaselinesPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  return <BaselinesPanel projectId={projectId} />;
}
