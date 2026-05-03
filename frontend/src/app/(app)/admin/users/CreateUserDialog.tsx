"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";

import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { profileApi } from "@/lib/api/profileApi";
import { userApi, type CreateUserRequest } from "@/lib/api/userApi";
import { getErrorMessage } from "@/lib/utils/error";

interface CreateUserDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CreateUserDialog({ open, onOpenChange }: CreateUserDialogProps) {
  const queryClient = useQueryClient();

  const [form, setForm] = useState<CreateUserRequest>({
    username: "",
    email: "",
    password: "",
    firstName: "",
    lastName: "",
    profileId: null,
    enabled: true,
  });
  const [error, setError] = useState<string | null>(null);

  const set = <K extends keyof CreateUserRequest>(key: K, value: CreateUserRequest[K]) =>
    setForm((s) => ({ ...s, [key]: value }));

  const { data: profilesResponse } = useQuery({
    queryKey: ["profiles"],
    queryFn: () => profileApi.listProfiles(),
    enabled: open,
  });
  const profiles = profilesResponse?.data ?? [];

  const reset = () => {
    setForm({
      username: "",
      email: "",
      password: "",
      firstName: "",
      lastName: "",
      profileId: null,
      enabled: true,
    });
    setError(null);
  };

  const close = () => {
    reset();
    onOpenChange(false);
  };

  const mutation = useMutation({
    mutationFn: () => userApi.createUser(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success("User created");
      close();
    },
    onError: (err: unknown) => setError(getErrorMessage(err, "Failed to create user")),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.username.trim() || !form.email.trim() || !form.password.trim()) {
      setError("Username, email and password are required");
      return;
    }
    mutation.mutate();
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg bg-surface text-text-primary">
        <DialogHeader>
          <DialogTitle>Create user</DialogTitle>
        </DialogHeader>
        <form onSubmit={submit}>
          <DialogBody>
            {error && (
              <div className="mb-3 rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
                {error}
              </div>
            )}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <label className="block text-sm font-medium text-text-secondary">Username *</label>
                <input
                  required
                  value={form.username}
                  onChange={(e) => set("username", e.target.value)}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
                />
              </div>
              <div className="sm:col-span-2">
                <label className="block text-sm font-medium text-text-secondary">Email *</label>
                <input
                  required
                  type="email"
                  value={form.email}
                  onChange={(e) => set("email", e.target.value)}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
                />
              </div>
              <div className="sm:col-span-2">
                <label className="block text-sm font-medium text-text-secondary">Password *</label>
                <input
                  required
                  type="password"
                  minLength={6}
                  value={form.password}
                  onChange={(e) => set("password", e.target.value)}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">First name</label>
                <input
                  value={form.firstName ?? ""}
                  onChange={(e) => set("firstName", e.target.value)}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">Last name</label>
                <input
                  value={form.lastName ?? ""}
                  onChange={(e) => set("lastName", e.target.value)}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
                />
              </div>
              <div className="sm:col-span-2">
                <label className="block text-sm font-medium text-text-secondary">Profile</label>
                <select
                  value={form.profileId ?? ""}
                  onChange={(e) => set("profileId", e.target.value || null)}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
                >
                  <option value="">— No profile —</option>
                  {profiles.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}{p.systemDefault ? " (default)" : ""}
                    </option>
                  ))}
                </select>
                <p className="mt-1 text-xs text-text-muted">
                  Determines what this user can do. Pick later from the user list if unsure.
                </p>
              </div>
            </div>
          </DialogBody>
          <DialogFooter>
            <button
              type="button"
              onClick={close}
              className="rounded-md border border-border bg-surface-hover px-3 py-1.5 text-sm font-medium text-text-secondary hover:bg-surface"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="rounded-md bg-accent px-3 py-1.5 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
            >
              {mutation.isPending ? "Creating…" : "Create user"}
            </button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
