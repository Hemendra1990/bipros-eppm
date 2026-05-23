"use client";

import { useAuth } from "@/lib/auth/useAuth";

interface DashboardHeroProps {
  projectName: string;
  projectCode?: string;
}

export function DashboardHero({ projectName, projectCode }: DashboardHeroProps) {
  const { user } = useAuth();
  const firstName = user?.firstName?.trim() || user?.username || "there";

  return (
    <header className="space-y-1">
      <div className="text-[11px] font-medium uppercase tracking-[0.18em] text-slate">
        Home <span className="opacity-40">/</span> Dashboard
      </div>
      <h1 className="font-display text-4xl font-semibold leading-tight tracking-tight text-charcoal">
        Dashboard
      </h1>
      <p className="text-sm text-slate">
        Welcome back, <span className="font-medium text-charcoal">{firstName}</span>
        <span className="mx-2 text-gold-deep">•</span>
        <span className="font-medium text-charcoal">{projectName}</span>
        {projectCode && (
          <span className="ml-2 rounded-md border border-hairline bg-ivory px-1.5 py-0.5 text-[10px] font-semibold tracking-wider text-gold-deep">
            {projectCode}
          </span>
        )}
      </p>
    </header>
  );
}
