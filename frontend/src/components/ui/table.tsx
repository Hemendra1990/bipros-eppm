import React from 'react';

export type TableProps = React.HTMLAttributes<HTMLTableElement>;

export function Table({ className = '', ...props }: TableProps) {
  return (
    <table className={`w-full text-sm ${className}`} {...props} />
  );
}

export function TableHeader({ className = '', ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={`border-b border-border ${className}`} {...props} />;
}

export function TableBody({ className = '', ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={className} {...props} />;
}

export function TableRow({ className = '', ...props }: React.HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={`border-b border-border/50 ${className}`} {...props} />;
}

export function TableHead({ className = '', ...props }: React.HTMLAttributes<HTMLTableCellElement>) {
  return (
    <th className={`text-left py-3 px-4 font-medium text-text-secondary ${className}`} {...props} />
  );
}

export function TableCell({ className = '', ...props }: React.HTMLAttributes<HTMLTableCellElement>) {
  return (
    <td className={`py-3 px-4 ${className}`} {...props} />
  );
}
