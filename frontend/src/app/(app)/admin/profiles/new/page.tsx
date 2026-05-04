"use client";

import { PageHeader } from "@/components/common/PageHeader";
import { ProfileForm } from "../ProfileForm";

export default function NewProfilePage() {
  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title="New Profile"
        description="Define a permission bundle and pick the actions users with this profile may perform."
      />
      <ProfileForm />
    </div>
  );
}
