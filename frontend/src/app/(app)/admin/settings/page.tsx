"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { settingsApi, type SettingResponse, type CurrencyResponse } from "@/lib/api/settingsApi";
import { PageHeader } from "@/components/common/PageHeader";
import { TabTip } from "@/components/common/TabTip";
import { Save, Plus, Trash2, Settings2, DollarSign, Bot } from "lucide-react";
import toast from "react-hot-toast";

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const [activeSection, setActiveSection] = useState<"settings" | "currencies" | "ai">("settings");

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
              ? "bg-accent text-text-primary"
              : "bg-surface-hover/50 text-text-secondary hover:text-text-primary hover:bg-surface-hover"
          }`}
        >
          <Settings2 size={16} />
          Global Settings
        </button>
        <button
          onClick={() => setActiveSection("currencies")}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            activeSection === "currencies"
              ? "bg-accent text-text-primary"
              : "bg-surface-hover/50 text-text-secondary hover:text-text-primary hover:bg-surface-hover"
          }`}
        >
          <DollarSign size={16} />
          Currencies
        </button>
        <button
          onClick={() => setActiveSection("ai")}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            activeSection === "ai"
              ? "bg-accent text-text-primary"
              : "bg-surface-hover/50 text-text-secondary hover:text-text-primary hover:bg-surface-hover"
          }`}
        >
          <Bot size={16} />
          AI Providers
        </button>
      </div>

      {activeSection === "settings" && <GlobalSettingsSection />}
      {activeSection === "currencies" && <CurrenciesSection />}
      {activeSection === "ai" && <AiProvidersSection />}
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
    return <div className="text-center text-text-muted py-12">Loading settings...</div>;
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
      <div className="rounded-xl border border-dashed border-border py-12 text-center">
        <p className="text-text-muted">No settings configured yet.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {Object.entries(grouped).map(([category, categorySettings]) => (
        <div key={category} className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h2 className="mb-4 text-lg font-semibold text-text-primary capitalize">{category}</h2>
          <div className="space-y-4">
            {categorySettings.map((setting) => (
              <div key={setting.id} className="flex items-end gap-4">
                <div className="flex-1">
                  <label className="block text-sm font-medium text-text-secondary">
                    {setting.settingKey.replace(/\./g, " ").replace(/default /i, "")}
                  </label>
                  {setting.description && (
                    <p className="mt-0.5 text-xs text-text-muted">{setting.description}</p>
                  )}
                  <input
                    type="text"
                    value={editValues[setting.id] ?? setting.settingValue}
                    onChange={(e) => setEditValues((prev) => ({ ...prev, [setting.id]: e.target.value }))}
                    className="mt-2 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  />
                </div>
                {editValues[setting.id] !== undefined && editValues[setting.id] !== setting.settingValue && (
                  <button
                    onClick={() => updateMutation.mutate({ id: setting.id, setting })}
                    disabled={updateMutation.isPending}
                    className="rounded-md bg-accent px-3 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border flex items-center gap-1"
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
          className="flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={16} />
          Add Currency
        </button>
      </div>

      {showForm && (
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="mb-4 text-lg font-semibold text-text-primary">New Currency</h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-text-secondary">Code *</label>
              <input
                type="text"
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                placeholder="e.g., INR"
                maxLength={3}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Name *</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="e.g., Indian Rupee"
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Symbol *</label>
              <input
                type="text"
                value={formData.symbol}
                onChange={(e) => setFormData({ ...formData, symbol: e.target.value })}
                placeholder="e.g., ₹"
                maxLength={3}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Exchange Rate</label>
              <input
                type="number"
                step="0.01"
                value={formData.exchangeRate}
                onChange={(e) => setFormData({ ...formData, exchangeRate: parseFloat(e.target.value) || 1 })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
          </div>
          <div className="mt-4 flex gap-2">
            <button
              onClick={() => createMutation.mutate()}
              disabled={!formData.code || !formData.name || !formData.symbol || createMutation.isPending}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
            >
              {createMutation.isPending ? "Adding..." : "Add Currency"}
            </button>
            <button
              onClick={() => setShowForm(false)}
              className="rounded-md border border-border px-4 py-2 text-sm text-text-secondary hover:bg-surface-hover"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="text-center text-text-muted py-12">Loading currencies...</div>
      ) : currencies.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border py-12 text-center">
          <p className="text-text-muted">No currencies configured. Add your first currency.</p>
        </div>
      ) : (
        <div className="rounded-xl border border-border bg-surface/50 shadow-lg overflow-hidden">
          <table className="min-w-full divide-y divide-border/50">
            <thead className="bg-surface/80">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Code</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Name</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Symbol</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Exchange Rate</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Base</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {currencies.map((c: CurrencyResponse) => (
                <tr key={c.id} className="hover:bg-surface-hover/30">
                  <td className="px-6 py-4 text-sm font-medium text-accent">{c.code}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{c.name}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{c.symbol}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{c.exchangeRate}</td>
                  <td className="px-6 py-4 text-sm">
                    {c.isBaseCurrency ? (
                      <span className="bg-success/10 text-success ring-1 ring-success/20 rounded-md px-2 py-0.5 text-xs font-medium">Base</span>
                    ) : (
                      <span className="text-text-muted">—</span>
                    )}
                  </td>
                  <td className="px-6 py-4 text-sm">
                    {!c.isBaseCurrency && (
                      <button
                        onClick={() => deleteMutation.mutate(c.id)}
                        disabled={deleteMutation.isPending}
                        className="text-danger hover:text-danger disabled:text-text-muted"
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

function AiProvidersSection() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formData, setFormData] = useState({
    name: "",
    baseUrl: "https://api.openai.com/v1",
    apiKey: "",
    model: "gpt-4o-mini",
    maxTokens: 4096,
    temperature: 0.2,
    timeoutMs: 60000,
    authScheme: "BEARER",
    supportsNativeTools: true,
    isDefault: false,
    isActive: true,
  });
  const [testResult, setTestResult] = useState<{ id: string; result: import("@/lib/api/aiApi").ProviderTestResponse } | null>(null);

  const { data: providersData, isLoading } = useQuery({
    queryKey: ["admin-llm-providers"],
    queryFn: () => import("@/lib/api/aiApi").then((m) => m.aiApi.listProviders()),
  });

  const createMutation = useMutation({
    mutationFn: () => import("@/lib/api/aiApi").then((m) => m.aiApi.createProvider(formData)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-llm-providers"] });
      toast.success("Provider added");
      resetForm();
      setShowForm(false);
    },
    onError: (err: unknown) => {
      const message = err instanceof Error ? err.message : "Failed to add provider";
      toast.error(message);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: import("@/lib/api/aiApi").UpdateLlmProviderRequest }) =>
      import("@/lib/api/aiApi").then((m) => m.aiApi.updateProvider(id, data)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-llm-providers"] });
      toast.success("Provider updated");
      resetForm();
      setShowForm(false);
      setEditingId(null);
    },
    onError: (err: unknown) => {
      const message = err instanceof Error ? err.message : "Failed to update provider";
      toast.error(message);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => import("@/lib/api/aiApi").then((m) => m.aiApi.deleteProvider(id)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-llm-providers"] });
      toast.success("Provider deleted");
    },
    onError: (err: unknown) => {
      const message = err instanceof Error ? err.message : "Failed to delete provider";
      toast.error(message);
    },
  });

  const testMutation = useMutation({
    mutationFn: (id: string) => import("@/lib/api/aiApi").then((m) => m.aiApi.testProvider(id)),
    onSuccess: (data, id) => {
      setTestResult({ id, result: data.data! });
    },
    onError: (err: unknown) => {
      const message = err instanceof Error ? err.message : "Test failed";
      toast.error(message);
    },
  });

  const providers = Array.isArray(providersData?.data) ? providersData.data : [];

  function resetForm() {
    setFormData({
      name: "",
      baseUrl: "https://api.openai.com/v1",
      apiKey: "",
      model: "gpt-4o-mini",
      maxTokens: 4096,
      temperature: 0.2,
      timeoutMs: 60000,
      authScheme: "BEARER",
      supportsNativeTools: true,
      isDefault: false,
      isActive: true,
    });
  }

  function startEdit(provider: import("@/lib/api/aiApi").LlmProviderResponse) {
    setEditingId(provider.id);
    setFormData({
      name: provider.name,
      baseUrl: provider.baseUrl,
      apiKey: "",
      model: provider.model,
      maxTokens: provider.maxTokens,
      temperature: provider.temperature,
      timeoutMs: provider.timeoutMs,
      authScheme: provider.authScheme,
      supportsNativeTools: provider.supportsNativeTools,
      isDefault: provider.isDefault,
      isActive: provider.isActive,
    });
    setShowForm(true);
  }

  function handleSubmit() {
    if (editingId) {
      const payload: import("@/lib/api/aiApi").UpdateLlmProviderRequest = { ...formData };
      if (!payload.apiKey) delete (payload as Record<string, unknown>).apiKey;
      updateMutation.mutate({ id: editingId, data: payload });
    } else {
      createMutation.mutate();
    }
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-6">
      <div className="flex justify-end">
        <button
          onClick={() => { setShowForm(!showForm); if (showForm) { setEditingId(null); resetForm(); } }}
          className="flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={16} />
          {showForm ? "Cancel" : "Add Provider"}
        </button>
      </div>

      {showForm && (
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg space-y-4">
          <h3 className="text-lg font-semibold text-text-primary">
            {editingId ? "Edit Provider" : "New LLM Provider"}
          </h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-text-secondary">Name *</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="e.g., OpenAI Production"
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Base URL *</label>
              <input
                type="text"
                value={formData.baseUrl}
                onChange={(e) => setFormData({ ...formData, baseUrl: e.target.value })}
                placeholder="https://api.openai.com/v1"
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">API Key {editingId ? "(leave blank to keep existing)" : "*"}</label>
              <input
                type="password"
                value={formData.apiKey}
                onChange={(e) => setFormData({ ...formData, apiKey: e.target.value })}
                placeholder="sk-..."
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Model *</label>
              <input
                type="text"
                value={formData.model}
                onChange={(e) => setFormData({ ...formData, model: e.target.value })}
                placeholder="gpt-4o-mini"
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Auth Scheme</label>
              <select
                value={formData.authScheme}
                onChange={(e) => setFormData({ ...formData, authScheme: e.target.value })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none"
              >
                <option value="BEARER">BEARER</option>
                <option value="API_KEY">API_KEY</option>
                <option value="AZURE">AZURE</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Max Tokens</label>
              <input
                type="number"
                value={formData.maxTokens}
                onChange={(e) => setFormData({ ...formData, maxTokens: parseInt(e.target.value) || 4096 })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Temperature</label>
              <input
                type="number"
                step="0.1"
                min="0"
                max="2"
                value={formData.temperature}
                onChange={(e) => setFormData({ ...formData, temperature: parseFloat(e.target.value) || 0 })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Timeout (ms)</label>
              <input
                type="number"
                value={formData.timeoutMs}
                onChange={(e) => setFormData({ ...formData, timeoutMs: parseInt(e.target.value) || 60000 })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
              />
            </div>
          </div>
          <div className="flex items-center gap-4">
            <label className="flex items-center gap-2 text-sm text-text-secondary">
              <input
                type="checkbox"
                checked={formData.supportsNativeTools}
                onChange={(e) => setFormData({ ...formData, supportsNativeTools: e.target.checked })}
                className="rounded border-border"
              />
              Supports Native Tools
            </label>
            <label className="flex items-center gap-2 text-sm text-text-secondary">
              <input
                type="checkbox"
                checked={formData.isDefault}
                onChange={(e) => setFormData({ ...formData, isDefault: e.target.checked })}
                className="rounded border-border"
              />
              Default Provider
            </label>
            <label className="flex items-center gap-2 text-sm text-text-secondary">
              <input
                type="checkbox"
                checked={formData.isActive}
                onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                className="rounded border-border"
              />
              Active
            </label>
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleSubmit}
              disabled={!formData.name || !formData.baseUrl || (!editingId && !formData.apiKey) || !formData.model || isSubmitting}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
            >
              {isSubmitting ? (editingId ? "Updating..." : "Adding...") : (editingId ? "Update Provider" : "Add Provider")}
            </button>
            <button
              onClick={() => { setShowForm(false); setEditingId(null); resetForm(); }}
              className="rounded-md border border-border px-4 py-2 text-sm text-text-secondary hover:bg-surface-hover"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="text-center text-text-muted py-12">Loading providers...</div>
      ) : providers.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border py-12 text-center">
          <p className="text-text-muted">No LLM providers configured. Add your first provider.</p>
        </div>
      ) : (
        <div className="rounded-xl border border-border bg-surface/50 shadow-lg overflow-hidden">
          <table className="min-w-full divide-y divide-border/50">
            <thead className="bg-surface/80">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Name</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Base URL</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Model</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Auth</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Default</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Status</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {providers.map((p: import("@/lib/api/aiApi").LlmProviderResponse) => (
                <tr key={p.id} className="hover:bg-surface-hover/30">
                  <td className="px-6 py-4 text-sm font-medium text-accent">{p.name}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary max-w-xs truncate">{p.baseUrl}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{p.model}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{p.authScheme}</td>
                  <td className="px-6 py-4 text-sm">
                    {p.isDefault ? (
                      <span className="bg-success/10 text-success ring-1 ring-success/20 rounded-md px-2 py-0.5 text-xs font-medium">Default</span>
                    ) : (
                      <span className="text-text-muted">—</span>
                    )}
                  </td>
                  <td className="px-6 py-4 text-sm">
                    {p.isActive ? (
                      <span className="bg-success/10 text-success ring-1 ring-success/20 rounded-md px-2 py-0.5 text-xs font-medium">Active</span>
                    ) : (
                      <span className="bg-danger/10 text-danger ring-1 ring-danger/20 rounded-md px-2 py-0.5 text-xs font-medium">Inactive</span>
                    )}
                  </td>
                  <td className="px-6 py-4 text-sm">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => testMutation.mutate(p.id)}
                        disabled={testMutation.isPending}
                        className="text-accent hover:text-accent disabled:text-text-muted text-xs font-medium"
                      >
                        {testMutation.isPending && testResult?.id === p.id ? "Testing..." : "Test"}
                      </button>
                      <button
                        onClick={() => startEdit(p)}
                        className="text-text-secondary hover:text-text-primary"
                      >
                        <Settings2 size={14} />
                      </button>
                      <button
                        onClick={() => deleteMutation.mutate(p.id)}
                        disabled={deleteMutation.isPending}
                        className="text-danger hover:text-danger disabled:text-text-muted"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                    {testResult?.id === p.id && (
                      <div className="mt-1 text-xs">
                        {testResult.result.ok ? (
                          <span className="text-success">
                            OK — {testResult.result.latencyMs}ms ({testResult.result.modelEcho ?? "unknown model"})
                          </span>
                        ) : (
                          <span className="text-danger">
                            Failed — {testResult.result.error ?? "unknown error"}
                          </span>
                        )}
                      </div>
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
