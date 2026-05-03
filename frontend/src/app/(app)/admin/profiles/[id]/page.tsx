"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";

import { profileApi } from "@/lib/api/profileApi";
import { PageHeader } from "@/components/common/PageHeader";
import { ProfileForm } from "../ProfileForm";
import { getErrorMessage } from "@/lib/utils/error";

export default function EditProfilePage() {
  const params = useParams<{ id: string }>();
  const id = params?.id;

  const { data, isLoading, error } = useQuery({
    queryKey: ["profile", id],
    queryFn: () => profileApi.getProfile(id!),
    enabled: !!id,
  });

  const profile = data?.data;

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title={profile ? `Edit Profile — ${profile.name}` : "Edit Profile"}
        description={
          profile?.systemDefault
            ? "This is a system-default profile. You can edit its name, description, and permissions but it cannot be deleted."
            : "Adjust permissions, name, or description for this profile."
        }
      />
      {isLoading && <div className="text-text-secondary">Loading…</div>}
      {error && (
        <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">
          {getErrorMessage(error, "Failed to load profile")}
        </div>
      )}
      {profile && <ProfileForm initial={profile} />}
    </div>
  );
}
