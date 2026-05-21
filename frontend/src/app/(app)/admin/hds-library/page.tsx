"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { hdsApi, type HdsDocument } from "@/lib/api/hdsApi";

export default function HdsLibraryPage() {
  const [docs, setDocs] = useState<HdsDocument[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    hdsApi
      .listDocuments()
      .then(setDocs)
      .catch((e) => setError(String(e)));
  }, []);

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">HDS Library</h1>
        <Link
          href="/admin/hds-library/new"
          className="px-3 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          + New publication
        </Link>
      </div>

      {error && <div className="text-red-600 mb-4">{error}</div>}
      {!docs && !error && <div>Loading…</div>}

      {docs && docs.length === 0 && (
        <div className="text-gray-500">
          No HDS publications yet. Create one to get started.
        </div>
      )}

      {docs && docs.length > 0 && (
        <table className="w-full border-collapse">
          <thead>
            <tr className="text-left border-b">
              <th className="py-2">Short code</th>
              <th>Title</th>
              <th>Discipline</th>
              <th>Authority</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {docs.map((d) => (
              <tr key={d.id} className="border-b hover:bg-gray-50">
                <td className="py-2 font-mono">{d.shortCode}</td>
                <td>
                  <Link
                    href={`/admin/hds-library/${d.id}`}
                    className="text-blue-600 hover:underline"
                  >
                    {d.title}
                  </Link>
                </td>
                <td>{d.discipline}</td>
                <td>{d.issuingAuthority || "—"}</td>
                <td>{new Date(d.createdAt).toLocaleDateString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
