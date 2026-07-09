import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/** A geocoding hit from Open-Meteo (proxied through the backend — CSP blocks the browser). */
export interface GeoResult {
  name: string;
  admin1: string | null;
  admin2: string | null;
  country: string | null;
  countryCode: string | null;
  latitude: number;
  longitude: number;
  timezone: string | null;
  population: number | null;
}

export interface DailyWx {
  date: string;
  tempMaxC: number | null;
  tempMinC: number | null;
  rainfallMm: number | null;
  windMaxKmh: number | null;
  weatherCode: number | null;
  condition: string | null;
}

export interface WeatherForecast {
  latitude: number;
  longitude: number;
  timezone: string | null;
  days: DailyWx[];
}

export const geoApi = {
  /** Search places by name; optionally restrict to an ISO-3166 alpha-2 country code. */
  search: (q: string, country?: string, count = 10) =>
    apiClient
      .get<ApiResponse<GeoResult[]>>("/v1/geo/search", { params: { q, country, count } })
      .then((r) => r.data),

  /** Live daily forecast for a coordinate (for the site-weather panel). */
  forecast: (lat: number, lon: number, days = 7) =>
    apiClient
      .get<ApiResponse<WeatherForecast>>("/v1/geo/forecast", { params: { lat, lon, days } })
      .then((r) => r.data),
};
