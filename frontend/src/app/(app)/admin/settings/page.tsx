"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { settingsApi, type SettingResponse, type CurrencyResponse } from "@/lib/api/settingsApi";
import { PageHeader } from "@/components/common/PageHeader";
import { TabTip } from "@/components/common/TabTip";
import { Save, Plus, Trash2, Settings2, DollarSign } from "lucide-react";
import toast from "react-hot-toast";

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const [activeSection, setActiveSection] = useState<"settings" | "currencies">("settings");

  return (
    <div>
      <PageHeader
        title="Settings"
        description="Configure global settings for your EPPM system"
      />

      <TabTip
        title="System Settings"
        description="Manage global configuration like scheduling defaults, EVM technique, and currencies. Changes here affect all new projects."
      />

      {/* Section Tabs */}
      <div className="flex gap-2 mb-6">
        <button
          onClick={() => setActiveSection("settings")}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            activeSection === "settings"
              ? "bg-blue-600 text-white"
              : "bg-slate-800/50 text-slate-400 hover:text-white hover:bg-slate-800"
          }`}
        >
          <Settings2 size={16} />
          Global Settings
        </button>
        <button
          onClick={() => setActiveSection("currencies")}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            activeSection === "currencies"
              ? "bg-blue-600 text-white"
              : "bg-slate-800/50 text-slate-400 hover:text-white hover:bg-slate-800"
          }`}
        >
          <DollarSign size={16} />
          Currencies
        </button>
      </div>

      {activeSection === "settings" && <GlobalSettingsSection />}
      {activeSection === "currencies" && <CurrenciesSection />}
    </div>
  );
}

