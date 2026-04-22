import React from 'react';

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'secondary' | 'destructive' | 'outline';
}

const variantStyles = {
  default: 'bg-accent/10 text-accent',
  secondary: 'bg-surface-hover text-text-secondary',
  destructive: 'bg-danger/10 text-danger',
  outline: 'border border-border text-text-secondary',
};

export function Badge({ variant = 'default', className = '', ...props }: BadgeProps) {
  return (
    <div
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${variantStyles[variant]} ${className}`}
      {...props}
    />
  );
}
