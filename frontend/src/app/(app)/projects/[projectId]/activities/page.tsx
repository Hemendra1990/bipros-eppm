"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { activityApi } from "@/lib/api/activityApi";
import { StatusBadge } from "@/components/common/StatusBadge";
import Link from "next/link";

export default function ActivitiesPage() {
  const router = useRouter();
  const params = useParams();
  const projectId = params.projectId as string;

  const [lookAheadWeeks, setLookAheadWeeks] = useState<4 | 13 | null>(null);

  const { data: activitiesData, isLoading } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId),
    enabled: !!projectId,
  });

  const activities = (activitiesData?.data?.content || []) as any[];

  // Filter activities by look-ahead window
  const getFilteredActivities = () => {
    if (!lookAheadWeeks) return activities;

    const today = new Date();
    const endDate = new Date(today);
    endDate.setDate(endDate.getDate() + lookAheadWeeks * 7);

    return activities.filter((activity: any) => {
      const startDate = activity.earlyStartDate || activity.plannedStartDate;
      if (!startDate) return false;

      const activityStart = new Date(startDate);
      return activityStart >= today && activityStart <= endDate;
    });
  };

  const filteredActivities = getFilteredActivities();

  return (
    <div>
      <PageHeader
        title="Activities"
        description="View and manage project activities"
        actions={
          <button
            onClick={() => router.push(`/projects/${projectId}/activities/new`)}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            New Activity
          </button>
        }
      />

      {/* Look-Ahead Filter */}
      <div className="mb-6 flex gap-2">
        <button
          onClick={() => setLookAheadWeeks(null)}
          className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
            lookAheadWeeks === null
              ? "bg-blue-600 text-white"
              : "bg-gray-200 text-gray-700 hover:bg-gray-300"
          }`}
        >
          All Activities
        </button>
        <button
          onClick={() => setLookAheadWeeks(4)}
          className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
            lookAheadWeeks === 4
              ? "bg-blue-600 text-white"
              : "bg-gray-200 text-gray-700 hover:bg-gray-300"
          }`}
        >
          4-Week Look-Ahead
        </button>
        <button
          onClick={() => setLookAheadWeeks(13)}
          className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
            lookAheadWeeks === 13
              ? "bg-blue-600 text-white"
              : "bg-gray-200 text-gray-700 hover:bg-gray-300"
          }`}
        >
          13-Week Look-Ahead
        </button>
      </div>

      {isLoading ? (
        <div className="text-center text-gray-500">Loading activities...</div>
      ) : filteredActivities.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-gray-50 p-8 text-center">
          <p className="text-gray-500">
            {lookAheadWeeks
              ? `No activities scheduled in the next ${lookAheadWeeks} weeks`
              : "No activities found"}
          </p>
        </div>
      ) : (
        <div className="rounded-lg border border-gray-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="border-b border-gray-200 bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                    Code
                  </th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                    Name
                  </th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                    Status
                  </th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                    % Complete
                  </th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                    Start Date
                  </th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                    Finish Date
                  </th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                    Duration
                  </th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                    Float
                  </th>
                  <th className="px-6 py-3 text-right text-sm font-semibold text-gray-700">
                    Action
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {filteredActivities.map((activity: any) => (
                  <tr key={activity.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 text-sm font-medium text-gray-900">
                      {activity.code}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-900">
                      {activity.name}
                    </td>
                    <td className="px-6 py-4 text-sm">
                      <StatusBadge status={activity.status} />
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-600">
                      {activity.percentComplete}%
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-600">
                      {activity.earlyStartDate || activity.plannedStartDate || "-"}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-600">
                      {activity.earlyFinishDate || activity.plannedFinishDate || "-"}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-600">
                      {activity.remainingDuration || activity.originalDuration || "-"} days
                    </td>
                    <td className="px-6 py-4 text-sm">
                      {activity.totalFloat !== undefined ? (
                        <span
                          className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${
                            activity.totalFloat === 0
                              ? "bg-red-100 text-red-800"
                              : activity.totalFloat <= 5
                                ? "bg-amber-100 text-amber-800"
                                : "bg-green-100 text-green-800"
                          }`}
                        >
                          {activity.totalFloat.toFixed(1)} days
                        </span>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td className="px-6 py-4 text-right text-sm">
                      <Link
                        href={`/projects/${projectId}/activities/${activity.id}`}
                        className="text-blue-600 hover:text-blue-900 hover:underline"
                      >
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {lookAheadWeeks && (
        <div className="mt-6 rounded-lg bg-blue-50 p-4">
          <p className="text-sm text-blue-800">
            Showing {filteredActivities.length} of {activities.length} activities scheduled in the
            next {lookAheadWeeks} weeks.
          </p>
        </div>
      )}
    </div>
  );
}
