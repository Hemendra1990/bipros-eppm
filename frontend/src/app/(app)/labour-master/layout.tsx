import { Suspense } from "react";
import type { ReactNode } from "react";
import LabourMasterNav from "./LabourMasterNav";

export default function LabourMasterLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<div className="p-6 text-sm text-slate">Loading…</div>}>
      <LabourMasterNav>{children}</LabourMasterNav>
    </Suspense>
  );
}
