import React from 'react';

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'secondary' | 'destructive' | 'outline';
}

const variantStyles = {
  default: 'bg-blue-500/10 text-blue-700',
  secondary: 'bg-slate-800/50 text-slate-300',
  destructive: 'bg-red-500/10 text-red-400',
  outline: 'border border-slate-700 text-slate-300',
};

export function Badge({ variant = 'default', className = '', ...props }: BadgeProps) {
  return (
    <div
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${variantStyles[variant]} ${className}`}
      {...props}
    />
  );
}