function GlobalSettingsSection() {
  const queryClient = useQueryClient();
  const { data: settingsData, isLoading } = useQuery({
    queryKey: ["admin-settings"],
    queryFn: () => settingsApi.listSettings(),
  });

  const [editValues, setEditValues] = useState<Record<string, string>>({});

  const updateMutation = useMutation({
    mutationFn: ({ id, setting }: { id: string; setting: SettingResponse }) =>
      settingsApi.updateSetting(id, {
        settingKey: setting.settingKey,
        settingValue: editValues[setting.id] ?? setting.settingValue,
        description: setting.description,
        category: setting.category,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-settings"] });
      toast.success("Setting updated");
      setEditValues({});
    },
    onError: (err: unknown) => {
      const message = err instanceof Error ? err.message : "Failed to update setting";
      toast.error(message);
    },
  });

  const settings = Array.isArray(settingsData?.data) ? settingsData.data : [];

  if (isLoading) {
    return <div className="text-center text-slate-500 py-12">Loading settings...</div>;
  }

  // Group settings by category
  const grouped = settings.reduce((acc: Record<string, SettingResponse[]>, s: SettingResponse) => {
    const cat = s.category || "general";
    if (!acc[cat]) acc[cat] = [];
    acc[cat].push(s);
    return acc;
  }, {});

  if (Object.keys(grouped).length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-700 py-12 text-center">
        <p className="text-slate-500">No settings configured yet.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {Object.entries(grouped).map(([category, categorySettings]) => (
        <div key={category} className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h2 className="mb-4 text-lg font-semibold text-white capitalize">{category}</h2>
          <div className="space-y-4">
            {categorySettings.map((setting) => (
              <div key={setting.id} className="flex items-end gap-4">
                <div className="flex-1">
                  <label className="block text-sm font-medium text-slate-300">
                    {setting.settingKey.replace(/\./g, " ").replace(/default /i, "")}
                  </label>
                  {setting.description && (
                    <p className="mt-0.5 text-xs text-slate-500">{setting.description}</p>
                  )}
                  <input
                    type="text"
                    value={editValues[setting.id] ?? setting.settingValue}
                    onChange={(e) => setEditValues((prev) => ({ ...prev, [setting.id]: e.target.value }))}
                    className="mt-2 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>
                {editValues[setting.id] !== undefined && editValues[setting.id] !== setting.settingValue && (
                  <button
                    onClick={() => updateMutation.mutate({ id: setting.id, setting })}
                    disabled={updateMutation.isPending}
                    className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-600 flex items-center gap-1"
                  >
                    <Save size={14} />
                    Save
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

function CurrenciesSection() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    code: "",
    name: "",
    symbol: "",
    exchangeRate: 1.0,
    decimalPlaces: 2,
  });

  const { data: currencyData, isLoading } = useQuery({
    queryKey: ["admin-currencies"],
    queryFn: () => settingsApi.listCurrencies(),
  });

  const createMutation = useMutation({
    mutationFn: () => settingsApi.createCurrency({
      ...formData,
      isBaseCurrency: false,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-currencies"] });
      toast.success("Currency added");
      setFormData({ code: "", name: "", symbol: "", exchangeRate: 1.0, decimalPlaces: 2 });
      setShowForm(false);
    },
    onError: (err: unknown) => {
      const message = err instanceof Error ? err.message : "Failed to add currency";
      toast.error(message);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => settingsApi.deleteCurrency(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-currencies"] });
      toast.success("Currency deleted");
    },
    onError: (err: unknown) => {
      const message = err instanceof Error ? err.message : "Failed to delete currency";
      toast.error(message);
    },
  });

  const currencies = Array.isArray(currencyData?.data) ? currencyData.data : [];

  return (
    <div className="space-y-6">
      <div className="flex justify-end">
        <button
          onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
        >
          <Plus size={16} />
          Add Currency
        </button>
      </div>

      {showForm && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h3 className="mb-4 text-lg font-semibold text-white">New Currency</h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-300">Code *</label>
              <input
                type="text"
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                placeholder="e.g., INR"
                maxLength={3}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300">Name *</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="e.g., Indian Rupee"
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300">Symbol *</label>
              <input
                type="text"
                value={formData.symbol}
                onChange={(e) => setFormData({ ...formData, symbol: e.target.value })}
                placeholder="e.g., ₹"
                maxLength={3}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300">Exchange Rate</label>
              <input
                type="number"
                step="0.01"
                value={formData.exchangeRate}
                onChange={(e) => setFormData({ ...formData, exchangeRate: parseFloat(e.target.value) || 1 })}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
              />
            </div>
          </div>
          <div className="mt-4 flex gap-2">
            <button
              onClick={() => createMutation.mutate()}
              disabled={!formData.code || !formData.name || !formData.symbol || createMutation.isPending}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-600"
            >
              {createMutation.isPending ? "Adding..." : "Add Currency"}
            </button>
            <button
              onClick={() => setShowForm(false)}
              className="rounded-md border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="text-center text-slate-500 py-12">Loading currencies...</div>
      ) : currencies.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-700 py-12 text-center">
          <p className="text-slate-500">No currencies configured. Add your first currency.</p>
        </div>
      ) : (
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 shadow-lg overflow-hidden">
          <table className="min-w-full divide-y divide-slate-800/50">
            <thead className="bg-slate-900/80">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">Code</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">Name</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">Symbol</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">Exchange Rate</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">Base</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/50">
              {currencies.map((c: CurrencyResponse) => (
                <tr key={c.id} className="hover:bg-slate-800/30">
                  <td className="px-6 py-4 text-sm font-medium text-blue-400">{c.code}</td>
                  <td className="px-6 py-4 text-sm text-slate-300">{c.name}</td>
                  <td className="px-6 py-4 text-sm text-slate-300">{c.symbol}</td>
                  <td className="px-6 py-4 text-sm text-slate-300">{c.exchangeRate}</td>
                  <td className="px-6 py-4 text-sm">
                    {c.isBaseCurrency ? (
                      <span className="bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20 rounded-md px-2 py-0.5 text-xs font-medium">Base</span>
                    ) : (
                      <span className="text-slate-500">—</span>
                    )}
                  </td>
                  <td className="px-6 py-4 text-sm">
                    {!c.isBaseCurrency && (
                      <button
                        onClick={() => deleteMutation.mutate(c.id)}
                        disabled={deleteMutation.isPending}
                        className="text-red-400 hover:text-red-300 disabled:text-slate-600"
                      >
                        <Trash2 size={16} />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
