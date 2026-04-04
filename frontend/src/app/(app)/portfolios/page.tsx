"use client";

import { useEffect, useState } from "react";
import { portfolioApi } from "@/lib/api/portfolioApi";
import type { PortfolioResponse } from "@/lib/types";
import { Plus, Trash2 } from "lucide-react";

export default function PortfoliosPage() {
  const [portfolios, setPortfolios] = useState<PortfolioResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showNewForm, setShowNewForm] = useState(false);
  const [formData, setFormData] = useState({ code: "", name: "", description: "" });
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    loadPortfolios();
  }, []);

  const loadPortfolios = async () => {
    try {
      setLoading(true);
      const result = await portfolioApi.listPortfolios();
      const data = result.data;
      setPortfolios(Array.isArray(data) ? data : (data as any)?.content ?? []);
    } catch {
      setError("Failed to load portfolios");
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
    } catch {
      setError("Failed to create portfolio");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeletePortfolio = async (id: string) => {
    if (!confirm("Are you sure you want to delete this portfolio?")) return;

    try {
      await portfolioApi.deletePortfolio(id);
      setPortfolios(portfolios.filter((p) => p.id !== id));
    } catch {
      setError("Failed to delete portfolio");
    }
  };

  if (loading) {
    return <div className="text-center text-gray-500">Loading portfolios...</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-gray-900">Portfolios</h1>
        <button
          onClick={() => setShowNewForm(!showNewForm)}
          className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          <Plus size={18} />
          New Portfolio
        </button>
      </div>

      {error && (
        <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {showNewForm && (
        <form
          onSubmit={handleCreatePortfolio}
          className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
        >
          <h2 className="mb-4 text-lg font-semibold text-gray-900">Create New Portfolio</h2>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700">Code</label>
              <input
                type="text"
                required
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="e.g., PORT-001"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Name</label>
              <input
                type="text"
                required
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Portfolio name"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Description</label>
              <textarea
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Portfolio description (optional)"
                rows={3}
              />
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={submitting}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {submitting ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => setShowNewForm(false)}
                className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </div>
        </form>
      )}

      {portfolios.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white p-8 text-center">
          <p className="text-gray-500">No portfolios yet. Create one to get started.</p>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {portfolios.map((portfolio) => (
            <a
              key={portfolio.id}
              href={`/portfolios/${portfolio.id}`}
              className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm hover:shadow-md transition-shadow"
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <h3 className="font-semibold text-gray-900">{portfolio.name}</h3>
                  <p className="text-sm text-gray-500">{portfolio.code}</p>
                  {portfolio.description && (
                    <p className="mt-2 text-sm text-gray-600 line-clamp-2">
                      {portfolio.description}
                    </p>
                  )}
                  <p className="mt-4 text-xs text-gray-500">
                    {portfolio.projectCount} project{portfolio.projectCount !== 1 ? "s" : ""}
                  </p>
                </div>
                <button
                  onClick={(e) => {
                    e.preventDefault();
                    handleDeletePortfolio(portfolio.id);
                  }}
                  className="rounded p-1 text-gray-400 hover:bg-red-50 hover:text-red-600"
                >
                  <Trash2 size={18} />
                </button>
              </div>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}
