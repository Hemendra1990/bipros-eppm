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
      className="flex items-center space-x-1 text-sm text-gray-600"
    >
      {items.map((item, index) => (
        <div key={item.href} className="flex items-center">
          {index > 0 && (
            <ChevronRight className="mx-2 h-4 w-4 text-gray-400" />
          )}
          {item.active ? (
            <span className="font-medium text-gray-900">{item.label}</span>
          ) : (
            <Link
              href={item.href}
              className="transition hover:text-blue-600 hover:underline"
            >
              {item.label}
            </Link>
          )}
        </div>
      ))}
    </nav>
  );
}
