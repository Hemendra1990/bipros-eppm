import { apiClient } from "./client";
import type { ApiResponse, PagedResponse } from "../types";

export interface CalendarResponse {
  id: string;
  code: string;
  name: string;
  type: "GLOBAL" | "PROJECT" | "RESOURCE";
  baseCalendarId: string | null;
  workHoursPerDay: number;
  workDaysPerWeek: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCalendarRequest {
  code: string;
  name: string;
  type: "GLOBAL" | "PROJECT" | "RESOURCE";
  baseCalendarId?: string;
  workHoursPerDay?: number;
  workDaysPerWeek?: number;
}

export interface UpdateCalendarRequest {
  name?: string;
  workHoursPerDay?: number;
  workDaysPerWeek?: number;
}

export const calendarApi = {
  listCalendars: (page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<CalendarResponse>>>("/v1/calendars", {
        params: { page, size },
      })
      .then((r) => r.data),

  getCalendar: (id: string) =>
    apiClient.get<ApiResponse<CalendarResponse>>(`/v1/calendars/${id}`).then((r) => r.data),

  createCalendar: (data: CreateCalendarRequest) =>
    apiClient.post<ApiResponse<CalendarResponse>>("/v1/calendars", data).then((r) => r.data),

  updateCalendar: (id: string, data: UpdateCalendarRequest) =>
    apiClient.put<ApiResponse<CalendarResponse>>(`/v1/calendars/${id}`, data).then((r) => r.data),

  deleteCalendar: (id: string) => apiClient.delete(`/v1/calendars/${id}`),

  getGlobalCalendar: () =>
    apiClient
      .get<ApiResponse<CalendarResponse>>("/v1/calendars/global")
      .then((r) => r.data),
};
