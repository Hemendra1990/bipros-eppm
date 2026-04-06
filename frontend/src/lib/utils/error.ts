/**
 * Extract a user-friendly error message from an unknown caught error.
 * Handles Axios-style errors with nested response data.
 */
export function getErrorMessage(err: unknown, fallback = "An unexpected error occurred"): string {
  if (err instanceof Error) {
    const axiosErr = err as Error & {
      response?: { data?: { error?: { message?: string }; message?: string } };
    };
    return (
      axiosErr.response?.data?.error?.message ??
      axiosErr.response?.data?.message ??
      axiosErr.message ??
      fallback
    );
  }
  if (typeof err === "string") return err;
  return fallback;
}
