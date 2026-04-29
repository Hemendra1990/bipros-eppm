import { create } from "zustand";
import { persist } from "zustand/middleware";

interface ScheduleStaleState {
  scheduleStale: Record<string, boolean>;
  markScheduleStale: (projectId: string) => void;
  markScheduleFresh: (projectId: string) => void;
  isScheduleStale: (projectId: string) => boolean;
}

export const useScheduleStaleStore = create<ScheduleStaleState>()(
  persist(
    (set, get) => ({
      scheduleStale: {},
      markScheduleStale: (projectId) =>
        set((s) => ({ scheduleStale: { ...s.scheduleStale, [projectId]: true } })),
      markScheduleFresh: (projectId) =>
        set((s) => ({ scheduleStale: { ...s.scheduleStale, [projectId]: false } })),
      isScheduleStale: (projectId) => get().scheduleStale[projectId] === true,
    }),
    { name: "bipros-schedule-stale" }
  )
);
