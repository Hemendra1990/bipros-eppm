"use client";

import type {
  LabourCategory,
  LabourCategoryReference,
  LabourGrade,
} from "@/lib/api/labourMasterApi";

type Props = {
  categories: LabourCategoryReference[];
  selectedCategory: LabourCategory | null;
  onCategoryChange: (c: LabourCategory | null) => void;
  selectedGrade: LabourGrade | null;
  onGradeChange: (g: LabourGrade | null) => void;
  query: string;
  onQueryChange: (q: string) => void;
};

const GRADES: LabourGrade[] = ["A", "B", "C", "D", "E"];

export function CategoryFilterBar({
  categories,
  selectedCategory,
  onCategoryChange,
  selectedGrade,
  onGradeChange,
  query,
  onQueryChange,
}: Props) {
  return (
    <div className="flex flex-wrap items-center gap-2 p-3 rounded-lg bg-white border">
      <button
        type="button"
        onClick={() => onCategoryChange(null)}
        className={`px-3 py-1.5 text-sm rounded ${
          selectedCategory == null ? "bg-slate-900 text-white" : "bg-slate-100"
        }`}
      >
        All
      </button>
      {categories.map((c) => (
        <button
          key={c.category}
          type="button"
          onClick={() => onCategoryChange(c.category)}
          className={`px-3 py-1.5 text-sm rounded ${
            selectedCategory === c.category ? "bg-slate-900 text-white" : "bg-slate-100"
          }`}
        >
          {c.codePrefix} · {c.displayName}
        </button>
      ))}
      <div className="mx-2 h-6 border-l" />
      <span className="text-xs text-muted-foreground">Grade:</span>
      <button
        type="button"
        onClick={() => onGradeChange(null)}
        className={`px-2 py-1 text-xs rounded ${
          selectedGrade == null ? "bg-slate-900 text-white" : "bg-slate-100"
        }`}
      >
        Any
      </button>
      {GRADES.map((g) => (
        <button
          key={g}
          type="button"
          onClick={() => onGradeChange(g)}
          className={`px-2 py-1 text-xs rounded ${
            selectedGrade === g ? "bg-slate-900 text-white" : "bg-slate-100"
          }`}
        >
          {g}
        </button>
      ))}
      <div className="ml-auto">
        <input
          type="search"
          placeholder="Search code, designation, trade…"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          className="px-3 py-1.5 text-sm rounded border min-w-[240px]"
        />
      </div>
    </div>
  );
}
