import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface CalendarResponse {
  id: string;
  name: string;
  description: string | null;
  calendarType: "GLOBAL" | "PROJECT" | "RESOURCE";
  standardWorkHoursPerDay: number;
  standardWorkDaysPerWeek: number;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCalendarRequest {
  name: string;
  description?: string;
  calendarType: "GLOBAL" | "PROJECT" | "RESOURCE";
  standardWorkHoursPerDay?: number;
  standardWorkDaysPerWeek?: number;
}

export const calendarApi = {
  listCalendars: (type?: string) =>
    apiClient
      .get<ApiResponse<CalendarResponse[]>>("/v1/calendars", {
        params: type ? { type } : {},
      })
      .then((r) => r.data),

  getCalendar: (id: string) =>
    apiClient.get<ApiResponse<CalendarResponse>>(`/v1/calendars/${id}`).then((r) => r.data),

  createCalendar: (data: CreateCalendarRequest) =>
    apiClient.post<ApiResponse<CalendarResponse>>("/v1/calendars", data).then((r) => r.data),

  deleteCalendar: (id: string) => apiClient.delete(`/v1/calendars/${id}`),
};
