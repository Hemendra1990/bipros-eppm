"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { PageHeader } from "@/components/common/PageHeader";
import { calendarApi } from "@/lib/api/calendarApi";
import type { CreateCalendarRequest } from "@/lib/api/calendarApi";

export default function NewCalendarPage() {
  const router = useRouter();

  const [formData, setFormData] = useState<CreateCalendarRequest>({
    code: "",
    name: "",
    type: "GLOBAL",
    workHoursPerDay: 8,
    workDaysPerWeek: 5,
  });

  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]:
        name === "workHoursPerDay" || name === "workDaysPerWeek" ? parseInt(value, 10) : value,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!formData.code || !formData.name) {
      setError("Code and Name are required");
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await calendarApi.createCalendar(formData);
      if (result.data) {
        router.push("/admin/calendars");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create calendar");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="New Calendar"
        description="Create a new project, resource, or global calendar"
      />

      <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <form onSubmit={handleSubmit} className="space-y-6">
          {error && (
            <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">{error}</div>
          )}

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700">Code *</label>
              <input
                type="text"
                name="code"
                value={formData.code}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., CAL-001"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">Name *</label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., Standard 9-5 Calendar"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700">Calendar Type *</label>
              <select
                name="type"
                value={formData.type}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="GLOBAL">Global</option>
                <option value="PROJECT">Project</option>
                <option value="RESOURCE">Resource</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">
                Work Days Per Week
              </label>
              <input
                type="number"
                name="workDaysPerWeek"
                value={formData.workDaysPerWeek || 5}
                onChange={handleChange}
                min="1"
                max="7"
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              Work Hours Per Day
            </label>
            <input
              type="number"
              name="workHoursPerDay"
              value={formData.workHoursPerDay || 8}
              onChange={handleChange}
              min="1"
              max="24"
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div className="flex gap-3 pt-6">
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
            >
              {isSubmitting ? "Creating..." : "Create Calendar"}
            </button>
            <button
              type="button"
              onClick={() => router.back()}
              className="rounded-md bg-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-300"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
