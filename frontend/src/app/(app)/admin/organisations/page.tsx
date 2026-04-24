"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Trash2 } from "lucide-react";
import { organisationApi } from "@/lib/api/organisationApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import type {
  OrganisationRegistrationStatus,
  OrganisationResponse,
  OrganisationType,
} from "@/lib/types";

const ORG_TYPE_COLORS: Record<OrganisationType, string> = {
  EMPLOYER: "bg-indigo-500/20 text-indigo-300",
  SPV: "bg-blue-500/20 text-blue-300",
  PMC: "bg-success/20 text-success",
  EPC_CONTRACTOR: "bg-amber-500/20 text-warning",
  GOVERNMENT_AUDITOR: "bg-purple-500/10 text-purple-400",
  MAIN_CONTRACTOR: "bg-amber-500/20 text-warning",
  SUB_CONTRACTOR: "bg-orange-500/20 text-orange-300",
  CONSULTANT: "bg-teal-500/20 text-teal-300",
  IE: "bg-cyan-500/20 text-cyan-300",
  SUPPLIER: "bg-slate-500/20 text-slate-300",
};

const STATUS_COLORS: Record<OrganisationRegistrationStatus, string> = {
  ACTIVE: "text-success",
  SUSPENDED: "text-amber-300",
  CLOSED: "text-text-muted",
  PENDING_KYC: "text-warning",
};

export default function OrganisationsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [confirmId, setConfirmId] = useState<string | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ["organisations"],
    queryFn: () => organisationApi.listAll(),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => organisationApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organisations"] });
      setConfirmId(null);
    },
  });

  const orgs = data?.data ?? [];

  const columns: ColumnDef<OrganisationResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    { key: "name", label: "Name", sortable: true },
    {
      key: "organisationType",
      label: "Type",
      sortable: true,
      render: (value) => {
        const t = value as OrganisationType;
        return (
          <span className={`rounded px-2 py-0.5 text-xs font-medium ${ORG_TYPE_COLORS[t]}`}>
            {t.replace(/_/g, " ")}
          </span>
        );
      },
    },
    { key: "pan", label: "PAN" },
    { key: "gstin", label: "GSTIN" },
    { key: "contactPersonName", label: "Contact" },
    { key: "contactMobile", label: "Mobile" },
    { key: "city", label: "City" },
    {
      key: "registrationStatus",
      label: "Status",
      render: (v, row) => {
        const s = v as OrganisationRegistrationStatus | null;
        if (!s) return row.active ? <span className="text-success">Active</span> : <span className="text-text-muted">Inactive</span>;
        return <span className={STATUS_COLORS[s]}>{s.replace(/_/g, " ")}</span>;
      },
    },
    {
      key: "_actions",
      label: "",
      render: (_v, row) => (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            setConfirmId(row.id);
          }}
          className="rounded p-1 text-text-secondary hover:bg-surface-hover hover:text-danger"
          aria-label="Delete"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Contractor & Organisation Master"
        description="Main contractors, sub-contractors, consultants, IE firms, suppliers, and other project stakeholders."
        actions={
          <Link
            href="/admin/organisations/new"
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90"
          >
            New Organisation
          </Link>
        }
      />

      {isLoading && <div className="py-12 text-center text-text-muted">Loading organisations…</div>}

      {error && (
        <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">
          Failed to load organisations. Is the backend running?
        </div>
      )}

      {!isLoading && orgs.length === 0 && (
        <EmptyState
          title="No organisations yet"
          description="Add a contractor, consultant or supplier to get started."
        />
      )}

      {orgs.length > 0 && (
        <DataTable
          data={orgs}
          columns={columns}
          rowKey="id"
          onRowClick={(row) => router.push(`/admin/organisations/${row.id}`)}
          searchable
          searchPlaceholder="Search organisations…"
        />
      )}

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete organisation?"
        message="This removes the organisation and unlinks it from all projects."
        confirmLabel={deleteMutation.isPending ? "Deleting…" : "Delete"}
        onConfirm={() => confirmId && deleteMutation.mutate(confirmId)}
        onCancel={() => setConfirmId(null)}
      />
    </div>
  );
}
