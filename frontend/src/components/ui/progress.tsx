import React from 'react';

export interface ProgressProps extends React.HTMLAttributes<HTMLDivElement> {
  value?: number;
  max?: number;
}

export function Progress({ value = 0, max = 100, className = '', ...props }: ProgressProps) {
  const percentage = Math.min((value / max) * 100, 100);

  return (
    <div
      className={`h-4 w-full overflow-hidden rounded-full bg-surface-hover ${className}`}
      {...props}
    >
      <div
        className="h-full bg-accent transition-all"
        style={{ width: `${percentage}%` }}
      />
    </div>
  );
}
