"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api/authApi";
import { useAuthStore } from "@/lib/state/store";

export default function LoginPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const result = await authApi.login({ username, password });
      if (result.data) {
        // Store token BEFORE calling me() so the interceptor can use it
        localStorage.setItem("access_token", result.data.accessToken);
        localStorage.setItem("refresh_token", result.data.refreshToken);
        const userResult = await authApi.me();
        if (userResult.data) {
          setAuth(userResult.data, result.data.accessToken, result.data.refreshToken);
          router.push("/");
        } else {
          // me() failed but login succeeded — still redirect
          setAuth({ id: "", username, email: "", firstName: "", lastName: "", enabled: true, roles: [] }, result.data.accessToken, result.data.refreshToken);
          router.push("/");
        }
      } else if (result.error) {
        setError(result.error.message);
      }
    } catch {
      setError("Invalid username or password");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-full items-center justify-center bg-gray-50 px-4 py-12">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-blue-600">Bipros EPPM</h1>
          <p className="mt-2 text-sm text-gray-500">
            Enterprise Project Portfolio Management
          </p>
        </div>

        <form onSubmit={handleSubmit} className="mt-8 space-y-6 rounded-lg border bg-white p-8 shadow-sm">
          <h2 className="text-xl font-semibold text-gray-900">Sign in</h2>

          {error && (
            <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">
              {error}
            </div>
          )}

          <div className="space-y-4">
            <div>
              <label htmlFor="username" className="block text-sm font-medium text-gray-700">
                Username
              </label>
              <input
                id="username"
                type="text"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Enter your username"
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700">
                Password
              </label>
              <input
                id="password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Enter your password"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50"
          >
            {loading ? "Signing in..." : "Sign in"}
          </button>
        </form>
      </div>
    </div>
  );
}
