"use client";

import { LogOut, User } from "lucide-react";
import { useAuthStore } from "@/lib/state/store";
import { useRouter } from "next/navigation";

export function Header() {
  const { user, clearAuth } = useAuthStore();
  const router = useRouter();

  const handleLogout = () => {
    document.cookie = 'access_token=; path=/; max-age=0';
    clearAuth();
    router.push("/auth/login");
  };

  return (
    <header className="relative flex h-14 items-center justify-between border-b border-border-subtle bg-surface/50 glass-subtle px-6">
      {/* Top gradient accent line */}
      <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-accent via-accent/50 to-transparent opacity-30"></div>

      <div className="flex items-center gap-4">
        <h1 className="text-sm font-medium text-text-secondary">
          Enterprise Project Portfolio Management
        </h1>
      </div>
      <div className="flex items-center gap-4">
        {user && (
          <div className="flex items-center gap-2 text-sm text-text-secondary">
            <User size={16} />
            <span>{user.firstName ?? user.username}</span>
          </div>
        )}
        <button
          onClick={handleLogout}
          className="flex items-center gap-1 rounded px-2 py-1 text-sm text-text-muted hover:text-text-primary hover:bg-surface-hover transition-colors"
        >
          <LogOut size={16} />
          <span>Logout</span>
        </button>
      </div>
    </header>
  );
}
