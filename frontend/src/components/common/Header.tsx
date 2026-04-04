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
    <header className="flex h-14 items-center justify-between border-b border-gray-200 bg-white px-6">
      <div className="flex items-center gap-4">
        <h1 className="text-sm font-medium text-gray-500">
          Enterprise Project Portfolio Management
        </h1>
      </div>
      <div className="flex items-center gap-4">
        {user && (
          <div className="flex items-center gap-2 text-sm text-gray-700">
            <User size={16} />
            <span>{user.firstName ?? user.username}</span>
          </div>
        )}
        <button
          onClick={handleLogout}
          className="flex items-center gap-1 rounded px-2 py-1 text-sm text-gray-500 hover:bg-gray-100 hover:text-gray-700"
        >
          <LogOut size={16} />
          <span>Logout</span>
        </button>
      </div>
    </header>
  );
}
