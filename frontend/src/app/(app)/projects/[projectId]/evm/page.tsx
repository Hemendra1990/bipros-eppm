"use client";

import { useParams } from "next/navigation";
import { EvmTab } from "@/components/evm/EvmTab";

/**
 * Standalone route for the Earned Value Management view so it is reachable at
 * /projects/{id}/evm (deep-link / direct navigation), not only via ?tab=evm on
 * the project overview. Renders inside the [projectId] layout, so it inherits
 * the project nav + ProjectCurrencyProvider exactly like the ?tab=evm render.
 */
export default function ProjectEvmPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  return <EvmTab projectId={projectId} />;
}
