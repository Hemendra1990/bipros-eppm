"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";

import { profileApi } from "@/lib/api/profileApi";
import { getErrorMessage } from "@/lib/utils/error";
import type { PermissionDescriptor, ProfileResponse } from "@/lib/types";

const LEGACY_ROLES = [
  "ADMIN",
  "EXECUTIVE",
  "PMO",
  "FINANCE",
  "PROJECT_MANAGER",
  "SCHEDULER",
  "RESOURCE_MANAGER",
  "TEAM_MEMBER",
  "CLIENT",
  "VIEWER",
  "FOREMAN",
  "SITE_ENGINEER",
  "HSE_OFFICER",
];

interface ProfileFormProps {
  initial?: ProfileResponse;
}

export function ProfileForm({ initial }: ProfileFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const isEdit = !!initial;

  const [code, setCode] = useState(initial?.code ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [legacyRoleName, setLegacyRoleName] = useState(initial?.legacyRoleName ?? "VIEWER");
  const [permissions, setPermissions] = useState<Set<string>>(
    new Set(initial?.permissions ?? []),
  );
  const [error, setError] = useState<string | null>(null);

  const { data: catalog } = useQuery({
    queryKey: ["permissions-catalog"],
    queryFn: () => profileApi.listPermissions(),
    staleTime: 1000 * 60 * 60,
  });

  const grouped = useMemo(() => {
    const all: PermissionDescriptor[] = catalog?.data ?? [];
    const map = new Map<string, PermissionDescriptor[]>();
    for (const p of all) {
      if (!map.has(p.module)) map.set(p.module, []);
      map.get(p.module)!.push(p);
    }
    return Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b));
  }, [catalog]);

  const togglePermission = (codeKey: string) =>
    setPermissions((prev) => {
      const next = new Set(prev);
      if (next.has(codeKey)) next.delete(codeKey);
      else next.add(codeKey);
      return next;
    });

  const toggleModule = (perms: PermissionDescriptor[], select: boolean) =>
    setPermissions((prev) => {
      const next = new Set(prev);
      perms.forEach((p) => (select ? next.add(p.code) : next.delete(p.code)));
      return next;
    });

  const toggleAll = (select: boolean) => {
    setPermissions(
      select
        ? new Set((catalog?.data ?? []).map((p) => p.code))
        : new Set(),
    );
  };

  const mutation = useMutation({
    mutationFn: () => {
      const body = {
        name,
        description,
        legacyRoleName,
        permissions: Array.from(permissions),
      };
      if (isEdit) {
        return profileApi.updateProfile(initial!.id, body);
      }
      return profileApi.createProfile({ ...body, code });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profiles"] });
      toast.success(isEdit ? "Profile updated" : "Profile created");
      router.push("/admin/profiles");
    },
    onError: (err: unknown) => setError(getErrorMessage(err, "Save failed")),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!isEdit && !code.trim()) {
      setError("Code is required");
      return;
    }
    if (!name.trim()) {
      setError("Name is required");
      return;
    }
    mutation.mutate();
  };

  const totalPermissions = catalog?.data?.length ?? 0;

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {error && (
        <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </div>
      )}

      <div className="space-y-4 rounded-lg border border-border bg-surface p-6">
        <h2 className="text-sm font-semibold text-text-primary">Profile details</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Code *</label>
            <input
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase().replace(/\s+/g, "_"))}
              required
              disabled={isEdit}
              placeholder="e.g. SAFETY_OFFICER"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary uppercase focus:border-accent focus:outline-none disabled:opacity-60"
            />
            <p className="mt-1 text-xs text-text-muted">Unique, immutable. Letters, digits, underscores only.</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Name *</label>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              placeholder="e.g. Site Safety Officer"
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            />
          </div>
        </div>
        <div>
          <label className="block text-sm font-medium text-text-secondary">Description</label>
          <textarea
            value={description ?? ""}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-text-secondary">Maps to legacy role *</label>
          <select
            value={legacyRoleName}
            onChange={(e) => setLegacyRoleName(e.target.value)}
            className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
          >
            {LEGACY_ROLES.map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </select>
          <p className="mt-1 text-xs text-text-muted">
            When a user is assigned this profile, this role is added to their JWT so existing
            role-based authorization keeps working.
          </p>
        </div>
      </div>

      <div className="space-y-4 rounded-lg border border-border bg-surface p-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-text-primary">Permissions</h2>
            <p className="text-xs text-text-muted">
              {permissions.size} of {totalPermissions} selected
            </p>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => toggleAll(true)}
              className="rounded border border-border bg-surface-hover px-2 py-1 text-xs hover:bg-surface-active"
            >
              Select all
            </button>
            <button
              type="button"
              onClick={() => toggleAll(false)}
              className="rounded border border-border bg-surface-hover px-2 py-1 text-xs hover:bg-surface-active"
            >
              Clear all
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {grouped.map(([moduleName, perms]) => {
            const allSelected = perms.every((p) => permissions.has(p.code));
            const someSelected = perms.some((p) => permissions.has(p.code));
            return (
              <div key={moduleName} className="rounded-md border border-border bg-surface-hover p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-text-secondary">
                    {moduleName.replace(/_/g, " ")}
                  </span>
                  <button
                    type="button"
                    onClick={() => toggleModule(perms, !allSelected)}
                    className={`text-[11px] font-medium ${
                      allSelected ? "text-accent" : someSelected ? "text-warning" : "text-text-muted"
                    } hover:underline`}
                  >
                    {allSelected ? "Unselect all" : "Select all"}
                  </button>
                </div>
                <div className="space-y-1.5">
                  {perms.map((p) => (
                    <label
                      key={p.code}
                      className="flex cursor-pointer items-start gap-2 rounded px-1.5 py-1 text-xs hover:bg-surface-active"
                    >
                      <input
                        type="checkbox"
                        checked={permissions.has(p.code)}
                        onChange={() => togglePermission(p.code)}
                        className="mt-0.5 h-3.5 w-3.5 cursor-pointer rounded border-border text-accent focus:ring-accent"
                      />
                      <span className="flex-1">
                        <span className="block font-medium text-text-primary">{p.label}</span>
                        <span className="block font-mono text-[10px] text-text-muted">{p.code}</span>
                      </span>
                    </label>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <div className="flex justify-end gap-3">
        <button
          type="button"
          onClick={() => router.push("/admin/profiles")}
          className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
        >
          {mutation.isPending ? "Saving…" : isEdit ? "Save changes" : "Create profile"}
        </button>
      </div>
    </form>
  );
}
