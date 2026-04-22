export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="w-full space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          className="h-10 animate-pulse rounded bg-surface-hover"
        />
      ))}
    </div>
  );
}

export function CardSkeleton() {
  return (
    <div className="w-full animate-pulse rounded-lg border border-border p-6">
      <div className="mb-4 h-6 w-3/4 rounded bg-surface-hover" />
      <div className="mb-3 h-4 w-full rounded bg-surface/50" />
      <div className="mb-3 h-4 w-5/6 rounded bg-surface/50" />
      <div className="h-4 w-4/6 rounded bg-surface/50" />
    </div>
  );
}

export function ListSkeleton({ items = 3 }: { items?: number }) {
  return (
    <div className="w-full space-y-4">
      {Array.from({ length: items }).map((_, i) => (
        <div key={i} className="animate-pulse">
          <div className="mb-2 h-4 w-1/3 rounded bg-surface-hover" />
          <div className="h-3 w-full rounded bg-surface/50" />
        </div>
      ))}
    </div>
  );
}

export function FormSkeleton() {
  return (
    <div className="w-full space-y-4">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i}>
          <div className="mb-2 h-4 w-1/4 rounded bg-surface-hover" />
          <div className="h-10 w-full animate-pulse rounded bg-surface/50" />
        </div>
      ))}
      <div className="h-10 w-full animate-pulse rounded bg-surface-hover" />
    </div>
  );
}
