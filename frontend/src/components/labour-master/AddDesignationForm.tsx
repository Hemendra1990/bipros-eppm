"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  labourMasterApi,
  type LabourCategory,
  type LabourGrade,
  type NationalityType,
} from "@/lib/api/labourMasterApi";

const CATEGORIES: { value: LabourCategory; prefix: string; label: string }[] = [
  { value: "SITE_MANAGEMENT",     prefix: "SM", label: "Site Management" },
  { value: "PLANT_EQUIPMENT",     prefix: "PO", label: "Plant & Equipment Operators" },
  { value: "SKILLED_LABOUR",      prefix: "SL", label: "Skilled Labour" },
  { value: "SEMI_SKILLED_LABOUR", prefix: "SS", label: "Semi-Skilled Labour" },
  { value: "GENERAL_UNSKILLED",   prefix: "GL", label: "General / Unskilled Labour" },
];
const GRADES: LabourGrade[] = ["A", "B", "C", "D", "E"];
const NATIONALITIES: NationalityType[] = ["OMANI", "EXPAT", "OMANI_OR_EXPAT"];

const inputCls =
  "px-3 py-2 border rounded text-sm w-full bg-white";

export function AddDesignationForm() {
  const router = useRouter();
  const qc = useQueryClient();

  const [category, setCategory] = useState<LabourCategory>("SITE_MANAGEMENT");
  const [code, setCode] = useState("SM-001");
  const [designation, setDesignation] = useState("");
  const [trade, setTrade] = useState("");
  const [grade, setGrade] = useState<LabourGrade>("C");
  const [nationality, setNationality] = useState<NationalityType>("EXPAT");
  const [experienceYearsMin, setExperience] = useState(1);
  const [defaultDailyRate, setRate] = useState(0);
  const [skillsCsv, setSkillsCsv] = useState("");
  const [certsCsv, setCertsCsv] = useState("");
  const [error, setError] = useState<string | null>(null);

  const onCategoryChange = (c: LabourCategory) => {
    setCategory(c);
    const prefix = CATEGORIES.find((x) => x.value === c)!.prefix;
    const suffix = (code.split("-")[1] ?? "001").padStart(3, "0");
    setCode(`${prefix}-${suffix}`);
  };

  const create = useMutation({
    mutationFn: () =>
      labourMasterApi.designations.create({
        code,
        designation,
        category,
        trade,
        grade,
        nationality,
        experienceYearsMin,
        defaultDailyRate,
        skills: skillsCsv.split(",").map((s) => s.trim()).filter(Boolean),
        certifications: certsCsv.split(",").map((s) => s.trim()).filter(Boolean),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["labour-designations"] });
      router.push("/labour-master/cards");
    },
    onError: (e: Error) => setError(e.message),
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        setError(null);
        create.mutate();
      }}
      className="grid gap-4 grid-cols-1 md:grid-cols-2 max-w-3xl"
    >
      <Field label="Worker Code">
        <input
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          pattern="^(SM|PO|SL|SS|GL)-\d{3}$"
          required
          className={inputCls}
        />
      </Field>
      <Field label="Designation">
        <input
          value={designation}
          onChange={(e) => setDesignation(e.target.value)}
          required
          className={inputCls}
        />
      </Field>
      <Field label="Category">
        <select
          value={category}
          onChange={(e) => onCategoryChange(e.target.value as LabourCategory)}
          className={inputCls}
        >
          {CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Trade">
        <input
          value={trade}
          onChange={(e) => setTrade(e.target.value)}
          required
          className={inputCls}
        />
      </Field>
      <Field label="Grade">
        <select
          value={grade}
          onChange={(e) => setGrade(e.target.value as LabourGrade)}
          className={inputCls}
        >
          {GRADES.map((g) => (
            <option key={g} value={g}>
              {g}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Nationality">
        <select
          value={nationality}
          onChange={(e) => setNationality(e.target.value as NationalityType)}
          className={inputCls}
        >
          {NATIONALITIES.map((n) => (
            <option key={n} value={n}>
              {n.replace("_", " / ")}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Experience (years)">
        <input
          type="number"
          min={0}
          value={experienceYearsMin}
          onChange={(e) => setExperience(Number(e.target.value))}
          className={inputCls}
        />
      </Field>
      <Field label="Daily Rate (OMR)">
        <input
          type="number"
          min={0}
          step="0.01"
          value={defaultDailyRate}
          onChange={(e) => setRate(Number(e.target.value))}
          className={inputCls}
        />
      </Field>
      <Field label="Skills (comma-separated)">
        <textarea
          rows={2}
          value={skillsCsv}
          onChange={(e) => setSkillsCsv(e.target.value)}
          className={inputCls}
        />
      </Field>
      <Field label="Certifications (comma-separated)">
        <textarea
          rows={2}
          value={certsCsv}
          onChange={(e) => setCertsCsv(e.target.value)}
          className={inputCls}
        />
      </Field>
      {error && <div className="md:col-span-2 text-sm text-red-700">{error}</div>}
      <div className="md:col-span-2 flex gap-2">
        <button
          type="submit"
          disabled={create.isPending}
          className="px-4 py-2 rounded bg-slate-900 text-white"
        >
          Save
        </button>
        <button
          type="button"
          onClick={() => router.back()}
          className="px-4 py-2 rounded border"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="space-y-1">
      <span className="text-sm font-medium">{label}</span>
      <div>{children}</div>
    </label>
  );
}
