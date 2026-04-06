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

export interface CalendarDetailResponse {
  calendar: CalendarResponse;
  workWeeks: CalendarWorkWeekResponse[];
  exceptions: CalendarExceptionResponse[];
}

export interface CalendarWorkWeekResponse {
  id: string;
  calendarId: string;
  dayOfWeek: string;
  dayType: "WORKING" | "NON_WORKING" | "EXCEPTION_WORKING" | "EXCEPTION_NON_WORKING";
  startTime1: string | null;
  endTime1: string | null;
  startTime2: string | null;
  endTime2: string | null;
  totalWorkHours: number | null;
}

export interface CalendarExceptionResponse {
  id: string;
  calendarId: string;
  exceptionDate: string;
  dayType: "WORKING" | "NON_WORKING" | "EXCEPTION_WORKING" | "EXCEPTION_NON_WORKING";
  name: string | null;
  startTime1: string | null;
  endTime1: string | null;
  startTime2: string | null;
  endTime2: string | null;
  totalWorkHours: number | null;
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

export interface CalendarWorkWeekRequest {
  dayOfWeek: string;
  dayType: "WORKING" | "NON_WORKING";
  startTime1?: string;
  endTime1?: string;
  startTime2?: string;
  endTime2?: string;
}

export interface CalendarExceptionRequest {
  exceptionDate: string;
  dayType: "WORKING" | "NON_WORKING" | "EXCEPTION_WORKING" | "EXCEPTION_NON_WORKING";
  name?: string;
  startTime1?: string;
  endTime1?: string;
  startTime2?: string;
  endTime2?: string;
}

export const calendarApi = {
  listCalendars: (type?: string) =>
    apiClient
      .get<ApiResponse<CalendarResponse[]>>("/v1/calendars", {
        params: type ? { type } : {},
      })
      .then((r) => r.data),

  getCalendar: (id: string) =>
    apiClient
      .get<ApiResponse<CalendarDetailResponse>>(`/v1/calendars/${id}`)
      .then((r) => r.data),

  createCalendar: (data: CreateCalendarRequest) =>
    apiClient
      .post<ApiResponse<CalendarResponse>>("/v1/calendars", data)
      .then((r) => r.data),

  updateCalendar: (id: string, data: CreateCalendarRequest) =>
    apiClient
      .put<ApiResponse<CalendarResponse>>(`/v1/calendars/${id}`, data)
      .then((r) => r.data),

  deleteCalendar: (id: string) => apiClient.delete(`/v1/calendars/${id}`),

  setWorkWeek: (calendarId: string, data: CalendarWorkWeekRequest[]) =>
    apiClient
      .put<ApiResponse<CalendarWorkWeekResponse[]>>(
        `/v1/calendars/${calendarId}/work-week`,
        data
      )
      .then((r) => r.data),

  addException: (calendarId: string, data: CalendarExceptionRequest) =>
    apiClient
      .post<ApiResponse<CalendarExceptionResponse>>(
        `/v1/calendars/${calendarId}/exceptions`,
        data
      )
      .then((r) => r.data),

  getExceptions: (calendarId: string, start: string, end: string) =>
    apiClient
      .get<ApiResponse<CalendarExceptionResponse[]>>(
        `/v1/calendars/${calendarId}/exceptions`,
        { params: { start, end } }
      )
      .then((r) => r.data),

  removeException: (calendarId: string, exceptionId: string) =>
    apiClient.delete(`/v1/calendars/${calendarId}/exceptions/${exceptionId}`),
};
