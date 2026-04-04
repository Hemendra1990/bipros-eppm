"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { calendarApi } from "@/lib/api/calendarApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
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
  const calendars = Array.isArray(rawData) ? rawData : (rawData as any)?.content ?? [];

  const columns: ColumnDef<CalendarResponse>[] = [
    { key: "name", label: "Name", sortable: true },
    {
      key: "calendarType",
      label: "Type",
      sortable: true,
      render: (value) => <span className="text-sm font-medium">{String(value)}</span>,
    },
    { key: "standardWorkHoursPerDay", label: "Hours/Day", sortable: true },
    { key: "standardWorkDaysPerWeek", label: "Days/Week", sortable: true },
    { key: "createdAt", label: "Created", sortable: true },
  ];

  return (
    <div>
      <PageHeader
        title="Calendars"
        description="Manage global, project, and resource calendars"
        actions={
          <button
            onClick={() => router.push("/admin/calendars/new")}
            className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Plus size={16} />
            New Calendar
          </button>
        }
      />

      {isLoading && (
        <div className="py-12 text-center text-gray-500">Loading calendars...</div>
      )}

      {error && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
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
        <DataTable columns={columns} data={calendars} rowKey="id" />
      )}
    </div>
  );
}
