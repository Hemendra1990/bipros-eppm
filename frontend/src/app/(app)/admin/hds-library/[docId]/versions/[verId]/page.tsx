"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { hdsApi, type HdsVersionDetail } from "@/lib/api/hdsApi";

export default function HdsVersionDetailPage() {
  const params = useParams() as { docId: string; verId: string };
  const [detail, setDetail] = useState<HdsVersionDetail | null>(null);
  const [progress, setProgress] = useState<{
    stage: string;
    pct: number;
    msg: string;
  } | null>(null);

  useEffect(() => {
    hdsApi.getVersion(params.verId).then(setDetail);
    const sub = hdsApi.subscribeProgress(params.verId, (ev) => {
      setProgress({ stage: ev.stage, pct: ev.progressPct, msg: ev.message });
      if (ev.stage === "COMPLETE" || ev.stage === "FAILED") {
        hdsApi.getVersion(params.verId).then(setDetail);
      }
    });
    return () => sub.close();
  }, [params.verId]);

  if (!detail) return <div className="p-6">Loading…</div>;
  const v = detail.version;

  return (
    <div className="p-6 max-w-3xl">
      <h1 className="text-2xl font-semibold mb-2">{v.versionLabel}</h1>
      <div className="text-sm text-gray-600 mb-4">
        {v.fileName} · {((v.fileSizeBytes ?? 0) / 1024 / 1024).toFixed(1)} MB ·{" "}
        {v.pageCount ?? "—"} pages
      </div>

      <div className="mb-4">
        <div className="font-medium">
          Status: {v.status} ({v.indexingProgressPct}%)
        </div>
        {progress && progress.stage !== "COMPLETE" && (
          <>
            <div className="text-sm text-gray-600">{progress.msg}</div>
            <div className="w-full h-2 bg-gray-200 rounded mt-1">
              <div
                className="h-2 bg-blue-600 rounded"
                style={{ width: `${progress.pct}%` }}
              />
            </div>
          </>
        )}
        {v.status === "FAILED" && detail.indexingError && (
          <div className="mt-2 p-3 bg-red-50 text-red-700 rounded text-sm">
            <div className="font-medium">Error</div>
            <pre className="whitespace-pre-wrap text-xs mt-1">
              {detail.indexingError}
            </pre>
            <button
              onClick={() =>
                hdsApi.retryVersion(v.id).then(() => location.reload())
              }
              className="mt-2 px-3 py-1 bg-red-600 text-white rounded"
            >
              Retry
            </button>
          </div>
        )}
        {v.status === "INDEXED" && (
          <div className="mt-2 text-green-700">
            Indexed {v.chunkCount} chunks at {v.indexedAt}
          </div>
        )}
      </div>

      <button
        onClick={async () => {
          if (confirm("Delete this version and its chunks?")) {
            await hdsApi.deleteVersion(v.id);
            location.href = `/admin/hds-library/${params.docId}`;
          }
        }}
        className="px-3 py-1 bg-gray-200 text-gray-700 rounded hover:bg-red-100 hover:text-red-700"
      >
        Delete version
      </button>
    </div>
  );
}
