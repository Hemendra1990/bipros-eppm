"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { MapPin, Search, Loader2, CloudRain } from "lucide-react";

import { projectApi } from "@/lib/api/projectApi";
import { geoApi, type GeoResult } from "@/lib/api/geoApi";
import { listCountries } from "@/lib/geo/countries";
import { useAuth } from "@/lib/auth/useAuth";
import { getErrorMessage } from "@/lib/utils/error";
import type { ProjectResponse } from "@/lib/types";

interface Draft {
  siteLatitude: number;
  siteLongitude: number;
  sitePlaceLabel: string;
  siteCountry: string | null;
  siteCountryCode: string | null;
  siteRegion: string | null;
  siteCity: string | null;
  siteTimezone: string | null;
}

function label(r: GeoResult): string {
  return [r.name, r.admin1, r.country].filter(Boolean).join(", ");
}

/**
 * Per-project "Site Location" card. An admin picks the site (Country dropdown narrows a live
 * city search sourced from Open-Meteo geocoding, proxied by the backend), which resolves the
 * coordinates that drive real-weather monitoring and the weather-risk agent. Non-admins see the
 * current location read-only.
 */
export function LocationCard({ project, projectId }: { project: ProjectResponse; projectId: string }) {
  const { isAdmin } = useAuth();
  const queryClient = useQueryClient();
  const countries = useMemo(() => listCountries(), []);

  const [editing, setEditing] = useState(false);
  const [country, setCountry] = useState<string>(project.siteCountryCode ?? "");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<GeoResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [weatherOn, setWeatherOn] = useState<boolean>(project.weatherMonitoringEnabled ?? false);

  // Debounced city search against the backend geo proxy.
  useEffect(() => {
    if (!editing || query.trim().length < 2) {
      setResults([]);
      return;
    }
    setSearching(true);
    const id = setTimeout(async () => {
      try {
        const res = await geoApi.search(query.trim(), country || undefined, 8);
        setResults(res.data ?? []);
      } catch {
        setResults([]);
      } finally {
        setSearching(false);
      }
    }, 300);
    return () => clearTimeout(id);
  }, [query, country, editing]);

  const mutation = useMutation({
    mutationFn: (payload: Draft & { weatherMonitoringEnabled: boolean }) =>
      projectApi.updateProject(projectId, payload),
    onSuccess: () => {
      toast.success("Site location saved — weather monitoring updated");
      queryClient.invalidateQueries({ queryKey: ["project", projectId] });
      setEditing(false);
      setQuery("");
      setResults([]);
    },
    onError: (e: unknown) => toast.error(getErrorMessage(e, "Failed to save location")),
  });

  const pick = (r: GeoResult) => {
    setDraft({
      siteLatitude: r.latitude,
      siteLongitude: r.longitude,
      sitePlaceLabel: label(r),
      siteCountry: r.country,
      siteCountryCode: r.countryCode,
      siteRegion: r.admin1,
      siteCity: r.name,
      siteTimezone: r.timezone,
    });
    setQuery(label(r));
    setResults([]);
  };

  const save = () => {
    if (!draft) {
      // Only toggling weather on an existing location.
      if (project.siteLatitude == null) {
        toast.error("Choose a site location first");
        return;
      }
      mutation.mutate({
        siteLatitude: project.siteLatitude,
        siteLongitude: project.siteLongitude as number,
        sitePlaceLabel: project.sitePlaceLabel ?? "",
        siteCountry: project.siteCountry,
        siteCountryCode: project.siteCountryCode,
        siteRegion: project.siteRegion,
        siteCity: project.siteCity,
        siteTimezone: project.siteTimezone,
        weatherMonitoringEnabled: weatherOn,
      });
      return;
    }
    mutation.mutate({ ...draft, weatherMonitoringEnabled: weatherOn });
  };

  const hasLocation = project.siteLatitude != null && project.siteLongitude != null;

  return (
    <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <h3 className="flex items-center gap-1.5 text-sm font-medium text-text-secondary">
            <MapPin size={14} /> Site Location
          </h3>

          {!editing && (
            <>
              <p className="mt-2 text-lg font-medium text-text-primary">
                {project.sitePlaceLabel ?? (hasLocation ? "Location set" : "Not set")}
              </p>
              <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-text-muted">
                {hasLocation && (
                  <span>
                    {project.siteLatitude!.toFixed(3)}, {project.siteLongitude!.toFixed(3)}
                    {project.siteTimezone ? ` · ${project.siteTimezone}` : ""}
                  </span>
                )}
                <span
                  className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${
                    project.weatherMonitoringEnabled
                      ? "bg-emerald-500/15 text-emerald-500"
                      : "bg-surface-hover text-text-muted"
                  }`}
                >
                  <CloudRain size={11} />
                  {project.weatherMonitoringEnabled ? "Weather monitoring on" : "Weather monitoring off"}
                </span>
              </div>
              <p className="mt-1 text-xs text-text-muted">
                Sets the coordinates used for real-weather monitoring and the weather-risk agent.
              </p>
            </>
          )}

          {editing && (
            <div className="mt-3 space-y-3">
              <div className="flex flex-col gap-2 sm:flex-row">
                <select
                  value={country}
                  onChange={(e) => setCountry(e.target.value)}
                  className="rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent sm:w-48"
                >
                  <option value="">All countries</option>
                  {countries.map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.name}
                    </option>
                  ))}
                </select>
                <div className="relative flex-1">
                  <Search size={14} className="absolute left-2.5 top-2.5 text-text-muted" />
                  <input
                    value={query}
                    onChange={(e) => {
                      setQuery(e.target.value);
                      setDraft(null);
                    }}
                    placeholder="Search city or place…"
                    className="w-full rounded-md border border-border bg-surface-hover py-2 pl-8 pr-8 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  />
                  {searching && <Loader2 size={14} className="absolute right-2.5 top-2.5 animate-spin text-text-muted" />}
                  {results.length > 0 && (
                    <ul className="absolute z-20 mt-1 max-h-64 w-full overflow-auto rounded-md border border-border bg-surface shadow-xl">
                      {results.map((r, i) => (
                        <li key={`${r.latitude}-${r.longitude}-${i}`}>
                          <button
                            type="button"
                            onClick={() => pick(r)}
                            className="flex w-full flex-col items-start px-3 py-2 text-left text-sm hover:bg-surface-hover"
                          >
                            <span className="font-medium text-text-primary">{r.name}</span>
                            <span className="text-xs text-text-muted">
                              {[r.admin1, r.country].filter(Boolean).join(", ")}
                              {r.timezone ? ` · ${r.timezone}` : ""}
                            </span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>

              {draft && (
                <p className="text-xs text-text-secondary">
                  Selected: <b className="text-text-primary">{draft.sitePlaceLabel}</b> ({draft.siteLatitude.toFixed(3)},{" "}
                  {draft.siteLongitude.toFixed(3)})
                </p>
              )}

              <label className="flex items-center gap-2 text-sm text-text-secondary">
                <input
                  type="checkbox"
                  checked={weatherOn}
                  onChange={(e) => setWeatherOn(e.target.checked)}
                  className="h-4 w-4 rounded border-border accent-accent"
                />
                Enable weather monitoring &amp; weather-risk agent for this site
              </label>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={save}
                  disabled={mutation.isPending}
                  className="rounded-md bg-accent px-3 py-1.5 text-xs font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
                >
                  {mutation.isPending ? "Saving…" : "Save"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setEditing(false);
                    setQuery("");
                    setResults([]);
                    setDraft(null);
                  }}
                  className="rounded-md border border-border px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-hover/50"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>

        {isAdmin && !editing && (
          <button
            type="button"
            onClick={() => {
              setEditing(true);
              setCountry(project.siteCountryCode ?? "");
              setWeatherOn(project.weatherMonitoringEnabled ?? false);
              setQuery("");
              setDraft(null);
            }}
            className="shrink-0 rounded-md border border-border px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-hover/50"
          >
            {hasLocation ? "Change" : "Set location"}
          </button>
        )}
      </div>
    </div>
  );
}
