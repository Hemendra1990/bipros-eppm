import Link from 'next/link';
import { ChevronRight } from 'lucide-react';

export interface BreadcrumbItem {
  label: string;
  href: string;
  active?: boolean;
}

interface BreadcrumbProps {
  items: BreadcrumbItem[];
}

export function Breadcrumb({ items }: BreadcrumbProps) {
  return (
    <nav
      aria-label="Breadcrumb"
      className="flex items-center space-x-1 text-sm text-slate-400"
    >
      {items.map((item, index) => (
        <div key={item.href} className="flex items-center">
          {index > 0 && (
            <ChevronRight className="mx-2 h-4 w-4 text-slate-500" />
          )}
          {item.active ? (
            <span className="font-medium text-white">{item.label}</span>
          ) : (
            <Link
              href={item.href}
              className="transition hover:text-blue-400 hover:underline"
            >
              {item.label}
            </Link>
          )}
        </div>
      ))}
    </nav>
  );
}
