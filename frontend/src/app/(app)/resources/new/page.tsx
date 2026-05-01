"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { Breadcrumb } from "@/components/common/Breadcrumb";
import {
  resourceApi,
  type CreateResourceRequest,
  type EquipmentDetailsDto,
  type ManpowerDto,
  type MaterialDetailsDto,
  type ResourceStatus,
  type ResourceOwnership,
  type MaterialType,
  type ManpowerCategory,
  type EmploymentType,
  type SkillLevel,
  type SalaryType,
  type PaymentMode,
  type AttendanceStatus,
  type ShiftType,
  type AvailabilityStatus,
  type MedicalStatus,
} from "@/lib/api/resourceApi";
import { resourceTypeApi } from "@/lib/api/resourceTypeApi";
import { resourceRoleApi } from "@/lib/api/resourceRoleApi";
import { calendarApi } from "@/lib/api/calendarApi";
import { getErrorMessage } from "@/lib/utils/error";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import toast from "react-hot-toast";

type TypeKind = "MANPOWER" | "EQUIPMENT" | "MATERIAL" | "OTHER";

function classifyType(code: string | undefined | null): TypeKind {
  switch (code) {
    case "MANPOWER":
    case "LABOR":
      return "MANPOWER";
    case "EQUIPMENT":
    case "NONLABOR":
      return "EQUIPMENT";
    case "MATERIAL":
      return "MATERIAL";
    default:
      return "OTHER";
  }
}

function codePrefixFor(kind: TypeKind): string {
  switch (kind) {
    case "MANPOWER":
      return "LAB-";
    case "EQUIPMENT":
      return "EQ-";
    case "MATERIAL":
      return "MAT-";
    default:
      return "RES-";
  }
}

const inputCls =
  "mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent";

const labelCls = "block text-sm font-medium text-text-secondary";

interface CommonForm {
  code: string;
  name: string;
  description: string;
  availability: string;
  costPerUnit: string;
  unit: string;
  status: ResourceStatus;
  calendarId: string;
  parentId: string;
}

const emptyCommon = (): CommonForm => ({
  code: "",
  name: "",
  description: "",
  availability: "",
  costPerUnit: "",
  unit: "",
  status: "ACTIVE",
  calendarId: "",
  parentId: "",
});

const emptyEquipment = (): EquipmentDetailsDto => ({});
const emptyMaterial = (): MaterialDetailsDto => ({});
const emptyManpower = (): ManpowerDto => ({
  master: {},
  skills: {},
  financials: {},
  attendance: {},
  allocation: {},
  compliance: {},
});

function toNumberOrUndef(value: string | undefined | null): number | undefined {
  if (value == null) return undefined;
  const trimmed = String(value).trim();
  if (trimmed === "") return undefined;
  const n = Number(trimmed);
  return Number.isFinite(n) ? n : undefined;
}

function toIntOrUndef(value: string | undefined | null): number | undefined {
  const n = toNumberOrUndef(value);
  if (n === undefined) return undefined;
  return Math.trunc(n);
}

function parseJsonStringOrThrow(value: string, label: string): string | undefined {
  const trimmed = value.trim();
  if (trimmed === "") return undefined;
  try {
    JSON.parse(trimmed);
  } catch {
    throw new Error(`${label} must be valid JSON`);
  }
  return trimmed;
}

