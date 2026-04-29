"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { labourMasterApi } from "@/lib/api/labourMasterApi";
import { WorkerDetailModal } from "@/components/labour-master";

export default function DesignationDetailPage() {
  const params = useParams<{ code: string }>();
  const router = useRouter();
  const code = params?.code;

  const q = useQuery({
    queryKey: ["labour-designation", code],
    queryFn: () => labourMasterApi.designations.getByCode(code!),
    enabled: !!code,
  });

  if (q.isLoading) return <p>Loading…</p>;
  if (q.isError || !q.data?.data) return <p className="text-red-700">Not found.</p>;
  return (
    <WorkerDetailModal
      designation={q.data.data}
      onClose={() => router.push("/labour-master/cards")}
    />
  );
}
