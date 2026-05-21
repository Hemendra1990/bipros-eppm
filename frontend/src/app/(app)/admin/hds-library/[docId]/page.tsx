"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { hdsApi, type HdsVersion } from "@/lib/api/hdsApi";

export default function HdsVersionsListPage() {
  const params = useParams() as { docId: string };
  const [versions, setVersions] = useState<HdsVersion[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // listVersions returns INDEXED only; for admin we need ALL — adjust the API
    // For v1 keep it simple: combine indexed list with /admin/documents/:id
    // (which doesn't list versions).
    // PLACEHOLDER: until backend adds GET /v1/hds/admin/documents/:id/versions,
    // the indexed list is the best we have.
    hdsApi
      .listVersions()
      .then((all) => setVersions(all.filter((v) => v.hdsDocumentId === params.docId)))
      .catch((e) => setError(String(e)));
  }, [params.docId]);

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Versions</h1>
        <Link
          href={`/admin/hds-library/${params.docId}/upload`}
          className="px-3 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          + Upload version
        </Link>
      </div>

      {error && <div className="text-red-600 mb-4">{error}</div>}
      {!versions && !error && <div>Loading…</div>}
      {versions && versions.length === 0 && (
        <div className="text-gray-500">
          No versions yet. Upload the first revision.
        </div>
      )}

      {versions && versions.length > 0 && (
        <table className="w-full border-collapse">
          <thead>
            <tr className="text-left border-b">
              <th>Label</th>
              <th>Year</th>
              <th>Status</th>
              <th>Pages</th>
              <th>Chunks</th>
              <th>Uploaded</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {versions.map((v) => (
              <tr key={v.id} className="border-b">
                <td className="py-2 font-mono">{v.versionLabel}</td>
                <td>{v.revisionYear ?? "—"}</td>
                <td>
                  <StatusBadge status={v.status} pct={v.indexingProgressPct} />
                </td>
                <td>{v.pageCount ?? "—"}</td>
                <td>{v.chunkCount ?? "—"}</td>
                <td>{new Date(v.uploadedAt).toLocaleString()}</td>
                <td className="text-right">
                  <Link
                    href={`/admin/hds-library/${params.docId}/versions/${v.id}`}
                    className="text-blue-600 hover:underline"
                  >
                    Detail
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function StatusBadge({ status, pct }: { status: string; pct: number }) {
  const color =
    {
      INDEXED: "bg-green-100 text-green-800",
      FAILED: "bg-red-100 text-red-800",
      PENDING: "bg-gray-100 text-gray-700",
    }[status] || "bg-blue-100 text-blue-700";
  return (
    <span className={`inline-block px-2 py-0.5 text-xs rounded ${color}`}>
      {status === "INDEXED" || status === "FAILED" || status === "PENDING"
        ? status
        : `${status} · ${pct}%`}
    </span>
  );
}
