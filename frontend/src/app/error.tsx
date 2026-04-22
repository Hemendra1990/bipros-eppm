'use client';

import { useEffect } from 'react';
import Link from 'next/link';
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
    <div className="flex min-h-screen items-center justify-center bg-background/80 px-4 py-12">
      <div className="w-full max-w-md rounded-lg border border-border bg-surface/50 p-8 shadow-md">
        <div className="flex items-center justify-center">
          <AlertTriangle className="h-12 w-12 text-danger" />
        </div>

        <h2 className="mt-4 text-center text-2xl font-bold text-text-primary">
          Something went wrong
        </h2>

        <p className="mt-2 text-center text-sm text-text-secondary">
          {error.message ||
            'An unexpected error occurred. Please try again.'}
        </p>

        {error.digest && (
          <p className="mt-2 text-center text-xs text-text-muted">
            Error ID: {error.digest}
          </p>
        )}

        <button
          onClick={() => reset()}
          className="mt-6 w-full rounded-lg bg-accent px-4 py-2 text-center font-medium text-text-primary transition hover:bg-accent-hover focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-background"
        >
          Try Again
        </button>

        <Link
          href="/"
          className="mt-3 block w-full rounded-lg border border-border px-4 py-2 text-center font-medium text-text-secondary transition hover:bg-surface-hover focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-background"
        >
          Go Home
        </Link>
      </div>
    </div>
  );
}
