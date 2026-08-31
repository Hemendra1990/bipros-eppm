"use client";

import { SectionNav, type SectionNavItem } from "@/components/common/dashboard/SectionNav";
import { useAuthStore } from "@/lib/state/store";
import { ProjectStatusSnapshot } from "@/components/reports/project/ProjectStatusSnapshot";
import { MilestoneTracker } from "@/components/reports/project/MilestoneTracker";
import { TasksSection } from "./TasksSection";
import { ScheduleSection } from "./ScheduleSection";
import { CostSection } from "./CostSection";
import { EvmCashFlowSection } from "./EvmCashFlowSection";
import { ResourcesSection } from "./ResourcesSection";
import { RisksSection } from "./RisksSection";
import { BillsVosSection } from "./BillsVosSection";

const sections: SectionNavItem[] = [
  { id: "status", label: "Status" },
  { id: "tasks", label: "Tasks" },
  { id: "schedule", label: "Schedule" },
  { id: "cost", label: "Cost" },
  { id: "evm-cash", label: "EVM & Cash Flow" },
  { id: "resources", label: "Resources" },
  { id: "risks", label: "Risks" },
  { id: "milestones", label: "Milestones" },
  { id: "bills-vos", label: "Bills & VOs" },
];

export function ProjectReportsCanvas({ projectId }: { projectId: string }) {
  // Access-Output row 4: the Cost section reads COST.READ endpoints — hide it (and its
  // nav entry) from roles without that permission.
  const canCost = useAuthStore((st) => st.hasPermission)("COST.READ");
  const visibleSections = canCost ? sections : sections.filter((s) => s.id !== "cost");
  return (
    <div>
      <SectionNav sections={visibleSections} />

      <div className="space-y-6">
        <section id="status" className="scroll-mt-20">
          <ProjectStatusSnapshot projectId={projectId} />
        </section>

        <section id="tasks" className="scroll-mt-20">
          <TasksSection projectId={projectId} />
        </section>

        <section id="schedule" className="scroll-mt-20">
          <ScheduleSection projectId={projectId} />
        </section>

        {canCost && (
          <section id="cost" className="scroll-mt-20">
            <CostSection projectId={projectId} />
          </section>
        )}

        <section id="evm-cash" className="scroll-mt-20">
          <EvmCashFlowSection projectId={projectId} />
        </section>

        <section id="resources" className="scroll-mt-20">
          <ResourcesSection projectId={projectId} />
        </section>

        <section id="risks" className="scroll-mt-20">
          <RisksSection projectId={projectId} />
        </section>

        <section id="milestones" className="scroll-mt-20">
          <MilestoneTracker projectId={projectId} />
        </section>

        <section id="bills-vos" className="scroll-mt-20">
          <BillsVosSection projectId={projectId} />
        </section>
      </div>
    </div>
  );
}
