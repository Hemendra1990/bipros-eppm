"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { hdsApi, type HdsDiscipline } from "@/lib/api/hdsApi";

const DISCIPLINES: HdsDiscipline[] = [
  "HIGHWAY",
  "BRIDGE",
  "GEOTECH",
  "PAVEMENT",
  "TRAFFIC",
  "DRAINAGE",
  "OTHER",
];

export default function NewHdsPublicationPage() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [shortCode, setShortCode] = useState("");
  const [discipline, setDiscipline] = useState<HdsDiscipline>("HIGHWAY");
  const [issuingAuthority, setIssuingAuthority] = useState("");
  const [country, setCountry] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const doc = await hdsApi.createDocument({
        title,
        shortCode,
        discipline,
        issuingAuthority: issuingAuthority || undefined,
        country: country || undefined,
        description: description || undefined,
      });
      router.push(`/admin/hds-library/${doc.id}`);
    } catch (e) {
      const err = e as { response?: { data?: { message?: string } } };
      setError(err?.response?.data?.message || String(e));
      setBusy(false);
    }
  };

  return (
    <div className="p-6 max-w-2xl">
      <h1 className="text-2xl font-semibold mb-6">New HDS Publication</h1>
      <div className="space-y-4">
        <label className="block">
          <span className="block text-sm font-medium">Title *</span>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="mt-1 w-full border rounded px-3 py-2"
            placeholder="Highway Design Standard, Volume 3 — Geometric Design"
          />
        </label>
        <label className="block">
          <span className="block text-sm font-medium">Short code *</span>
          <input
            value={shortCode}
            onChange={(e) => setShortCode(e.target.value)}
            className="mt-1 w-full border rounded px-3 py-2 font-mono"
            placeholder="HDS-V3"
          />
          <span className="text-xs text-gray-500">
            Used in citation strings. Must be unique.
          </span>
        </label>
        <label className="block">
          <span className="block text-sm font-medium">Discipline *</span>
          <select
            value={discipline}
            onChange={(e) => setDiscipline(e.target.value as HdsDiscipline)}
            className="mt-1 w-full border rounded px-3 py-2"
          >
            {DISCIPLINES.map((d) => (
              <option key={d}>{d}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="block text-sm font-medium">Issuing authority</span>
          <input
            value={issuingAuthority}
            onChange={(e) => setIssuingAuthority(e.target.value)}
            className="mt-1 w-full border rounded px-3 py-2"
            placeholder="Sultanate of Oman, MoT"
          />
        </label>
        <label className="block">
          <span className="block text-sm font-medium">Country (ISO-3166)</span>
          <input
            value={country}
            onChange={(e) => setCountry(e.target.value)}
            maxLength={2}
            className="mt-1 w-32 border rounded px-3 py-2 font-mono"
            placeholder="OM"
          />
        </label>
        <label className="block">
          <span className="block text-sm font-medium">Description</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className="mt-1 w-full border rounded px-3 py-2"
          />
        </label>
        {error && <div className="text-red-600">{error}</div>}
        <button
          onClick={submit}
          disabled={busy || !title || !shortCode}
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
        >
          {busy ? "Creating…" : "Create publication"}
        </button>
      </div>
    </div>
  );
}
