"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Drawer } from "@/components/common/Drawer";

interface OverviewDrillDownDrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  viewAll?: { href: string; label: string };
  children: ReactNode;
}

export function OverviewDrillDownDrawer({
  open,
  onClose,
  title,
  subtitle,
  viewAll,
  children,
}: OverviewDrillDownDrawerProps) {
  return (
    <Drawer open={open} onClose={onClose} title={title} widthClass="max-w-2xl">
      <div className="flex flex-col gap-4 p-5">
        {subtitle && (
          <p className="text-sm text-slate">{subtitle}</p>
        )}
        <div>{children}</div>
        {viewAll && (
          <div className="border-t border-hairline pt-4">
            <Link
              href={viewAll.href}
              prefetch={false}
              className="text-sm font-medium text-gold-deep hover:underline"
            >
              {viewAll.label}
            </Link>
          </div>
        )}
      </div>
    </Drawer>
  );
}
