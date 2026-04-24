"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { organisationApi } from "@/lib/api/organisationApi";
import { PageHeader } from "@/components/common/PageHeader";
import type {
  CreateOrganisationRequest,
  OrganisationRegistrationStatus,
  OrganisationType,
} from "@/lib/types";

const ORG_TYPE_OPTIONS: { value: OrganisationType; label: string }[] = [
  { value: "MAIN_CONTRACTOR", label: "Main Contractor" },
  { value: "SUB_CONTRACTOR", label: "Sub-Contractor" },
  { value: "CONSULTANT", label: "Consultant" },
  { value: "IE", label: "Independent Engineer" },
  { value: "SUPPLIER", label: "Supplier" },
  { value: "EPC_CONTRACTOR", label: "EPC Contractor" },
  { value: "EMPLOYER", label: "Employer" },
  { value: "SPV", label: "SPV" },
  { value: "PMC", label: "PMC" },
  { value: "GOVERNMENT_AUDITOR", label: "Government Auditor" },
];

const STATUS_OPTIONS: OrganisationRegistrationStatus[] = [
  "ACTIVE",
  "SUSPENDED",
  "CLOSED",
  "PENDING_KYC",
];

export default function NewOrganisationPage() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [state, setState] = useState<CreateOrganisationRequest>({
    name: "",
    organisationType: "MAIN_CONTRACTOR",
    registrationStatus: "ACTIVE",
    active: true,
  });
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (body: CreateOrganisationRequest) => organisationApi.create(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organisations"] });
      router.push("/admin/organisations");
    },
    onError: (err: { response?: { data?: { error?: { message?: string } } } }) => {
      setError(err.response?.data?.error?.message ?? "Failed to create organisation");
    },
  });

  const set = <K extends keyof CreateOrganisationRequest>(
    key: K,
    value: CreateOrganisationRequest[K],
  ) => setState((s) => ({ ...s, [key]: value }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!state.name.trim()) {
      setError("Name is required");
      return;
    }
    mutation.mutate(state);
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <PageHeader
        title="New Organisation"
        description="Register a contractor, consultant, IE, or supplier."
      />

      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg border border-border bg-surface p-6">
        {error && (
          <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Code (optional)</label>
            <input
              value={state.code ?? ""}
              onChange={(e) => set("code", e.target.value)}
              placeholder="Auto CONT-NNN"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Name *</label>
            <input
              value={state.name}
              onChange={(e) => set("name", e.target.value)}
              required
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Type *</label>
            <select
              value={state.organisationType}
              onChange={(e) => set("organisationType", e.target.value as OrganisationType)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              {ORG_TYPE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Short Name</label>
            <input
              value={state.shortName ?? ""}
              onChange={(e) => set("shortName", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Registration Status</label>
            <select
              value={state.registrationStatus ?? "ACTIVE"}
              onChange={(e) =>
                set("registrationStatus", e.target.value as OrganisationRegistrationStatus)
              }
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s.replace("_", " ")}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">PAN</label>
            <input
              value={state.pan ?? ""}
              onChange={(e) => set("pan", e.target.value.toUpperCase())}
              placeholder="AAAAA0000A"
              maxLength={10}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 uppercase text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">GSTIN</label>
            <input
              value={state.gstin ?? ""}
              onChange={(e) => set("gstin", e.target.value.toUpperCase())}
              placeholder="22AAAAA0000A1Z5"
              maxLength={15}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 uppercase text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Registration No.</label>
            <input
              value={state.registrationNumber ?? ""}
              onChange={(e) => set("registrationNumber", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">Registered Address</label>
          <textarea
            value={state.addressLine ?? ""}
            onChange={(e) => set("addressLine", e.target.value)}
            rows={2}
            className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">City</label>
            <input
              value={state.city ?? ""}
              onChange={(e) => set("city", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">State</label>
            <input
              value={state.state ?? ""}
              onChange={(e) => set("state", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">PIN Code</label>
            <input
              value={state.pincode ?? ""}
              onChange={(e) => set("pincode", e.target.value)}
              placeholder="6 digits"
              maxLength={6}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Contact Person</label>
            <input
              value={state.contactPersonName ?? ""}
              onChange={(e) => set("contactPersonName", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Mobile</label>
            <input
              value={state.contactMobile ?? ""}
              onChange={(e) => set("contactMobile", e.target.value)}
              placeholder="+91 98765 43210"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Email</label>
            <input
              type="email"
              value={state.contactEmail ?? ""}
              onChange={(e) => set("contactEmail", e.target.value)}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={() => router.push("/admin/organisations")}
            className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
          >
            {mutation.isPending ? "Saving…" : "Create Organisation"}
          </button>
        </div>
      </form>
    </div>
  );
}
