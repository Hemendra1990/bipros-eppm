"use client";

import { useQuery } from "@tanstack/react-query";
import { CloudRain, Wind, Thermometer, MapPin, Loader2 } from "lucide-react";

import { Card } from "@/components/ui/card";
import { projectApi } from "@/lib/api/projectApi";
import { geoApi, type DailyWx } from "@/lib/api/geoApi";
import { WeatherIcon } from "./WeatherIcon";

// Same construction thresholds the backend WeatherRiskAgent uses, for at-a-glance highlighting.
const RAIN_MM = 20;
const WIND_KMH = 40;
const HEAT_C = 43;
const COLD_C = 3;

function weekday(iso: string): string {
  try {
    return new Date(iso + "T00:00:00").toLocaleDateString(undefined, { weekday: "short" });
  } catch {
    return iso;
  }
}

function flags(d: DailyWx): { adverse: boolean; hue: string | null } {
  if (d.rainfallMm != null && d.rainfallMm >= RAIN_MM) return { adverse: true, hue: "#2563EB" };
  if (d.windMaxKmh != null && d.windMaxKmh >= WIND_KMH) return { adverse: true, hue: "#B45309" };
  if (d.tempMaxC != null && d.tempMaxC >= HEAT_C) return { adverse: true, hue: "#DC2626" };
  if (d.tempMinC != null && d.tempMinC <= COLD_C) return { adverse: true, hue: "#0891B2" };
  return { adverse: false, hue: null };
}

/**
 * Live 7-day site-weather strip for the AI overview. Reads the project's configured coordinates and
 * pulls a real forecast from Open-Meteo (backend-proxied). Renders nothing when the project has no
 * site location or weather monitoring is off — the weather-risk agent is likewise dormant then.
 */
export function SiteWeatherPanel({ projectId }: { projectId: string }) {
  const { data: projectRes } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
    enabled: !!projectId,
  });
  const project = projectRes?.data;

  const lat = project?.siteLatitude ?? null;
  const lon = project?.siteLongitude ?? null;
  const monitoring = project?.weatherMonitoringEnabled ?? false;
  const enabled = monitoring && lat != null && lon != null;

  const { data: forecastRes, isLoading } = useQuery({
    queryKey: ["site-forecast", projectId, lat, lon],
    queryFn: () => geoApi.forecast(lat as number, lon as number, 7),
    enabled,
    staleTime: 60 * 60 * 1000,
  });

  if (!enabled) return null;

  const days = forecastRes?.data?.days ?? [];

  return (
    <Card variant="flat" className="p-4">
      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="flex items-center gap-1.5 text-sm font-semibold text-charcoal">
          <CloudRain size={15} className="text-gold" />
          Site weather
          <span className="ml-1 inline-flex items-center gap-1 text-[11px] font-normal text-slate">
            <MapPin size={11} />
            {project?.sitePlaceLabel ?? `${lat?.toFixed(2)}, ${lon?.toFixed(2)}`}
          </span>
        </div>
        {forecastRes?.data?.timezone && (
          <span className="text-[11px] text-slate">{forecastRes.data.timezone}</span>
        )}
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 py-6 text-sm text-slate">
          <Loader2 size={14} className="animate-spin" /> Loading forecast…
        </div>
      ) : days.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate">Forecast unavailable right now.</div>
      ) : (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {days.map((d) => {
            const f = flags(d);
            return (
              <div
                key={d.date}
                className="min-w-[92px] flex-1 rounded-lg border p-2.5 text-center"
                style={{
                  borderColor: f.adverse ? `${f.hue}55` : undefined,
                  backgroundColor: f.adverse ? `${f.hue}0F` : undefined,
                }}
              >
                <div className="text-[11px] font-semibold uppercase tracking-wide text-slate">
                  {weekday(d.date)}
                </div>
                <div className="mt-1 flex justify-center">
                  <WeatherIcon code={d.weatherCode} title={d.condition ?? undefined} size={34} />
                </div>
                <div className="mt-1 truncate text-[11px] text-charcoal" title={d.condition ?? ""}>
                  {d.condition ?? "—"}
                </div>
                <div className="mt-1.5 flex items-center justify-center gap-1 text-xs font-semibold tabular-nums text-charcoal">
                  <Thermometer size={11} className="text-slate" />
                  {d.tempMaxC != null ? Math.round(d.tempMaxC) : "–"}°
                  <span className="font-normal text-slate">
                    /{d.tempMinC != null ? Math.round(d.tempMinC) : "–"}°
                  </span>
                </div>
                <div className="mt-1 flex items-center justify-center gap-1 text-[11px] tabular-nums text-slate">
                  <CloudRain size={10} />
                  {d.rainfallMm != null ? `${Math.round(d.rainfallMm)}mm` : "–"}
                </div>
                <div className="flex items-center justify-center gap-1 text-[11px] tabular-nums text-slate">
                  <Wind size={10} />
                  {d.windMaxKmh != null ? `${Math.round(d.windMaxKmh)}` : "–"}
                </div>
              </div>
            );
          })}
        </div>
      )}
      <p className="mt-2 text-[11px] text-slate">
        Live Open-Meteo forecast. Adverse days (heavy rain, high wind, extreme heat/cold) are
        highlighted and drive the Weather-Risk agent&apos;s findings below.
      </p>
    </Card>
  );
}
