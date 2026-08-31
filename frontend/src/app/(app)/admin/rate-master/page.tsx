"use client";

import { useState } from "react";
import { ManpowerRateSection } from "./ManpowerRateSection";
import { EquipmentRateSection } from "./EquipmentRateSection";
import { MaterialRateSection } from "./MaterialRateSection";

type RateTab = "MANPOWER" | "EQUIPMENT" | "MATERIAL";

export default function RateMasterPage() {
  const [tab, setTab] = useState<RateTab>("MANPOWER");

  const tabs: { key: RateTab; label: string }[] = [
    { key: "MANPOWER", label: "Manpower" },
    { key: "EQUIPMENT", label: "Equipment" },
    { key: "MATERIAL", label: "Material" },
  ];

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-text-primary">Rate Master</h1>
        <p className="mt-1 text-sm text-text-secondary">
          Rate books for Manpower, Equipment, and Material. Each row's Unit and Rate auto-fill
          onto every Resource pointing at it; revising a rate cascades to all linked resources.
        </p>
      </div>

      <div className="mb-6 flex flex-wrap gap-2 border-b border-border">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`-mb-px rounded-t-md px-4 py-2 text-sm font-medium transition-colors ${
              tab === t.key
                ? "border border-b-transparent border-border bg-surface text-text-primary"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "MANPOWER" && <ManpowerRateSection />}
      {tab === "EQUIPMENT" && <EquipmentRateSection />}
      {tab === "MATERIAL" && <MaterialRateSection />}
    </div>
  );
}
