"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { projectApi } from "@/lib/api/projectApi";
import { useAppStore } from "@/lib/state/store";

type Props = {
  title?: string;
  /** Where to navigate after a project is picked. Defaults to the current pathname. */
  redirectBasePath?: string;
};

export function ProjectPickerEmpty({ title = "Select a project", redirectBasePath }: Props) {
  const router = useRouter();
  const setStored = useAppStore((s) => s.setCurrentProjectId);
  const projects = useQuery({
    queryKey: ["labour-master-project-picker"],
    queryFn: () => projectApi.listProjects(0, 50),
  });

  const list = projects.data?.data?.content ?? [];

  const select = (id: string) => {
    setStored(id);
    const target = `${redirectBasePath ?? "/labour-master"}?projectId=${id}`;
    router.push(target);
  };

  return (
    <section className="rounded-xl border border-hairline bg-paper p-6 max-w-2xl">
      <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-slate">Project</div>
      <h2 className="mt-1 font-display text-[20px] font-semibold text-charcoal">{title}</h2>
      <p className="mt-1 text-[13px] text-slate">Pick a project to view its labour deployments.</p>

      {projects.isLoading && (
        <p className="mt-4 text-[13px] text-slate">Loading projects…</p>
      )}
      {projects.isError && (
        <p className="mt-4 text-[13px] text-burgundy">Failed to load projects.</p>
      )}
      {!projects.isLoading && !projects.isError && list.length === 0 && (
        <p className="mt-4 text-[13px] text-slate">
          No projects yet. Create one under{" "}
          <Link className="underline text-gold-deep" href="/projects">Projects</Link> first.
        </p>
      )}

      <ul className="mt-4 grid gap-2">
        {list.map((p) => (
          <li key={p.id}>
            <button
              type="button"
              onClick={() => select(p.id)}
              className="flex w-full items-center justify-between rounded-md border border-hairline bg-paper px-3 py-2.5 text-left transition hover:border-gold/40 hover:bg-ivory"
            >
              <span className="text-[13px] font-medium text-charcoal">{p.name}</span>
              <span className="text-[11px] text-gold-ink font-mono">{p.code}</span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
