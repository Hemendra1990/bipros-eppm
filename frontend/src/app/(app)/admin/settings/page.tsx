"use client";

import { useState } from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import { settingsApi } from "@/lib/api/settingsApi";
import { PageHeader } from "@/components/common/PageHeader";

export default function SettingsPage() {
  const { data: settingsData, isLoading } = useQuery({
    queryKey: ["settings"],
    queryFn: () => settingsApi.listSettings(),
  });

  const [formData, setFormData] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const settings = settingsData?.data?.settings ?? [];

  const handleChange = (key: string, value: string) => {
    setFormData((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    setIsSubmitting(true);

    try {
      const updates = Object.entries(formData)
        .filter(([key]) => formData[key] !== undefined)
        .map(([key, value]) => ({ key, value }));

      if (updates.length === 0) {
        setError("No changes to save");
        setIsSubmitting(false);
        return;
      }

      await settingsApi.bulkUpdateSettings(updates);
      setSuccess("Settings saved successfully");
      setFormData({});
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save settings");
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <div className="text-center text-gray-500">Loading settings...</div>;
  }

  const groupedSettings = settings.reduce(
    (acc, setting) => {
      const category = setting.category || "General";
      if (!acc[category]) {
        acc[category] = [];
      }
      acc[category].push(setting);
      return acc;
    },
    {} as Record<string, typeof settings>
  );

  return (
    <div>
      <PageHeader
        title="Settings"
        description="Configure global settings for your EPPM system"
      />

      <form onSubmit={handleSubmit} className="space-y-6">
        {error && (
          <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">{error}</div>
        )}
        {success && (
          <div className="rounded-md bg-green-50 p-4 text-sm text-green-700">{success}</div>
        )}

        {Object.entries(groupedSettings).map(([category, categorySettings]) => (
          <div
            key={category}
            className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
          >
            <h2 className="mb-4 text-lg font-semibold text-gray-900">{category}</h2>

            <div className="space-y-4">
              {categorySettings.map((setting) => (
                <div key={setting.key}>
                  <label className="block text-sm font-medium text-gray-700">
                    {setting.key}
                  </label>
                  {setting.description && (
                    <p className="mt-0.5 text-xs text-gray-500">{setting.description}</p>
                  )}
                  <input
                    type="text"
                    value={formData[setting.key] ?? setting.value ?? ""}
                    onChange={(e) => handleChange(setting.key, e.target.value)}
                    className="mt-2 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>
              ))}
            </div>
          </div>
        ))}

        <div className="flex gap-3 pt-6">
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
          >
            {isSubmitting ? "Saving..." : "Save Settings"}
          </button>
        </div>
      </form>
    </div>
  );
}