export default function NewResourcePage() {
  const router = useRouter();

  const [step, setStep] = useState<1 | 2>(1);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Step 1
  const [typeId, setTypeId] = useState("");
  const [roleId, setRoleId] = useState("");
  const [common, setCommon] = useState<CommonForm>(emptyCommon());

  // Step 2 — type-specific
  const [equipment, setEquipment] = useState<EquipmentDetailsDto>(emptyEquipment());
  const [material, setMaterial] = useState<MaterialDetailsDto>(emptyMaterial());
  const [manpower, setManpower] = useState<ManpowerDto>(emptyManpower());
  const [activeManpowerSection, setActiveManpowerSection] = useState<
    "master" | "skills" | "financials" | "attendance" | "allocation" | "compliance"
  >("master");

  // Reference data
  const { data: typesData } = useQuery({
    queryKey: ["resource-types"],
    queryFn: () => resourceTypeApi.list(),
  });
  const types = useMemo(
    () => (typesData?.data ?? []).filter((t) => t.active),
    [typesData]
  );

  const { data: rolesData, isLoading: rolesLoading } = useQuery({
    queryKey: ["resource-roles", "by-type", typeId],
    queryFn: () => resourceRoleApi.listByType(typeId),
    enabled: !!typeId,
  });
  const roles = useMemo(
    () => (rolesData?.data ?? []).filter((r) => r.active),
    [rolesData]
  );

  const { data: calendarsData, isLoading: calendarsLoading } = useQuery({
    queryKey: ["calendars", "all"],
    queryFn: () => calendarApi.listCalendars(),
  });
  const resourceCalendars = calendarsData?.data ?? [];

  const selectedType = types.find((t) => t.id === typeId) ?? null;
  const kind = classifyType(selectedType?.code);

  // Auto-fill code prefix when type changes (only when code is empty or still a prefix-only string)
  useEffect(() => {
    if (!selectedType) return;
    setCommon((prev) => {
      const prefix = codePrefixFor(kind);
      // Only seed the prefix if user hasn't typed a real code yet.
      if (prev.code.trim() === "" || /^(LAB-|EQ-|MAT-|RES-)$/.test(prev.code.trim())) {
        return { ...prev, code: prefix };
      }
      return prev;
    });
  }, [selectedType, kind]);

  // Auto-fill name and costPerUnit from selected role's defaults (only if blank).
  useEffect(() => {
    if (!roleId) return;
    const role = roles.find((r) => r.id === roleId);
    if (!role) return;
    setCommon((prev) => {
      const next = { ...prev };
      if (prev.name.trim() === "") next.name = role.name;
      if (prev.costPerUnit.trim() === "" && role.defaultRate != null) {
        next.costPerUnit = String(role.defaultRate);
      }
      if (prev.unit.trim() === "" && role.productivityUnit) {
        next.unit = role.productivityUnit;
      }
      return next;
    });
  }, [roleId, roles]);

  const canAdvanceToStep2 = !!typeId && !!roleId && common.name.trim() !== "";

  const handleSubmit = async () => {
    setError("");

    if (!typeId || !roleId) {
      setError("Select a Resource Type and Role first");
      return;
    }
    if (!common.name.trim()) {
      setError("Name is required");
      return;
    }

    setIsSubmitting(true);
    try {
      const base: CreateResourceRequest = {
        code: common.code.trim() || undefined,
        name: common.name.trim(),
        description: common.description.trim() || undefined,
        roleId,
        resourceTypeId: typeId,
        availability: toNumberOrUndef(common.availability) ?? null,
        costPerUnit: toNumberOrUndef(common.costPerUnit) ?? null,
        unit: common.unit.trim() || null,
        status: common.status,
        calendarId: common.calendarId || null,
        parentId: common.parentId || null,
      };

      if (kind === "EQUIPMENT") {
        base.equipment = equipment;
      } else if (kind === "MATERIAL") {
        // alternateUnits is JSONB — validate the textarea before sending.
        if (material.alternateUnits) {
          parseJsonStringOrThrow(
            material.alternateUnits,
            "Alternate Units (JSON)"
          );
        }
        base.material = material;
      } else if (kind === "MANPOWER") {
        // Validate JSON-ish textareas. Each may be undefined; surface a nice error if user typed bad JSON.
        const skills = manpower.skills;
        if (skills?.secondarySkills)
          parseJsonStringOrThrow(skills.secondarySkills, "Secondary Skills (JSON)");
        const financials = manpower.financials;
        if (financials?.allowances)
          parseJsonStringOrThrow(financials.allowances, "Allowances (JSON)");
        if (financials?.deductions)
          parseJsonStringOrThrow(financials.deductions, "Deductions (JSON)");
        const attendance = manpower.attendance;
        if (attendance?.leaveSchedule)
          parseJsonStringOrThrow(attendance.leaveSchedule, "Leave Schedule (JSON)");
        base.manpower = manpower;
      }

      const result = await resourceApi.createResource(base);
      if (result.data) {
        toast.success("Resource created");
        router.push(`/resources/${result.data.id}`);
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create resource"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div>
      <div className="mb-4">
        <Breadcrumb
          items={[
            { label: "Resources", href: "/resources" },
            { label: "New Resource", href: "/resources/new", active: true },
          ]}
        />
      </div>
      <PageHeader
        title="New Resource"
        description="Create a manpower, equipment or material resource"
      />

      {/* Step indicator */}
      <div className="mb-6 flex items-center gap-2 text-sm">
        <span
          className={`inline-flex items-center gap-2 rounded-md px-3 py-1.5 ${
            step === 1
              ? "bg-accent text-text-primary"
              : "bg-surface-hover text-text-secondary"
          }`}
        >
          <span className="font-bold">1</span> Identity
        </span>
        <span className="text-text-muted">→</span>
        <span
          className={`inline-flex items-center gap-2 rounded-md px-3 py-1.5 ${
            step === 2
              ? "bg-accent text-text-primary"
              : "bg-surface-hover text-text-secondary"
          }`}
        >
          <span className="font-bold">2</span> {kind === "MANPOWER" ? "Manpower" : kind === "EQUIPMENT" ? "Equipment" : kind === "MATERIAL" ? "Material" : "Details"}
        </span>
      </div>

      {error && (
        <div className="mb-4 flex items-center justify-between rounded-md bg-danger/10 p-4 text-sm text-danger">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => setError("")}
            className="ml-3 text-danger hover:text-danger"
          >
            ×
          </button>
        </div>
      )}

      {step === 1 && (
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h2 className="mb-4 text-lg font-semibold text-text-primary">Resource Type</h2>
          <div className="mb-6 grid grid-cols-1 md:grid-cols-3 gap-4">
            {types.map((t) => {
              const k = classifyType(t.code);
              const isSelected = t.id === typeId;
              return (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => {
                    setTypeId(t.id);
                    setRoleId("");
                  }}
                  className={`rounded-lg border p-4 text-left transition-colors ${
                    isSelected
                      ? "border-accent bg-accent/10"
                      : "border-border bg-surface hover:bg-surface-hover/50"
                  }`}
                >
                  <div className="text-xs font-semibold uppercase tracking-wide text-text-muted">
                    {t.code}
                  </div>
                  <div className="mt-1 text-base font-semibold text-text-primary">
                    {t.name}
                  </div>
                  <div className="mt-1 text-xs text-text-muted">
                    {k === "MANPOWER" && "People — workers, supervisors, drivers."}
                    {k === "EQUIPMENT" && "Plant & machinery — owned, hired, sub-let."}
                    {k === "MATERIAL" && "Consumed or stocked materials."}
                    {k === "OTHER" && (t.description ?? "Custom resource type.")}
                  </div>
                </button>
              );
            })}
          </div>

          {typeId && (
            <>
              <h2 className="mb-3 text-lg font-semibold text-text-primary">Role</h2>
              <div className="mb-6">
                <SearchableSelect
                  options={roles.map((r) => ({
                    value: r.id,
                    label: `${r.name} (${r.code})`,
                  }))}
                  value={roleId}
                  onChange={setRoleId}
                  placeholder={
                    rolesLoading
                      ? "Loading roles…"
                      : roles.length === 0
                        ? "No active roles for this type"
                        : "Pick a role"
                  }
                  disabled={rolesLoading || roles.length === 0}
                />
                <p className="mt-1 text-xs text-text-muted">
                  Define roles in{" "}
                  <a href="/admin/resource-roles" className="text-accent hover:underline">
                    Resource Roles
                  </a>
                  . The chosen role auto-fills name, unit and rate when blank.
                </p>
              </div>

              <h2 className="mb-3 text-lg font-semibold text-text-primary">Common Fields</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className={labelCls}>Code</label>
                  <input
                    type="text"
                    value={common.code}
                    onChange={(e) => setCommon({ ...common, code: e.target.value })}
                    placeholder={`${codePrefixFor(kind)}001`}
                    className={inputCls}
                  />
                  <p className="mt-1 text-xs text-text-muted">
                    Auto-generated from prefix when blank.
                  </p>
                </div>
                <div>
                  <label className={labelCls}>Name *</label>
                  <input
                    type="text"
                    value={common.name}
                    onChange={(e) => setCommon({ ...common, name: e.target.value })}
                    className={inputCls}
                    required
                  />
                </div>
                <div className="md:col-span-2">
                  <label className={labelCls}>Description</label>
                  <textarea
                    value={common.description}
                    onChange={(e) => setCommon({ ...common, description: e.target.value })}
                    rows={2}
                    className={inputCls}
                  />
                </div>
                <div>
                  <label className={labelCls}>Availability</label>
                  <input
                    type="number"
                    step="0.01"
                    value={common.availability}
                    onChange={(e) => setCommon({ ...common, availability: e.target.value })}
                    className={inputCls}
                    placeholder="e.g. 8 hours/day or 100 units"
                  />
                </div>
                <div>
                  <label className={labelCls}>Cost Per Unit</label>
                  <input
                    type="number"
                    step="0.01"
                    value={common.costPerUnit}
                    onChange={(e) => setCommon({ ...common, costPerUnit: e.target.value })}
                    className={inputCls}
                  />
                </div>
                <div>
                  <label className={labelCls}>Unit</label>
                  <input
                    type="text"
                    value={common.unit}
                    onChange={(e) => setCommon({ ...common, unit: e.target.value })}
                    placeholder="Hour, Day, Cum, MT, Bags…"
                    className={inputCls}
                  />
                </div>
                <div>
                  <label className={labelCls}>Status</label>
                  <select
                    value={common.status}
                    onChange={(e) =>
                      setCommon({ ...common, status: e.target.value as ResourceStatus })
                    }
                    className={inputCls}
                  >
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                  </select>
                </div>
                <div>
                  <label className={labelCls}>Calendar</label>
                  <select
                    value={common.calendarId}
                    onChange={(e) => setCommon({ ...common, calendarId: e.target.value })}
                    className={inputCls}
                    disabled={calendarsLoading}
                  >
                    {calendarsLoading && <option value="">Loading…</option>}
                    <option value="">— none —</option>
                    {resourceCalendars.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className={labelCls}>Parent Resource ID</label>
                  <input
                    type="text"
                    value={common.parentId}
                    onChange={(e) => setCommon({ ...common, parentId: e.target.value })}
                    placeholder="Optional — for hierarchical resources"
                    className={inputCls}
                  />
                </div>
              </div>
            </>
          )}

          <div className="mt-6 flex gap-3">
            <button
              type="button"
              disabled={!canAdvanceToStep2}
              onClick={() => setStep(2)}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
            >
              Continue →
            </button>
            <button
              type="button"
              onClick={() => router.back()}
              className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          {kind === "EQUIPMENT" && (
            <EquipmentForm value={equipment} onChange={setEquipment} />
          )}
          {kind === "MATERIAL" && (
            <MaterialForm value={material} onChange={setMaterial} />
          )}
          {kind === "MANPOWER" && (
            <ManpowerForm
              value={manpower}
              onChange={setManpower}
              activeSection={activeManpowerSection}
              onActiveSectionChange={setActiveManpowerSection}
            />
          )}
          {kind === "OTHER" && (
            <div className="rounded-md border border-dashed border-border p-6 text-center text-sm text-text-muted">
              No type-specific fields for this resource type. Click Create to save.
            </div>
          )}

          <div className="mt-6 flex gap-3">
            <button
              type="button"
              onClick={() => setStep(1)}
              className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
            >
              ← Back
            </button>
            <button
              type="button"
              disabled={isSubmitting}
              onClick={handleSubmit}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
            >
              {isSubmitting ? "Creating…" : "Create Resource"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Equipment form
// ────────────────────────────────────────────────────────────────────────────

function EquipmentForm({
  value,
  onChange,
}: {
  value: EquipmentDetailsDto;
  onChange: (v: EquipmentDetailsDto) => void;
}) {
  const set = <K extends keyof EquipmentDetailsDto>(key: K, v: EquipmentDetailsDto[K]) =>
    onChange({ ...value, [key]: v });

  return (
    <div className="space-y-6">
      <Section title="Manufacturer & Model">
        <Field label="Make">
          <input
            className={inputCls}
            value={value.make ?? ""}
            onChange={(e) => set("make", e.target.value || null)}
          />
        </Field>
        <Field label="Model">
          <input
            className={inputCls}
            value={value.model ?? ""}
            onChange={(e) => set("model", e.target.value || null)}
          />
        </Field>
        <Field label="Variant">
          <input
            className={inputCls}
            value={value.variant ?? ""}
            onChange={(e) => set("variant", e.target.value || null)}
          />
        </Field>
        <Field label="Manufacturer Name">
          <input
            className={inputCls}
            value={value.manufacturerName ?? ""}
            onChange={(e) => set("manufacturerName", e.target.value || null)}
          />
        </Field>
        <Field label="Country of Origin">
          <input
            className={inputCls}
            value={value.countryOfOrigin ?? ""}
            onChange={(e) => set("countryOfOrigin", e.target.value || null)}
          />
        </Field>
        <Field label="Year of Manufacture">
          <input
            type="number"
            className={inputCls}
            value={value.yearOfManufacture ?? ""}
            onChange={(e) => set("yearOfManufacture", toIntOrUndef(e.target.value) ?? null)}
          />
        </Field>
      </Section>

      <Section title="Identification">
        <Field label="Serial Number">
          <input
            className={inputCls}
            value={value.serialNumber ?? ""}
            onChange={(e) => set("serialNumber", e.target.value || null)}
          />
        </Field>
        <Field label="Chassis Number">
          <input
            className={inputCls}
            value={value.chassisNumber ?? ""}
            onChange={(e) => set("chassisNumber", e.target.value || null)}
          />
        </Field>
        <Field label="Engine Number">
          <input
            className={inputCls}
            value={value.engineNumber ?? ""}
            onChange={(e) => set("engineNumber", e.target.value || null)}
          />
        </Field>
        <Field label="Registration Number">
          <input
            className={inputCls}
            value={value.registrationNumber ?? ""}
            onChange={(e) => set("registrationNumber", e.target.value || null)}
          />
        </Field>
      </Section>

      <Section title="Specification">
        <Field label="Capacity / Spec">
          <input
            className={inputCls}
            placeholder="1.0 Cum, 60 TPH, 5.5 m"
            value={value.capacitySpec ?? ""}
            onChange={(e) => set("capacitySpec", e.target.value || null)}
          />
        </Field>
        <Field label="Fuel L/Hour">
          <input
            type="number"
            step="0.01"
            className={inputCls}
            value={value.fuelLitresPerHour ?? ""}
            onChange={(e) => set("fuelLitresPerHour", toNumberOrUndef(e.target.value) ?? null)}
          />
        </Field>
        <Field label="Standard Output / Day">
          <input
            type="number"
            step="0.01"
            className={inputCls}
            value={value.standardOutputPerDay ?? ""}
            onChange={(e) => set("standardOutputPerDay", toNumberOrUndef(e.target.value) ?? null)}
          />
        </Field>
        <Field label="Output Unit">
          <input
            className={inputCls}
            value={value.standardOutputUnit ?? ""}
            onChange={(e) => set("standardOutputUnit", e.target.value || null)}
          />
        </Field>
      </Section>

      <Section title="Ownership">
        <Field label="Ownership Type">
          <select
            className={inputCls}
            value={value.ownershipType ?? ""}
            onChange={(e) =>
              set("ownershipType", (e.target.value || null) as ResourceOwnership | null)
            }
          >
            <option value="">—</option>
            <option value="OWNED">Owned</option>
            <option value="HIRED">Hired</option>
            <option value="SUB_CONTRACTOR_PROVIDED">Sub-contractor provided</option>
          </select>
        </Field>
        <Field label="Quantity Available">
          <input
            type="number"
            className={inputCls}
            value={value.quantityAvailable ?? ""}
            onChange={(e) => set("quantityAvailable", toIntOrUndef(e.target.value) ?? null)}
          />
        </Field>
      </Section>

      <Section title="Service & Insurance">
        <Field label="Insurance Expiry">
          <input
            type="date"
            className={inputCls}
            value={value.insuranceExpiry ?? ""}
            onChange={(e) => set("insuranceExpiry", e.target.value || null)}
          />
        </Field>
        <Field label="Last Service Date">
          <input
            type="date"
            className={inputCls}
            value={value.lastServiceDate ?? ""}
            onChange={(e) => set("lastServiceDate", e.target.value || null)}
          />
        </Field>
        <Field label="Next Service Date">
          <input
            type="date"
            className={inputCls}
            value={value.nextServiceDate ?? ""}
            onChange={(e) => set("nextServiceDate", e.target.value || null)}
          />
        </Field>
      </Section>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Material form
// ────────────────────────────────────────────────────────────────────────────

function MaterialForm({
  value,
  onChange,
}: {
  value: MaterialDetailsDto;
  onChange: (v: MaterialDetailsDto) => void;
}) {
  const set = <K extends keyof MaterialDetailsDto>(key: K, v: MaterialDetailsDto[K]) =>
    onChange({ ...value, [key]: v });

  return (
    <div className="space-y-6">
      <Section title="Identification">
        <Field label="Material Type">
          <div className="mt-1 flex gap-4">
            {(["CONSUMABLE", "NON_CONSUMABLE"] as MaterialType[]).map((m) => (
              <label key={m} className="inline-flex items-center gap-2 text-sm text-text-secondary">
                <input
                  type="radio"
                  name="materialType"
                  checked={value.materialType === m}
                  onChange={() => set("materialType", m)}
                />
                {m === "CONSUMABLE" ? "Consumable" : "Non-Consumable"}
              </label>
            ))}
          </div>
        </Field>
        <Field label="Category">
          <input
            className={inputCls}
            value={value.category ?? ""}
            onChange={(e) => set("category", e.target.value || null)}
          />
        </Field>
        <Field label="Sub-Category">
          <input
            className={inputCls}
            value={value.subCategory ?? ""}
            onChange={(e) => set("subCategory", e.target.value || null)}
          />
        </Field>
        <Field label="Material Grade">
          <input
            className={inputCls}
            value={value.materialGrade ?? ""}
            onChange={(e) => set("materialGrade", e.target.value || null)}
          />
        </Field>
        <Field label="Specification" colSpan={2}>
          <input
            className={inputCls}
            value={value.specification ?? ""}
            onChange={(e) => set("specification", e.target.value || null)}
          />
        </Field>
      </Section>

      <Section title="Brand & Standards">
        <Field label="Brand">
          <input
            className={inputCls}
            value={value.brand ?? ""}
            onChange={(e) => set("brand", e.target.value || null)}
          />
        </Field>
        <Field label="Manufacturer Name">
          <input
            className={inputCls}
            value={value.manufacturerName ?? ""}
            onChange={(e) => set("manufacturerName", e.target.value || null)}
          />
        </Field>
        <Field label="Standard Code">
          <input
            className={inputCls}
            placeholder="IS / ASTM / EN…"
            value={value.standardCode ?? ""}
            onChange={(e) => set("standardCode", e.target.value || null)}
          />
        </Field>
        <Field label="Quality Class">
          <input
            className={inputCls}
            value={value.qualityClass ?? ""}
            onChange={(e) => set("qualityClass", e.target.value || null)}
          />
        </Field>
      </Section>

      <Section title="Units & Density">
        <Field label="Base Unit">
          <input
            className={inputCls}
            placeholder="MT, Cum, Bags, Litre…"
            value={value.baseUnit ?? ""}
            onChange={(e) => set("baseUnit", e.target.value || null)}
          />
        </Field>
        <Field label="Conversion Factor">
          <input
            type="number"
            step="0.001"
            className={inputCls}
            value={value.conversionFactor ?? ""}
            onChange={(e) => set("conversionFactor", toNumberOrUndef(e.target.value) ?? null)}
          />
        </Field>
        <Field label="Density">
          <input
            type="number"
            step="0.001"
            className={inputCls}
            value={value.density ?? ""}
            onChange={(e) => set("density", toNumberOrUndef(e.target.value) ?? null)}
          />
        </Field>
        <Field label="Alternate Units (JSON)" colSpan={2}>
          <textarea
            className={inputCls}
            rows={3}
            placeholder='{"bags": 50, "kg-per-bag": 1}'
            value={value.alternateUnits ?? ""}
            onChange={(e) => set("alternateUnits", e.target.value || null)}
          />
          <p className="mt-1 text-xs text-text-muted">
            Free-form JSON. Validated on submit.
          </p>
        </Field>
      </Section>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Manpower form
// ────────────────────────────────────────────────────────────────────────────

type ManpowerSection =
  | "master"
  | "skills"
  | "financials"
  | "attendance"
  | "allocation"
  | "compliance";

function ManpowerForm({
  value,
  onChange,
  activeSection,
  onActiveSectionChange,
}: {
  value: ManpowerDto;
  onChange: (v: ManpowerDto) => void;
  activeSection: ManpowerSection;
  onActiveSectionChange: (s: ManpowerSection) => void;
}) {
  const sections: { key: ManpowerSection; label: string }[] = [
    { key: "master", label: "Master" },
    { key: "skills", label: "Skills" },
    { key: "financials", label: "Financial" },
    { key: "attendance", label: "Attendance" },
    { key: "allocation", label: "Allocation" },
    { key: "compliance", label: "Compliance" },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2 border-b border-border pb-3">
        {sections.map((s) => (
          <button
            key={s.key}
            type="button"
            onClick={() => onActiveSectionChange(s.key)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              activeSection === s.key
                ? "bg-accent text-text-primary"
                : "border border-border text-text-secondary hover:bg-surface-hover/50"
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>

      {activeSection === "master" && (
        <ManpowerMasterFields value={value} onChange={onChange} />
      )}
      {activeSection === "skills" && (
        <ManpowerSkillsFields value={value} onChange={onChange} />
      )}
      {activeSection === "financials" && (
        <ManpowerFinancialsFields value={value} onChange={onChange} />
      )}
      {activeSection === "attendance" && (
        <ManpowerAttendanceFields value={value} onChange={onChange} />
      )}
      {activeSection === "allocation" && (
        <ManpowerAllocationFields value={value} onChange={onChange} />
      )}
      {activeSection === "compliance" && (
        <ManpowerComplianceFields value={value} onChange={onChange} />
      )}
    </div>
  );
}

function ManpowerMasterFields({
  value,
  onChange,
}: {
  value: ManpowerDto;
  onChange: (v: ManpowerDto) => void;
}) {
  const m = value.master ?? {};
  const set = (patch: Partial<typeof m>) =>
    onChange({ ...value, master: { ...m, ...patch } });

  return (
    <Section title="Personal & Employment">
      <Field label="Employee Code">
        <input
          className={inputCls}
          value={m.employeeCode ?? ""}
          onChange={(e) => set({ employeeCode: e.target.value || null })}
        />
      </Field>
      <Field label="Full Name (display)">
        <input
          className={inputCls}
          value={m.fullName ?? ""}
          onChange={(e) => set({ fullName: e.target.value || null })}
        />
      </Field>
      <Field label="First Name">
        <input
          className={inputCls}
          value={m.firstName ?? ""}
          onChange={(e) => set({ firstName: e.target.value || null })}
        />
      </Field>
      <Field label="Last Name">
        <input
          className={inputCls}
          value={m.lastName ?? ""}
          onChange={(e) => set({ lastName: e.target.value || null })}
        />
      </Field>
      <Field label="Category">
        <select
          className={inputCls}
          value={m.category ?? ""}
          onChange={(e) =>
            set({ category: (e.target.value || null) as ManpowerCategory | null })
          }
        >
          <option value="">—</option>
          <option value="SKILLED">Skilled</option>
          <option value="UNSKILLED">Unskilled</option>
          <option value="STAFF">Staff</option>
        </select>
      </Field>
      <Field label="Sub-Category">
        <input
          className={inputCls}
          value={m.subCategory ?? ""}
          onChange={(e) => set({ subCategory: e.target.value || null })}
        />
      </Field>
      <Field label="Date of Birth">
        <input
          type="date"
          className={inputCls}
          value={m.dateOfBirth ?? ""}
          onChange={(e) => set({ dateOfBirth: e.target.value || null })}
        />
      </Field>
      <Field label="Gender">
        <input
          className={inputCls}
          value={m.gender ?? ""}
          onChange={(e) => set({ gender: e.target.value || null })}
        />
      </Field>
      <Field label="Nationality">
        <input
          className={inputCls}
          value={m.nationality ?? ""}
          onChange={(e) => set({ nationality: e.target.value || null })}
        />
      </Field>
      <Field label="Contact Number">
        <input
          className={inputCls}
          value={m.contactNumber ?? ""}
          onChange={(e) => set({ contactNumber: e.target.value || null })}
        />
      </Field>
      <Field label="Email">
        <input
          type="email"
          className={inputCls}
          value={m.email ?? ""}
          onChange={(e) => set({ email: e.target.value || null })}
        />
      </Field>
      <Field label="Address" colSpan={2}>
        <input
          className={inputCls}
          value={m.address ?? ""}
          onChange={(e) => set({ address: e.target.value || null })}
        />
      </Field>
      <Field label="Emergency Contact">
        <input
          className={inputCls}
          value={m.emergencyContact ?? ""}
          onChange={(e) => set({ emergencyContact: e.target.value || null })}
        />
      </Field>
      <Field label="Photo URL">
        <input
          className={inputCls}
          value={m.photoUrl ?? ""}
          onChange={(e) => set({ photoUrl: e.target.value || null })}
        />
      </Field>
      <Field label="Employment Type">
        <select
          className={inputCls}
          value={m.employmentType ?? ""}
          onChange={(e) =>
            set({ employmentType: (e.target.value || null) as EmploymentType | null })
          }
        >
          <option value="">—</option>
          <option value="PERMANENT">Permanent</option>
          <option value="CONTRACT">Contract</option>
          <option value="DAILY_WAGE">Daily Wage</option>
        </select>
      </Field>
      <Field label="Designation">
        <input
          className={inputCls}
          value={m.designation ?? ""}
          onChange={(e) => set({ designation: e.target.value || null })}
        />
      </Field>
      <Field label="Department">
        <input
          className={inputCls}
          value={m.department ?? ""}
          onChange={(e) => set({ department: e.target.value || null })}
        />
      </Field>
      <Field label="Joining Date">
        <input
          type="date"
          className={inputCls}
          value={m.joiningDate ?? ""}
          onChange={(e) => set({ joiningDate: e.target.value || null })}
        />
      </Field>
      <Field label="Exit Date">
        <input
          type="date"
          className={inputCls}
          value={m.exitDate ?? ""}
          onChange={(e) => set({ exitDate: e.target.value || null })}
        />
      </Field>
      <Field label="Reporting Manager (UUID)">
        <input
          className={inputCls}
          value={m.reportingManagerId ?? ""}
          onChange={(e) => set({ reportingManagerId: e.target.value || null })}
        />
      </Field>
      <Field label="Company Name">
        <input
          className={inputCls}
          value={m.companyName ?? ""}
          onChange={(e) => set({ companyName: e.target.value || null })}
        />
      </Field>
      <Field label="Work Location">
        <input
          className={inputCls}
          value={m.workLocation ?? ""}
          onChange={(e) => set({ workLocation: e.target.value || null })}
        />
      </Field>
    </Section>
  );
}

function ManpowerSkillsFields({
  value,
  onChange,
}: {
  value: ManpowerDto;
  onChange: (v: ManpowerDto) => void;
}) {
  const s = value.skills ?? {};
  const set = (patch: Partial<typeof s>) =>
    onChange({ ...value, skills: { ...s, ...patch } });
  return (
    <Section title="Skills & Certifications">
      <Field label="Primary Skill">
        <input
          className={inputCls}
          value={s.primarySkill ?? ""}
          onChange={(e) => set({ primarySkill: e.target.value || null })}
        />
      </Field>
      <Field label="Skill Level">
        <select
          className={inputCls}
          value={s.skillLevel ?? ""}
          onChange={(e) =>
            set({ skillLevel: (e.target.value || null) as SkillLevel | null })
          }
        >
          <option value="">—</option>
          <option value="BEGINNER">Beginner</option>
          <option value="INTERMEDIATE">Intermediate</option>
          <option value="EXPERT">Expert</option>
        </select>
      </Field>
      <Field label="Experience (years)">
        <input
          type="number"
          className={inputCls}
          value={s.experienceYears ?? ""}
          onChange={(e) => set({ experienceYears: toIntOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="License Details">
        <input
          className={inputCls}
          value={s.licenseDetails ?? ""}
          onChange={(e) => set({ licenseDetails: e.target.value || null })}
        />
      </Field>
      <Field label="Secondary Skills (JSON)" colSpan={2}>
        <textarea
          className={inputCls}
          rows={3}
          placeholder='["welding","plumbing"]'
          value={s.secondarySkills ?? ""}
          onChange={(e) => set({ secondarySkills: e.target.value || null })}
        />
      </Field>
      <Field label="Certifications" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={s.certifications ?? ""}
          onChange={(e) => set({ certifications: e.target.value || null })}
        />
      </Field>
      <Field label="Training Records" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={s.trainingRecords ?? ""}
          onChange={(e) => set({ trainingRecords: e.target.value || null })}
        />
      </Field>
    </Section>
  );
}

function ManpowerFinancialsFields({
  value,
  onChange,
}: {
  value: ManpowerDto;
  onChange: (v: ManpowerDto) => void;
}) {
  const f = value.financials ?? {};
  const set = (patch: Partial<typeof f>) =>
    onChange({ ...value, financials: { ...f, ...patch } });
  return (
    <Section title="Financial">
      <Field label="Salary Type">
        <select
          className={inputCls}
          value={f.salaryType ?? ""}
          onChange={(e) =>
            set({ salaryType: (e.target.value || null) as SalaryType | null })
          }
        >
          <option value="">—</option>
          <option value="MONTHLY">Monthly</option>
          <option value="DAILY">Daily</option>
          <option value="HOURLY">Hourly</option>
        </select>
      </Field>
      <Field label="Currency">
        <input
          className={inputCls}
          value={f.currency ?? ""}
          onChange={(e) => set({ currency: e.target.value || null })}
        />
      </Field>
      <Field label="Base Salary">
        <input
          type="number"
          step="0.01"
          className={inputCls}
          value={f.baseSalary ?? ""}
          onChange={(e) => set({ baseSalary: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Hourly Rate">
        <input
          type="number"
          step="0.01"
          className={inputCls}
          value={f.hourlyRate ?? ""}
          onChange={(e) => set({ hourlyRate: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Overtime Rate">
        <input
          type="number"
          step="0.01"
          className={inputCls}
          value={f.overtimeRate ?? ""}
          onChange={(e) => set({ overtimeRate: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Payment Mode">
        <select
          className={inputCls}
          value={f.paymentMode ?? ""}
          onChange={(e) =>
            set({ paymentMode: (e.target.value || null) as PaymentMode | null })
          }
        >
          <option value="">—</option>
          <option value="BANK">Bank</option>
          <option value="CASH">Cash</option>
          <option value="CHEQUE">Cheque</option>
        </select>
      </Field>
      <Field label="Allowances (JSON)" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={f.allowances ?? ""}
          onChange={(e) => set({ allowances: e.target.value || null })}
        />
      </Field>
      <Field label="Deductions (JSON)" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={f.deductions ?? ""}
          onChange={(e) => set({ deductions: e.target.value || null })}
        />
      </Field>
      <Field label="Bank Account Details" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={f.bankAccountDetails ?? ""}
          onChange={(e) => set({ bankAccountDetails: e.target.value || null })}
        />
      </Field>
      <Field label="Tax Details" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={f.taxDetails ?? ""}
          onChange={(e) => set({ taxDetails: e.target.value || null })}
        />
      </Field>
      <Field label="PF Number">
        <input
          className={inputCls}
          value={f.pfNumber ?? ""}
          onChange={(e) => set({ pfNumber: e.target.value || null })}
        />
      </Field>
      <Field label="ESI Number">
        <input
          className={inputCls}
          value={f.esiNumber ?? ""}
          onChange={(e) => set({ esiNumber: e.target.value || null })}
        />
      </Field>
    </Section>
  );
}

function ManpowerAttendanceFields({
  value,
  onChange,
}: {
  value: ManpowerDto;
  onChange: (v: ManpowerDto) => void;
}) {
  const a = value.attendance ?? {};
  const set = (patch: Partial<typeof a>) =>
    onChange({ ...value, attendance: { ...a, ...patch } });
  return (
    <Section title="Attendance & Hours">
      <Field label="Daily Status">
        <select
          className={inputCls}
          value={a.dailyAttendanceStatus ?? ""}
          onChange={(e) =>
            set({
              dailyAttendanceStatus: (e.target.value || null) as AttendanceStatus | null,
            })
          }
        >
          <option value="">—</option>
          <option value="PRESENT">Present</option>
          <option value="ABSENT">Absent</option>
          <option value="ON_LEAVE">On leave</option>
          <option value="HALF_DAY">Half day</option>
        </select>
      </Field>
      <Field label="Shift">
        <select
          className={inputCls}
          value={a.shiftType ?? ""}
          onChange={(e) =>
            set({ shiftType: (e.target.value || null) as ShiftType | null })
          }
        >
          <option value="">—</option>
          <option value="DAY">Day</option>
          <option value="NIGHT">Night</option>
        </select>
      </Field>
      <Field label="Last Check-In (ISO)">
        <input
          className={inputCls}
          value={a.lastCheckInTime ?? ""}
          onChange={(e) => set({ lastCheckInTime: e.target.value || null })}
        />
      </Field>
      <Field label="Last Check-Out (ISO)">
        <input
          className={inputCls}
          value={a.lastCheckOutTime ?? ""}
          onChange={(e) => set({ lastCheckOutTime: e.target.value || null })}
        />
      </Field>
      <Field label="Working Hours / Day">
        <input
          type="number"
          step="0.1"
          className={inputCls}
          value={a.workingHoursPerDay ?? ""}
          onChange={(e) => set({ workingHoursPerDay: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Total Work Hours MTD">
        <input
          type="number"
          step="0.1"
          className={inputCls}
          value={a.totalWorkHoursMtd ?? ""}
          onChange={(e) => set({ totalWorkHoursMtd: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Overtime Hours MTD">
        <input
          type="number"
          step="0.1"
          className={inputCls}
          value={a.overtimeHoursMtd ?? ""}
          onChange={(e) => set({ overtimeHoursMtd: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Leave Balance">
        <input
          type="number"
          step="0.5"
          className={inputCls}
          value={a.leaveBalance ?? ""}
          onChange={(e) => set({ leaveBalance: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Leave Schedule (JSON)" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={a.leaveSchedule ?? ""}
          onChange={(e) => set({ leaveSchedule: e.target.value || null })}
        />
      </Field>
    </Section>
  );
}

function ManpowerAllocationFields({
  value,
  onChange,
}: {
  value: ManpowerDto;
  onChange: (v: ManpowerDto) => void;
}) {
  const a = value.allocation ?? {};
  const set = (patch: Partial<typeof a>) =>
    onChange({ ...value, allocation: { ...a, ...patch } });
  return (
    <Section title="Allocation & Performance">
      <Field label="Availability Status">
        <select
          className={inputCls}
          value={a.availabilityStatus ?? ""}
          onChange={(e) =>
            set({
              availabilityStatus: (e.target.value || null) as AvailabilityStatus | null,
            })
          }
        >
          <option value="">—</option>
          <option value="AVAILABLE">Available</option>
          <option value="ASSIGNED">Assigned</option>
          <option value="ON_LEAVE">On leave</option>
        </select>
      </Field>
      <Field label="Site Name">
        <input
          className={inputCls}
          value={a.siteName ?? ""}
          onChange={(e) => set({ siteName: e.target.value || null })}
        />
      </Field>
      <Field label="Current Project (UUID)">
        <input
          className={inputCls}
          value={a.currentProjectId ?? ""}
          onChange={(e) => set({ currentProjectId: e.target.value || null })}
        />
      </Field>
      <Field label="Assigned Activity (UUID)">
        <input
          className={inputCls}
          value={a.assignedActivityId ?? ""}
          onChange={(e) => set({ assignedActivityId: e.target.value || null })}
        />
      </Field>
      <Field label="Role in Project">
        <input
          className={inputCls}
          value={a.roleInProject ?? ""}
          onChange={(e) => set({ roleInProject: e.target.value || null })}
        />
      </Field>
      <Field label="Crew ID (UUID)">
        <input
          className={inputCls}
          value={a.crewId ?? ""}
          onChange={(e) => set({ crewId: e.target.value || null })}
        />
      </Field>
      <Field label="Utilization %">
        <input
          type="number"
          step="0.01"
          className={inputCls}
          value={a.utilizationPercentage ?? ""}
          onChange={(e) => set({ utilizationPercentage: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Standard Output / Hour">
        <input
          type="number"
          step="0.01"
          className={inputCls}
          value={a.standardOutputPerHour ?? ""}
          onChange={(e) => set({ standardOutputPerHour: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Output Unit">
        <input
          className={inputCls}
          value={a.outputUnit ?? ""}
          onChange={(e) => set({ outputUnit: e.target.value || null })}
        />
      </Field>
      <Field label="Efficiency Factor">
        <input
          type="number"
          step="0.01"
          className={inputCls}
          value={a.efficiencyFactor ?? ""}
          onChange={(e) => set({ efficiencyFactor: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Performance Rating">
        <input
          type="number"
          step="0.01"
          className={inputCls}
          value={a.performanceRating ?? ""}
          onChange={(e) => set({ performanceRating: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Productivity Trend">
        <input
          className={inputCls}
          value={a.productivityTrend ?? ""}
          onChange={(e) => set({ productivityTrend: e.target.value || null })}
        />
      </Field>
      <Field label="Attrition Risk Score">
        <input
          type="number"
          step="0.01"
          className={inputCls}
          value={a.attritionRiskScore ?? ""}
          onChange={(e) => set({ attritionRiskScore: toNumberOrUndef(e.target.value) ?? null })}
        />
      </Field>
      <Field label="Skill Gap Analysis" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={a.skillGapAnalysis ?? ""}
          onChange={(e) => set({ skillGapAnalysis: e.target.value || null })}
        />
      </Field>
      <Field label="Recommended Training" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={a.recommendedTraining ?? ""}
          onChange={(e) => set({ recommendedTraining: e.target.value || null })}
        />
      </Field>
      <Field label="Optimal Assignment" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={a.optimalAssignment ?? ""}
          onChange={(e) => set({ optimalAssignment: e.target.value || null })}
        />
      </Field>
    </Section>
  );
}

function ManpowerComplianceFields({
  value,
  onChange,
}: {
  value: ManpowerDto;
  onChange: (v: ManpowerDto) => void;
}) {
  const c = value.compliance ?? {};
  const set = (patch: Partial<typeof c>) =>
    onChange({ ...value, compliance: { ...c, ...patch } });
  return (
    <Section title="Compliance & Documents">
      <Field label="ID Proof Type">
        <input
          className={inputCls}
          value={c.idProofType ?? ""}
          onChange={(e) => set({ idProofType: e.target.value || null })}
        />
      </Field>
      <Field label="ID Proof Number">
        <input
          className={inputCls}
          value={c.idProofNumber ?? ""}
          onChange={(e) => set({ idProofNumber: e.target.value || null })}
        />
      </Field>
      <Field label="Labor License Number">
        <input
          className={inputCls}
          value={c.laborLicenseNumber ?? ""}
          onChange={(e) => set({ laborLicenseNumber: e.target.value || null })}
        />
      </Field>
      <Field label="Insurance Provider">
        <input
          className={inputCls}
          value={c.insuranceProvider ?? ""}
          onChange={(e) => set({ insuranceProvider: e.target.value || null })}
        />
      </Field>
      <Field label="Insurance Policy Number">
        <input
          className={inputCls}
          value={c.insurancePolicyNumber ?? ""}
          onChange={(e) => set({ insurancePolicyNumber: e.target.value || null })}
        />
      </Field>
      <Field label="Insurance Expiry">
        <input
          type="date"
          className={inputCls}
          value={c.insuranceExpiry ?? ""}
          onChange={(e) => set({ insuranceExpiry: e.target.value || null })}
        />
      </Field>
      <Field label="Medical Status">
        <select
          className={inputCls}
          value={c.medicalFitnessStatus ?? ""}
          onChange={(e) =>
            set({
              medicalFitnessStatus: (e.target.value || null) as MedicalStatus | null,
            })
          }
        >
          <option value="">—</option>
          <option value="FIT">Fit</option>
          <option value="UNFIT">Unfit</option>
          <option value="PENDING">Pending</option>
        </select>
      </Field>
      <Field label="Medical Expiry">
        <input
          type="date"
          className={inputCls}
          value={c.medicalExpiry ?? ""}
          onChange={(e) => set({ medicalExpiry: e.target.value || null })}
        />
      </Field>
      <Field label="Safety Training Completed">
        <select
          className={inputCls}
          value={c.safetyTrainingCompleted == null ? "" : c.safetyTrainingCompleted ? "yes" : "no"}
          onChange={(e) =>
            set({
              safetyTrainingCompleted:
                e.target.value === "" ? null : e.target.value === "yes",
            })
          }
        >
          <option value="">—</option>
          <option value="yes">Yes</option>
          <option value="no">No</option>
        </select>
      </Field>
      <Field label="Safety Training Date">
        <input
          type="date"
          className={inputCls}
          value={c.safetyTrainingDate ?? ""}
          onChange={(e) => set({ safetyTrainingDate: e.target.value || null })}
        />
      </Field>
      <Field label="Resume URL" colSpan={2}>
        <input
          className={inputCls}
          value={c.resumeUrl ?? ""}
          onChange={(e) => set({ resumeUrl: e.target.value || null })}
        />
      </Field>
      <Field label="Compliance Certificates" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={c.complianceCertificates ?? ""}
          onChange={(e) => set({ complianceCertificates: e.target.value || null })}
        />
      </Field>
      <Field label="Certification Documents" colSpan={2}>
        <textarea
          className={inputCls}
          rows={2}
          value={c.certificationDocuments ?? ""}
          onChange={(e) => set({ certificationDocuments: e.target.value || null })}
        />
      </Field>
      <Field label="Contract Document URL" colSpan={2}>
        <input
          className={inputCls}
          value={c.contractDocumentUrl ?? ""}
          onChange={(e) => set({ contractDocumentUrl: e.target.value || null })}
        />
      </Field>
    </Section>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Layout helpers
// ────────────────────────────────────────────────────────────────────────────

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-text-secondary">
        {title}
      </h3>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">{children}</div>
    </div>
  );
}

function Field({
  label,
  children,
  colSpan,
}: {
  label: string;
  children: React.ReactNode;
  colSpan?: 1 | 2;
}) {
  return (
    <div className={colSpan === 2 ? "md:col-span-2" : ""}>
      <label className={labelCls}>{label}</label>
      {children}
    </div>
  );
}
