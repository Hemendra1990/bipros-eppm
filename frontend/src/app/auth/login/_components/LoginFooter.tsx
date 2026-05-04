"use client";

import Link from "next/link";
import Image from "next/image";

const COLUMNS: Array<{ title: string; links: string[] }> = [
  {
    title: "Product",
    links: ["Schedule", "Cost", "Resources", "Risk", "Analytics"],
  },
  {
    title: "Company",
    links: ["About", "Customers", "Careers", "Press"],
  },
  {
    title: "Legal",
    links: ["Privacy", "Terms", "Cookies", "Security"],
  },
];

export function LoginFooter() {
  return (
    <footer className="relative border-t border-hairline bg-paper">
      <div className="mx-auto grid max-w-7xl grid-cols-1 gap-10 px-4 py-12 sm:px-8 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="relative inline-flex h-9 w-9 items-center justify-center rounded-xl bg-paper shadow-[0_2px_10px_rgba(28,28,28,0.06)] ring-1 ring-hairline">
              <Image
                src="/bipros-logo.png"
                alt="Bipros"
                width={28}
                height={28}
                className="h-7 w-7 object-contain"
              />
            </span>
            <span className="font-display text-[18px] font-semibold tracking-tight text-charcoal">
              Bipros
            </span>
            <span className="rounded border border-gold/30 bg-gold-tint/40 px-1.5 py-0.5 font-mono text-[9px] font-semibold uppercase tracking-[0.18em] text-gold-ink">
              EPPM
            </span>
          </div>
          <p className="mt-4 max-w-sm text-[13px] leading-[1.6] text-slate">
            Built for programme leaders running infrastructure, energy, and
            capital projects at scale. One disciplined book of work — schedule,
            cost, resources, and risk on the same calm spine.
          </p>
          <div className="mt-5 flex flex-wrap gap-1.5">
            {["SOC 2", "ISO 27001", "GDPR", "SSO / SAML"].map((t) => (
              <span
                key={t}
                className="rounded border border-hairline bg-ivory px-2 py-1 font-mono text-[9px] uppercase tracking-[0.14em] text-slate"
              >
                {t}
              </span>
            ))}
          </div>
        </div>

        {COLUMNS.map((col) => (
          <div key={col.title}>
            <div className="mb-3 text-[10.5px] font-semibold uppercase tracking-[0.22em] text-gold-deep">
              {col.title}
            </div>
            <ul className="space-y-2.5">
              {col.links.map((l) => (
                <li key={l}>
                  <a
                    href="#"
                    className="text-[13.5px] text-slate transition-colors hover:text-charcoal"
                  >
                    {l}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      <div className="border-t border-hairline">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-5 font-mono text-[10.5px] uppercase tracking-[0.16em] text-ash sm:px-8">
          <span className="flex items-center gap-2">
            <span aria-hidden className="h-1.5 w-1.5 rounded-full bg-emerald" />
            All systems operational
            <span className="text-hairline">·</span>
            v0.1.0 production
          </span>
          <span>© 2026 Bipros · all rights reserved</span>
          <div className="flex items-center gap-3">
            <Link
              href="/welcome"
              className="text-gold-deep transition hover:text-gold-ink"
            >
              Take the tour
            </Link>
            <span className="text-hairline">·</span>
            <a href="#" className="hover:text-charcoal">
              Status
            </a>
            <a href="#" className="hover:text-charcoal">
              Docs
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
