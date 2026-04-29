"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { projectApi } from "@/lib/api/projectApi";

type Props = {
  /** Title shown above the picker, defaults to "Select a project". */
  title?: string;
  /**
   * Path the picker should navigate to when a project is selected. The selected
   * `projectId` is appended as a query param (`?projectId=<id>`). Defaults to the
   * current pathname so the user stays on the tab they were viewing.
   */
  redirectBasePath?: string;
};

export function ProjectPickerEmpty({ title = "Select a project", redirectBasePath }: Props) {
  const projects = useQuery({
    queryKey: ["labour-master-project-picker"],
    queryFn: () => projectApi.listProjects(0, 50),
  });

  const list = projects.data?.data?.content ?? [];
  return (
    <section className="rounded-lg border border-border bg-card text-card-foreground p-6 max-w-2xl">
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Pick a project to view its labour deployments.
      </p>

      {projects.isLoading && (
        <p className="mt-4 text-sm text-muted-foreground">Loading projects…</p>
      )}
      {projects.isError && (
        <p className="mt-4 text-sm text-red-600">Failed to load projects.</p>
      )}
      {!projects.isLoading && !projects.isError && list.length === 0 && (
        <p className="mt-4 text-sm text-muted-foreground">
          No projects yet. Create one under{" "}
          <Link className="underline" href="/projects">Projects</Link> first.
        </p>
      )}

      <ul className="mt-4 grid gap-2">
        {list.map((p) => {
          const target = `${redirectBasePath ?? "/labour-master"}?projectId=${p.id}`;
          return (
            <li key={p.id}>
              <Link
                href={target}
                className="flex items-center justify-between rounded-md border border-border px-3 py-2 hover:bg-muted transition"
              >
                <span className="text-sm font-medium">{p.name}</span>
                <span className="text-xs text-muted-foreground font-mono">{p.code}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
