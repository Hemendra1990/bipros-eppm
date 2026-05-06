"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { calendarApi } from "@/lib/api/calendarApi";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import type { CalendarResponse } from "@/lib/types";

export default function CalendarsPage() {
  const router = useRouter();
  const { data: calendarsData, isLoading, error } = useQuery({
    queryKey: ["calendars"],
    queryFn: async () => {
      // Fetch all types and combine
      const [global, project, resource] = await Promise.all([
        calendarApi.listCalendars("GLOBAL"),
        calendarApi.listCalendars("PROJECT"),
        calendarApi.listCalendars("RESOURCE"),
      ]);
      const all = [...(global?.data ?? []), ...(project?.data ?? []), ...(resource?.data ?? [])];
      return { data: all, error: null, meta: global?.meta };
    },
  });

  const rawData = calendarsData?.data;
  const calendars = useMemo(
    () => (Array.isArray(rawData) ? rawData : (rawData as any)?.content ?? []),
    [rawData],
  );

  const columns = useMemo<ColumnDef<CalendarResponse, unknown>[]>(() => [
    { accessorKey: "name", header: "Name", enableSorting: true },
    {
      accessorKey: "calendarType",
      header: "Type",
      enableSorting: true,
      cell: (info) => <span className="text-sm font-medium">{String(info.getValue())}</span>,
    },
    { accessorKey: "standardWorkHoursPerDay", header: "Hours/Day", enableSorting: true },
    { accessorKey: "standardWorkDaysPerWeek", header: "Days/Week", enableSorting: true },
    {
      accessorKey: "createdAt",
      header: "Created",
      enableSorting: true,
      cell: (info) => {
        const value = info.getValue();
        if (!value) return "—";
        return new Date(value as string).toLocaleDateString();
      },
    },
  ], []);

  return (
    <div>
      <PageHeader
        title="Calendars"
        description="Manage global, project, and resource calendars"
        actions={
          <button
            onClick={() => router.push("/admin/calendars/new")}
            className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover"
          >
            <Plus size={16} />
            New Calendar
          </button>
        }
      />

      {isLoading && (
        <div className="py-12 text-center text-text-muted">Loading calendars...</div>
      )}

      {error && (
        <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">
          Failed to load calendars. Is the backend running?
        </div>
      )}

      {!isLoading && calendars.length === 0 && (
        <EmptyState
          title="No calendars yet"
          description="Create your first calendar to define work schedules."
        />
      )}

      {calendars.length > 0 && (
        <VirtualDataTable
          columns={columns}
          data={calendars}
          sortable
          resizable
          onRowClick={(row) => router.push(`/admin/calendars/${row.id}`)}
        />
      )}
    </div>
  );
}
