"use client";

import { useEffect, useState } from "react";

export interface SectionNavItem {
  id: string;
  label: string;
}

interface PortfolioSectionNavProps {
  sections: SectionNavItem[];
}

export function PortfolioSectionNav({ sections }: PortfolioSectionNavProps) {
  const [active, setActive] = useState(sections[0]?.id);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio);
        if (visible[0]) setActive(visible[0].target.id);
      },
      { rootMargin: "-20% 0px -60% 0px", threshold: [0, 0.25, 0.5, 0.75, 1] },
    );
    sections.forEach((s) => {
      const el = document.getElementById(s.id);
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, [sections]);

  const jumpTo = (id: string) => {
    const el = document.getElementById(id);
    if (!el) return;
    // Offset for sticky nav (~56px) + a little breathing room
    const y = el.getBoundingClientRect().top + window.scrollY - 72;
    window.scrollTo({ top: y, behavior: "smooth" });
  };

  return (
    <nav
      aria-label="Portfolio sections"
      className="sticky top-0 z-10 -mx-6 mb-6 border-b border-border bg-surface/90 px-6 py-3 backdrop-blur"
    >
      <div className="flex flex-wrap gap-2">
        {sections.map((s) => (
          <button
            key={s.id}
            onClick={() => jumpTo(s.id)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              active === s.id
                ? "bg-accent text-white"
                : "bg-surface-hover/40 text-text-secondary hover:bg-surface-hover hover:text-text-primary"
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>
    </nav>
  );
}
