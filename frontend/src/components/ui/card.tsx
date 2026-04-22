import React from 'react';

export type CardProps = React.HTMLAttributes<HTMLDivElement>;

export function Card({ className = '', ...props }: CardProps) {
  return (
    <div className={`rounded-xl border border-border bg-surface/50 shadow-lg ${className}`} {...props} />
  );
}

export function CardHeader({ className = '', ...props }: CardProps) {
  return <div className={`p-6 border-b border-border/50 ${className}`} {...props} />;
}

export function CardTitle({ className = '', ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <h2 className={`text-lg font-semibold text-text-primary ${className}`} {...props} />;
}

export function CardDescription({ className = '', ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={`text-sm text-text-secondary ${className}`} {...props} />;
}

export function CardContent({ className = '', ...props }: CardProps) {
  return <div className={`p-6 ${className}`} {...props} />;
}
