import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (typeof window !== "undefined") {
    const token = localStorage.getItem("access_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError & { config?: InternalAxiosRequestConfig & { _retry?: boolean } }) => {
    if (error.response?.status === 401 && !error.config?._retry) {
      error.config!._retry = true;
      // Skip redirect for login/refresh endpoints — let the caller handle the error
      const requestUrl = error.config?.url || '';
      if (requestUrl.includes('/v1/auth/login') || requestUrl.includes('/v1/auth/refresh')) {
        return Promise.reject(error);
      }
      if (typeof window !== "undefined") {
        const refreshToken = localStorage.getItem("refresh_token");
        if (refreshToken) {
          try {
            const res = await axios.post(`${API_BASE_URL}/v1/auth/refresh`, { refreshToken });
            const newToken = res.data.data.accessToken;
            localStorage.setItem("access_token", newToken);
            document.cookie = `access_token=${newToken}; path=/; max-age=3600; Secure; SameSite=Strict`;
            error.config!.headers.Authorization = `Bearer ${newToken}`;
            return apiClient(error.config!);
          } catch {
            // refresh failed, clear auth
            localStorage.removeItem("access_token");
            localStorage.removeItem("refresh_token");
            document.cookie = 'access_token=; path=/; max-age=0; Secure; SameSite=Strict';
            window.location.href = "/auth/login";
          }
        } else {
          // no refresh token, redirect to login
          localStorage.removeItem("access_token");
          document.cookie = 'access_token=; path=/; max-age=0; Secure; SameSite=Strict';
          window.location.href = "/auth/login";
        }
      }
    }
    return Promise.reject(error);
  }
);
