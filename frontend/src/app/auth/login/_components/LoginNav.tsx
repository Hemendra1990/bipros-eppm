"use client";

import Link from "next/link";
import Image from "next/image";
import { useSyncExternalStore } from "react";
import { ArrowUpRight, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";

const NAV = ["Features", "Solutions", "Pricing", "About"] as const;

function useMounted() {
  return useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );
}

function ThemeToggle() {
  const { setTheme, resolvedTheme } = useTheme();
  const mounted = useMounted();
  const isDark = mounted && resolvedTheme === "dark";

  return (
    <button
      type="button"
      onClick={() => setTheme(isDark ? "light" : "dark")}
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      title={isDark ? "Switch to light mode" : "Switch to dark mode"}
      className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-hairline bg-paper text-slate transition-all hover:-translate-y-px hover:border-gold-deep/60 hover:text-gold-deep"
    >
      {mounted ? (
        isDark ? (
          <Sun size={14} strokeWidth={1.8} />
        ) : (
          <Moon size={14} strokeWidth={1.8} />
        )
      ) : (
        <Moon size={14} strokeWidth={1.8} />
      )}
    </button>
  );
}

export function LoginNav() {
  return (
    <header className="relative z-30 px-4 pt-5 sm:px-8">
      <div className="mx-auto flex max-w-7xl items-center justify-between rounded-2xl border border-hairline bg-paper/80 px-3.5 py-2.5 shadow-[0_4px_22px_rgba(28,28,28,0.05)] backdrop-blur-md">
        <Link href="/" className="group flex items-center gap-2.5">
          <span className="relative inline-flex h-9 w-9 items-center justify-center rounded-xl bg-paper shadow-[0_2px_10px_rgba(28,28,28,0.06)] ring-1 ring-hairline">
            <Image
              src="/bipros-logo.png"
              alt="Bipros"
              width={28}
              height={28}
              className="h-7 w-7 object-contain"
              priority
            />
            <span
              aria-hidden
              className="absolute -bottom-px left-2 right-2 h-px"
              style={{
                background:
                  "linear-gradient(90deg,transparent,#D4AF37,transparent)",
              }}
            />
          </span>
          <span className="font-display text-[18px] font-semibold tracking-tight text-charcoal">
            Bipros
          </span>
          <span className="ml-0.5 rounded border border-gold/30 bg-gold-tint/40 px-1.5 py-0.5 font-mono text-[9px] font-semibold uppercase tracking-[0.18em] text-gold-ink">
            EPPM
          </span>
        </Link>

        <nav className="hidden items-center gap-0.5 md:flex">
          {NAV.map((label) => (
            <a
              key={label}
              href="#features"
              className="nav-link relative rounded-lg px-3.5 py-1.5 text-[13px] font-medium text-slate transition-colors hover:text-charcoal"
            >
              {label}
            </a>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <span className="hidden items-center gap-1.5 rounded-full border border-hairline bg-ivory px-2.5 py-1 font-mono text-[9.5px] font-semibold uppercase tracking-[0.16em] text-slate sm:inline-flex">
            <span aria-hidden className="h-1.5 w-1.5 rounded-full bg-emerald" />
            v0.1.0 · production
          </span>
          <ThemeToggle />
          <Link
            href="/welcome"
            className="group hidden items-center gap-1 rounded-full px-3 py-1.5 text-[12.5px] font-semibold text-gold-deep transition hover:text-gold-ink sm:inline-flex"
          >
            Take the tour
            <ArrowUpRight
              size={13}
              className="transition-transform duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
            />
          </Link>
        </div>
      </div>
    </header>
  );
}
