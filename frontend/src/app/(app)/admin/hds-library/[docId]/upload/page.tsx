"use client";

import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { hdsApi } from "@/lib/api/hdsApi";

export default function HdsUploadPage() {
  const router = useRouter();
  const params = useParams() as { docId: string };

  const [versionLabel, setVersionLabel] = useState("");
  const [year, setYear] = useState<number | "">("");
  const [file, setFile] = useState<File | null>(null);
  const [pct, setPct] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const upload = async () => {
    if (!file || !versionLabel) return;
    setBusy(true);
    setError(null);
    setPct(0);
    try {
      const ver = await hdsApi.uploadVersion(
        params.docId,
        versionLabel,
        year === "" ? undefined : year,
        file,
        setPct
      );
      router.push(`/admin/hds-library/${params.docId}/versions/${ver.id}`);
    } catch (e) {
      const err = e as {
        response?: {
          status?: number;
          data?: { message?: string; data?: { versionLabel?: string } };
        };
      };
      const status = err?.response?.status;
      if (status === 409) {
        const existing = err.response?.data?.data;
        setError(
          `Identical file already uploaded as version ${existing?.versionLabel}.`
        );
      } else {
        setError(err?.response?.data?.message || String(e));
      }
      setBusy(false);
    }
  };

  return (
    <div className="p-6 max-w-2xl">
      <h1 className="text-2xl font-semibold mb-6">Upload HDS Version</h1>
      <div className="space-y-4">
        <label className="block">
          <span className="block text-sm font-medium">Version label *</span>
          <input
            value={versionLabel}
            onChange={(e) => setVersionLabel(e.target.value)}
            className="mt-1 w-full border rounded px-3 py-2 font-mono"
            placeholder="Rev 2.1"
          />
        </label>
        <label className="block">
          <span className="block text-sm font-medium">Revision year</span>
          <input
            type="number"
            value={year}
            onChange={(e) =>
              setYear(e.target.value === "" ? "" : Number(e.target.value))
            }
            className="mt-1 w-32 border rounded px-3 py-2"
            placeholder="2024"
          />
        </label>
        <label className="block">
          <span className="block text-sm font-medium">PDF *</span>
          <input
            type="file"
            accept="application/pdf"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          {file && (
            <span className="ml-2 text-sm text-gray-600">
              {(file.size / 1024 / 1024).toFixed(1)} MB
            </span>
          )}
        </label>
        {busy && (
          <div>
            <div className="text-sm text-gray-600">Uploading… {pct}%</div>
            <div className="w-full h-2 bg-gray-200 rounded">
              <div
                className="h-2 bg-blue-600 rounded"
                style={{ width: `${pct}%` }}
              />
            </div>
          </div>
        )}
        {error && <div className="text-red-600">{error}</div>}
        <button
          onClick={upload}
          disabled={busy || !file || !versionLabel}
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
        >
          {busy ? "Uploading…" : "Upload"}
        </button>
      </div>
    </div>
  );
}
