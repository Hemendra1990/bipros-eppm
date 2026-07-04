"use client";

import { Suspense, useEffect } from "react";
import { useSearchParams } from "next/navigation";
import { useAuthStore } from "@/lib/state/store";

export default function DeeplinkPage() {
  return (
    <Suspense>
      <DeeplinkPageInner />
    </Suspense>
  );
}

function DeeplinkPageInner() {
  const searchParams = useSearchParams();

  useEffect(() => {
    const token = searchParams.get("auth");
    const projectId = searchParams.get("projectId");

    if (!token) {
      window.location.replace("/auth/login?error=missing_token");
      return;
    }

    const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

    fetch(`${API_BASE_URL}/v1/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error("Invalid token");
        return res.json();
      })
      .then((json) => {
        if (!json.data) throw new Error("No user data");

        useAuthStore.getState().setAuth(json.data, token, "");
        document.cookie = `access_token=${token}; path=/; max-age=3600; SameSite=Lax`;

        const target = projectId
          ? `/projects/${projectId}/gis-viewer`
          : "/";
        window.location.replace(target);
      })
      .catch(() => {
        window.location.replace("/auth/login?error=invalid_token");
      });
  }, [searchParams]);

  return null;
}
