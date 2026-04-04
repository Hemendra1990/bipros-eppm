'use client';

import { useEffect } from 'react';
import { AlertTriangle } from 'lucide-react';

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4 py-12">
      <div className="w-full max-w-md rounded-lg border border-red-200 bg-white p-8 shadow-md">
        <div className="flex items-center justify-center">
          <AlertTriangle className="h-12 w-12 text-red-500" />
        </div>

        <h2 className="mt-4 text-center text-2xl font-bold text-gray-900">
          Something went wrong
        </h2>

        <p className="mt-2 text-center text-sm text-gray-600">
          {error.message ||
            'An unexpected error occurred. Please try again.'}
        </p>

        {error.digest && (
          <p className="mt-2 text-center text-xs text-gray-500">
            Error ID: {error.digest}
          </p>
        )}

        <button
          onClick={() => reset()}
          className="mt-6 w-full rounded-lg bg-blue-600 px-4 py-2 text-center font-medium text-white transition hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          Try Again
        </button>

        <a
          href="/"
          className="mt-3 block w-full rounded-lg border border-gray-300 px-4 py-2 text-center font-medium text-gray-700 transition hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          Go Home
        </a>
      </div>
    </div>
  );
}
