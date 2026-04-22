"use client";

import { useEffect, useState } from "react";
import { portfolioApi } from "@/lib/api/portfolioApi";
import type { PortfolioResponse } from "@/lib/types";
import { Plus, Trash2, Pencil } from "lucide-react";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

export default function PortfoliosPage() {
  const [portfolios, setPortfolios] = useState<PortfolioResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showNewForm, setShowNewForm] = useState(false);
  const [formData, setFormData] = useState({ code: "", name: "", description: "" });
  const [submitting, setSubmitting] = useState(false);
  const [editingPortfolio, setEditingPortfolio] = useState<PortfolioResponse | null>(null);
  const [editForm, setEditForm] = useState({ name: "", description: "" });

  useEffect(() => {
    loadPortfolios();
  }, []);

  const loadPortfolios = async () => {
    try {
      setLoading(true);
      const result = await portfolioApi.listPortfolios();
      const data = result.data;
      setPortfolios(Array.isArray(data) ? data : (data as any)?.content ?? []);
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to load portfolios"));
    } finally {
      setLoading(false);
    }
  };

  const handleCreatePortfolio = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.code || !formData.name) {
      setError("Code and name are required");
      return;
    }
    if (portfolios.some((p) => p.code.toLowerCase() === formData.code.trim().toLowerCase())) {
      setError(`Portfolio code "${formData.code.trim()}" already exists. Please use a different code.`);
      return;
    }

    try {
      setSubmitting(true);
      const result = await portfolioApi.createPortfolio(formData);
      if (result.data) {
        setPortfolios([...portfolios, result.data]);
        setFormData({ code: "", name: "", description: "" });
        setShowNewForm(false);
        setError("");
      } else if (result.error) {
        setError(result.error.message);
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create portfolio"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeletePortfolio = async (id: string) => {
    if (!confirm("Are you sure you want to delete this portfolio?")) return;

    try {
      await portfolioApi.deletePortfolio(id);
      setPortfolios(portfolios.filter((p) => p.id !== id));
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete portfolio"));
    }
  };

  const handleEditPortfolio = (portfolio: PortfolioResponse) => {
    setEditingPortfolio(portfolio);
    setEditForm({ name: portfolio.name, description: portfolio.description || "" });
  };

  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingPortfolio || !editForm.name.trim()) {
      setError("Name is required");
      return;
    }

    try {
      setSubmitting(true);
      const result = await portfolioApi.updatePortfolio(editingPortfolio.id, {
        name: editForm.name,
        description: editForm.description,
      });
      if (result.data) {
        setPortfolios(portfolios.map((p) => (p.id === editingPortfolio.id ? result.data! : p)));
        setEditingPortfolio(null);
        setError("");
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to update portfolio"));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div className="text-center text-text-muted">Loading portfolios...</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-text-primary">Portfolios</h1>
        <button
          onClick={() => setShowNewForm(!showNewForm)}
          className="flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={18} />
          New Portfolio
        </button>
      </div>

      <TabTip
        title="Portfolio Management"
        description="Group related projects into portfolios for high-level oversight. Track overall portfolio health, budget, and progress across multiple projects."
      />

      {error && (
        <div className="rounded-md bg-danger/10 p-3 text-sm text-danger">
          {error}
        </div>
      )}

      {showNewForm && (
        <form
          onSubmit={handleCreatePortfolio}
          className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg"
        >
          <h2 className="mb-4 text-lg font-semibold text-text-primary">Create New Portfolio</h2>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text-secondary">Code</label>
              <input
                type="text"
                required
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted shadow-sm focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                placeholder="e.g., PORT-001"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Name</label>
              <input
                type="text"
                required
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted shadow-sm focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                placeholder="Portfolio name"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Description</label>
              <textarea
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted shadow-sm focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                placeholder="Portfolio description (optional)"
                rows={3}
              />
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={submitting}
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                {submitting ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => setShowNewForm(false)}
                className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
              >
                Cancel
              </button>
            </div>
          </div>
        </form>
      )}

      {portfolios.length === 0 ? (
        <div className="rounded-xl border border-border bg-surface/50 p-8 text-center shadow-lg">
          <p className="text-text-muted">No portfolios yet. Create one to get started.</p>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {portfolios.map((portfolio) => (
            <a
              key={portfolio.id}
              href={`/portfolios/${portfolio.id}`}
              className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg hover:shadow-xl transition-shadow"
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <h3 className="font-semibold text-text-primary">{portfolio.name}</h3>
                  <p className="text-sm text-text-secondary">{portfolio.code}</p>
                  {portfolio.description && (
                    <p className="mt-2 text-sm text-text-secondary line-clamp-2">
                      {portfolio.description}
                    </p>
                  )}
                  <p className="mt-4 text-xs text-text-muted">
                    {portfolio.projectCount} project{portfolio.projectCount !== 1 ? "s" : ""}
                  </p>
                </div>
                <div className="flex gap-1">
                  <button
                    onClick={(e) => {
                      e.preventDefault();
                      handleEditPortfolio(portfolio);
                    }}
                    className="rounded p-1 text-text-muted hover:bg-accent-hover/10 hover:text-accent"
                    title="Edit portfolio"
                  >
                    <Pencil size={16} />
                  </button>
                  <button
                    onClick={(e) => {
                      e.preventDefault();
                      handleDeletePortfolio(portfolio.id);
                    }}
                    className="rounded p-1 text-text-muted hover:bg-danger/10 hover:text-danger"
                    title="Delete portfolio"
                  >
                    <Trash2 size={18} />
                  </button>
                </div>
              </div>
            </a>
          ))}
        </div>
      )}

      {/* Edit Portfolio Modal */}
      {editingPortfolio && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
          <form
            onSubmit={handleSaveEdit}
            className="w-full max-w-md rounded-xl border border-border bg-surface p-6 shadow-xl"
          >
            <h2 className="mb-4 text-lg font-semibold text-text-primary">
              Edit Portfolio: {editingPortfolio.code}
            </h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">Name</label>
                <input
                  type="text"
                  required
                  value={editForm.name}
                  onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">Description</label>
                <textarea
                  value={editForm.description}
                  onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  rows={3}
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setEditingPortfolio(null)}
                  className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
                >
                  {submitting ? "Saving..." : "Save Changes"}
                </button>
              </div>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
