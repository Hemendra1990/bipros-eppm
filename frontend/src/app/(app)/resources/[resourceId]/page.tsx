"use client";

import { useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { ArrowLeft, Pencil } from "lucide-react";
import {
  resourceApi,
  type EquipmentDetailsDto,
  type MaterialDetailsDto,
  type ManpowerDto,
  type ResourceResponse,
} from "@/lib/api/resourceApi";
import { manpowerCategoryMasterApi } from "@/lib/api/manpowerCategoryMasterApi";
import { employmentTypeMasterApi } from "@/lib/api/employmentTypeMasterApi";
import { skillMasterApi } from "@/lib/api/skillMasterApi";
import { skillLevelMasterApi } from "@/lib/api/skillLevelMasterApi";
import { nationalityMasterApi } from "@/lib/api/nationalityMasterApi";
import { Breadcrumb } from "@/components/common/Breadcrumb";
import { StatusBadge } from "@/components/common/StatusBadge";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { MultiSelect } from "@/components/common/MultiSelect";
import { getErrorMessage } from "@/lib/utils/error";

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

type EquipmentTab = "OVERVIEW" | "MANUFACTURER" | "SERVICE";
type MaterialTab = "OVERVIEW" | "SPECIFICATION" | "UNITS";
// FINANCIAL / ATTENDANCE / ALLOCATION / COMPLIANCE tabs hidden for now (entities + DB columns
// retained — only the UI surface is removed).
type ManpowerTab = "OVERVIEW" | "PERSONAL" | "EMPLOYMENT" | "SKILLS";

export default function ResourceDetailPage() {
  const params = useParams<{ resourceId: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const resourceId = params.resourceId;

  const { data, isLoading, error } = useQuery({
    queryKey: ["resource", resourceId],
    queryFn: () => resourceApi.getResource(resourceId),
    enabled: !!resourceId,
  });

  const resource: ResourceResponse | undefined = data?.data ?? undefined;
  const kind = classifyType(resource?.resourceTypeCode);

  // Tab state per kind
  const [equipmentTab, setEquipmentTab] = useState<EquipmentTab>("OVERVIEW");
  const [materialTab, setMaterialTab] = useState<MaterialTab>("OVERVIEW");
  const [manpowerTab, setManpowerTab] = useState<ManpowerTab>("OVERVIEW");

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["resource", resourceId] });
    queryClient.invalidateQueries({ queryKey: ["resources"] });
  };

  if (isLoading) {
    return <div className="p-6 text-text-muted">Loading…</div>;
  }
  if (error || !resource) {
    return (
      <div className="p-6">
        <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">
          {error ? getErrorMessage(error, "Failed to load resource") : "Resource not found"}
        </div>
        <button
          type="button"
          onClick={() => router.push("/resources")}
          className="mt-4 text-accent hover:underline text-sm"
        >
          ← Back to Resources
        </button>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-4">
        <Breadcrumb
          items={[
            { label: "Resources", href: "/resources" },
            {
              label: resource.code,
              href: `/resources/${resource.id}`,
              active: true,
            },
          ]}
        />
      </div>

      {/* Header */}
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <button
            type="button"
            onClick={() => router.push("/resources")}
            className="mb-2 inline-flex items-center gap-1 text-xs text-text-secondary hover:text-accent"
          >
            <ArrowLeft size={12} /> Back
          </button>
          <h1 className="text-3xl font-bold text-text-primary">{resource.name}</h1>
          <p className="mt-1 text-sm text-text-secondary">
            <span className="font-mono">{resource.code}</span>
            {" · "}
            <span>{resource.resourceTypeName}</span>
            {" · "}
            <span>{resource.roleName}</span>
            {resource.availability != null && (
              <>
                {" · Availability "}
                <span className="font-medium text-text-primary">{resource.availability}</span>
              </>
            )}
          </p>
          {resource.description && (
            <p className="mt-2 text-sm text-text-secondary max-w-2xl">{resource.description}</p>
          )}
        </div>
        <StatusBadge status={resource.status} />
      </div>

      {/* Tabs by kind */}
      {kind === "EQUIPMENT" && (
        <EquipmentTabs
          resource={resource}
          tab={equipmentTab}
          onTabChange={setEquipmentTab}
          onUpdated={invalidate}
        />
      )}
      {kind === "MATERIAL" && (
        <MaterialTabs
          resource={resource}
          tab={materialTab}
          onTabChange={setMaterialTab}
          onUpdated={invalidate}
        />
      )}
      {kind === "MANPOWER" && (
        <ManpowerTabs
          resource={resource}
          tab={manpowerTab}
          onTabChange={setManpowerTab}
          onUpdated={invalidate}
        />
      )}
      {kind === "OTHER" && (
        <div className="rounded-xl border border-dashed border-border bg-surface/30 p-8 text-center text-sm text-text-muted">
          No type-specific detail available for this resource type.
        </div>
      )}
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Equipment tabs
// ────────────────────────────────────────────────────────────────────────────

function EquipmentTabs({
  resource,
  tab,
  onTabChange,
  onUpdated,
}: {
  resource: ResourceResponse;
  tab: EquipmentTab;
  onTabChange: (t: EquipmentTab) => void;
  onUpdated: () => void;
}) {
  const tabs: { key: EquipmentTab; label: string }[] = [
    { key: "OVERVIEW", label: "Overview" },
    { key: "MANUFACTURER", label: "Manufacturer" },
    { key: "SERVICE", label: "Service & Insurance" },
  ];

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<EquipmentDetailsDto>({});

  const e = resource.equipment ?? {};

  const mutation = useMutation({
    mutationFn: (dto: EquipmentDetailsDto) =>
      resourceApi.updateEquipmentDetails(resource.id, dto),
    onSuccess: () => {
      toast.success("Equipment details updated");
      setEditing(false);
      onUpdated();
    },
    onError: (err: unknown) =>
      toast.error(getErrorMessage(err, "Failed to update equipment details")),
  });

  return (
    <>
      <Tabs items={tabs} value={tab} onChange={onTabChange} />

      <div className="mt-4 rounded-xl border border-border bg-surface/50 p-6">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-text-primary">
            {tabs.find((t) => t.key === tab)?.label}
          </h2>
          {!editing ? (
            <button
              type="button"
              onClick={() => {
                setDraft(e);
                setEditing(true);
              }}
              className="inline-flex items-center gap-1 rounded-md border border-border bg-surface-hover px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-active"
            >
              <Pencil size={12} /> Edit Section
            </button>
          ) : (
            <div className="flex gap-2">
              <button
                type="button"
                disabled={mutation.isPending}
                onClick={() => mutation.mutate(draft)}
                className="rounded-md bg-accent px-3 py-1.5 text-xs font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                {mutation.isPending ? "Saving…" : "Save"}
              </button>
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="rounded-md border border-border bg-surface-hover px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-active"
              >
                Cancel
              </button>
            </div>
          )}
        </div>

        {tab === "OVERVIEW" && (
          <ReadOnlyGrid
            entries={[
              ["Capacity / Spec", e.capacitySpec],
              ["Standard Output / Day", e.standardOutputPerDay],
              ["Standard Output Unit", e.standardOutputUnit],
              ["Fuel L/Hour", e.fuelLitresPerHour],
              ["Ownership Type", e.ownershipType],
              ["Quantity Available", e.quantityAvailable],
            ]}
          />
        )}
        {tab === "MANUFACTURER" && !editing && (
          <ReadOnlyGrid
            entries={[
              ["Make", e.make],
              ["Model", e.model],
              ["Variant", e.variant],
              ["Manufacturer", e.manufacturerName],
              ["Country of Origin", e.countryOfOrigin],
              ["Year of Manufacture", e.yearOfManufacture],
              ["Serial Number", e.serialNumber],
              ["Chassis Number", e.chassisNumber],
              ["Engine Number", e.engineNumber],
              ["Registration Number", e.registrationNumber],
            ]}
          />
        )}
        {tab === "MANUFACTURER" && editing && (
          <EquipmentManufacturerEditor draft={draft} setDraft={setDraft} />
        )}
        {tab === "SERVICE" && !editing && (
          <ReadOnlyGrid
            entries={[
              ["Insurance Expiry", e.insuranceExpiry],
              ["Last Service Date", e.lastServiceDate],
              ["Next Service Date", e.nextServiceDate],
            ]}
          />
        )}
        {tab === "SERVICE" && editing && (
          <EquipmentServiceEditor draft={draft} setDraft={setDraft} />
        )}
        {tab === "OVERVIEW" && editing && (
          <EquipmentOverviewEditor draft={draft} setDraft={setDraft} />
        )}
      </div>
    </>
  );
}

function EquipmentOverviewEditor({
  draft,
  setDraft,
}: {
  draft: EquipmentDetailsDto;
  setDraft: (d: EquipmentDetailsDto) => void;
}) {
  return (
    <Grid>
      <TextField
        label="Capacity / Spec"
        value={draft.capacitySpec ?? ""}
        onChange={(v) => setDraft({ ...draft, capacitySpec: v || null })}
      />
      <NumberField
        label="Standard Output / Day"
        value={draft.standardOutputPerDay}
        onChange={(v) => setDraft({ ...draft, standardOutputPerDay: v })}
      />
      <TextField
        label="Standard Output Unit"
        value={draft.standardOutputUnit ?? ""}
        onChange={(v) => setDraft({ ...draft, standardOutputUnit: v || null })}
      />
      <NumberField
        label="Fuel L/Hour"
        value={draft.fuelLitresPerHour}
        onChange={(v) => setDraft({ ...draft, fuelLitresPerHour: v })}
      />
      <SelectField
        label="Ownership Type"
        value={draft.ownershipType ?? ""}
        onChange={(v) =>
          setDraft({
            ...draft,
            ownershipType:
              (v || null) as EquipmentDetailsDto["ownershipType"],
          })
        }
        options={[
          { value: "", label: "—" },
          { value: "OWNED", label: "Owned" },
          { value: "HIRED", label: "Hired" },
          { value: "SUB_CONTRACTOR_PROVIDED", label: "Sub-contractor" },
        ]}
      />
      <NumberField
        label="Quantity Available"
        value={draft.quantityAvailable}
        onChange={(v) => setDraft({ ...draft, quantityAvailable: v == null ? null : Math.trunc(v) })}
      />
    </Grid>
  );
}

function EquipmentManufacturerEditor({
  draft,
  setDraft,
}: {
  draft: EquipmentDetailsDto;
  setDraft: (d: EquipmentDetailsDto) => void;
}) {
  return (
    <Grid>
      <TextField label="Make" value={draft.make ?? ""} onChange={(v) => setDraft({ ...draft, make: v || null })} />
      <TextField label="Model" value={draft.model ?? ""} onChange={(v) => setDraft({ ...draft, model: v || null })} />
      <TextField label="Variant" value={draft.variant ?? ""} onChange={(v) => setDraft({ ...draft, variant: v || null })} />
      <TextField label="Manufacturer" value={draft.manufacturerName ?? ""} onChange={(v) => setDraft({ ...draft, manufacturerName: v || null })} />
      <TextField label="Country of Origin" value={draft.countryOfOrigin ?? ""} onChange={(v) => setDraft({ ...draft, countryOfOrigin: v || null })} />
      <NumberField label="Year of Manufacture" value={draft.yearOfManufacture} onChange={(v) => setDraft({ ...draft, yearOfManufacture: v == null ? null : Math.trunc(v) })} />
      <TextField label="Serial Number" value={draft.serialNumber ?? ""} onChange={(v) => setDraft({ ...draft, serialNumber: v || null })} />
      <TextField label="Chassis Number" value={draft.chassisNumber ?? ""} onChange={(v) => setDraft({ ...draft, chassisNumber: v || null })} />
      <TextField label="Engine Number" value={draft.engineNumber ?? ""} onChange={(v) => setDraft({ ...draft, engineNumber: v || null })} />
      <TextField label="Registration Number" value={draft.registrationNumber ?? ""} onChange={(v) => setDraft({ ...draft, registrationNumber: v || null })} />
    </Grid>
  );
}

function EquipmentServiceEditor({
  draft,
  setDraft,
}: {
  draft: EquipmentDetailsDto;
  setDraft: (d: EquipmentDetailsDto) => void;
}) {
  return (
    <Grid>
      <DateField label="Insurance Expiry" value={draft.insuranceExpiry ?? ""} onChange={(v) => setDraft({ ...draft, insuranceExpiry: v || null })} />
      <DateField label="Last Service Date" value={draft.lastServiceDate ?? ""} onChange={(v) => setDraft({ ...draft, lastServiceDate: v || null })} />
      <DateField label="Next Service Date" value={draft.nextServiceDate ?? ""} onChange={(v) => setDraft({ ...draft, nextServiceDate: v || null })} />
    </Grid>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Material tabs
// ────────────────────────────────────────────────────────────────────────────

function MaterialTabs({
  resource,
  tab,
  onTabChange,
  onUpdated,
}: {
  resource: ResourceResponse;
  tab: MaterialTab;
  onTabChange: (t: MaterialTab) => void;
  onUpdated: () => void;
}) {
  const tabs: { key: MaterialTab; label: string }[] = [
    { key: "OVERVIEW", label: "Overview" },
    { key: "SPECIFICATION", label: "Specification" },
    { key: "UNITS", label: "Units & Density" },
  ];

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<MaterialDetailsDto>({});
  const m = resource.material ?? {};

  const mutation = useMutation({
    mutationFn: (dto: MaterialDetailsDto) =>
      resourceApi.updateMaterialDetails(resource.id, dto),
    onSuccess: () => {
      toast.success("Material details updated");
      setEditing(false);
      onUpdated();
    },
    onError: (err: unknown) =>
      toast.error(getErrorMessage(err, "Failed to update material details")),
  });

  const handleSave = () => {
    if (draft.alternateUnits) {
      try {
        JSON.parse(draft.alternateUnits);
      } catch {
        toast.error("Alternate Units must be valid JSON");
        return;
      }
    }
    mutation.mutate(draft);
  };

  return (
    <>
      <Tabs items={tabs} value={tab} onChange={onTabChange} />
      <div className="mt-4 rounded-xl border border-border bg-surface/50 p-6">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-text-primary">
            {tabs.find((t) => t.key === tab)?.label}
          </h2>
          {!editing ? (
            <button
              type="button"
              onClick={() => {
                setDraft(m);
                setEditing(true);
              }}
              className="inline-flex items-center gap-1 rounded-md border border-border bg-surface-hover px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-active"
            >
              <Pencil size={12} /> Edit Section
            </button>
          ) : (
            <div className="flex gap-2">
              <button
                type="button"
                disabled={mutation.isPending}
                onClick={handleSave}
                className="rounded-md bg-accent px-3 py-1.5 text-xs font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                {mutation.isPending ? "Saving…" : "Save"}
              </button>
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="rounded-md border border-border bg-surface-hover px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-active"
              >
                Cancel
              </button>
            </div>
          )}
        </div>

        {tab === "OVERVIEW" && !editing && (
          <ReadOnlyGrid
            entries={[
              ["Material Type", m.materialType],
              ["Category", m.category],
              ["Sub-Category", m.subCategory],
              ["Material Grade", m.materialGrade],
              ["Brand", m.brand],
              ["Manufacturer", m.manufacturerName],
            ]}
          />
        )}
        {tab === "OVERVIEW" && editing && (
          <Grid>
            <SelectField
              label="Material Type"
              value={draft.materialType ?? ""}
              onChange={(v) =>
                setDraft({ ...draft, materialType: (v || null) as MaterialDetailsDto["materialType"] })
              }
              options={[
                { value: "", label: "—" },
                { value: "CONSUMABLE", label: "Consumable" },
                { value: "NON_CONSUMABLE", label: "Non-Consumable" },
              ]}
            />
            <TextField label="Category" value={draft.category ?? ""} onChange={(v) => setDraft({ ...draft, category: v || null })} />
            <TextField label="Sub-Category" value={draft.subCategory ?? ""} onChange={(v) => setDraft({ ...draft, subCategory: v || null })} />
            <TextField label="Material Grade" value={draft.materialGrade ?? ""} onChange={(v) => setDraft({ ...draft, materialGrade: v || null })} />
            <TextField label="Brand" value={draft.brand ?? ""} onChange={(v) => setDraft({ ...draft, brand: v || null })} />
            <TextField label="Manufacturer" value={draft.manufacturerName ?? ""} onChange={(v) => setDraft({ ...draft, manufacturerName: v || null })} />
          </Grid>
        )}

        {tab === "SPECIFICATION" && !editing && (
          <ReadOnlyGrid
            entries={[
              ["Specification", m.specification],
              ["Standard Code", m.standardCode],
              ["Quality Class", m.qualityClass],
            ]}
          />
        )}
        {tab === "SPECIFICATION" && editing && (
          <Grid>
            <TextField label="Specification" value={draft.specification ?? ""} onChange={(v) => setDraft({ ...draft, specification: v || null })} colSpan={2} />
            <TextField label="Standard Code" value={draft.standardCode ?? ""} onChange={(v) => setDraft({ ...draft, standardCode: v || null })} />
            <TextField label="Quality Class" value={draft.qualityClass ?? ""} onChange={(v) => setDraft({ ...draft, qualityClass: v || null })} />
          </Grid>
        )}

        {tab === "UNITS" && !editing && (
          <ReadOnlyGrid
            entries={[
              ["Base Unit", m.baseUnit],
              ["Conversion Factor", m.conversionFactor],
              ["Density", m.density],
              ["Alternate Units (JSON)", m.alternateUnits],
            ]}
          />
        )}
        {tab === "UNITS" && editing && (
          <Grid>
            <TextField label="Base Unit" value={draft.baseUnit ?? ""} onChange={(v) => setDraft({ ...draft, baseUnit: v || null })} />
            <NumberField label="Conversion Factor" value={draft.conversionFactor} onChange={(v) => setDraft({ ...draft, conversionFactor: v })} />
            <NumberField label="Density" value={draft.density} onChange={(v) => setDraft({ ...draft, density: v })} />
            <TextareaField
              label="Alternate Units (JSON)"
              value={draft.alternateUnits ?? ""}
              onChange={(v) => setDraft({ ...draft, alternateUnits: v || null })}
              colSpan={2}
            />
          </Grid>
        )}
      </div>
    </>
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Manpower tabs
// ────────────────────────────────────────────────────────────────────────────

function ManpowerTabs({
  resource,
  tab,
  onTabChange,
  onUpdated,
}: {
  resource: ResourceResponse;
  tab: ManpowerTab;
  onTabChange: (t: ManpowerTab) => void;
  onUpdated: () => void;
}) {
  const tabs: { key: ManpowerTab; label: string }[] = [
    { key: "OVERVIEW", label: "Overview" },
    { key: "PERSONAL", label: "Personal" },
    { key: "EMPLOYMENT", label: "Employment" },
    { key: "SKILLS", label: "Skills" },
  ];

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<ManpowerDto>({});

  const mp = resource.manpower ?? {};
  const master = mp.master ?? {};
  const skills = mp.skills ?? {};
  // financials / attendance / allocation / compliance vars dropped — those tabs are hidden.

  const mutation = useMutation({
    mutationFn: (dto: ManpowerDto) => resourceApi.updateManpower(resource.id, dto),
    onSuccess: () => {
      toast.success("Manpower details updated");
      setEditing(false);
      onUpdated();
    },
    onError: (err: unknown) =>
      toast.error(getErrorMessage(err, "Failed to update manpower details")),
  });

  const handleSave = () => {
    // JSONB validation
    const jsonFields: { value: string | null | undefined; label: string }[] = [
      { value: draft.skills?.secondarySkills, label: "Secondary Skills (JSON)" },
      { value: draft.financials?.allowances, label: "Allowances (JSON)" },
      { value: draft.financials?.deductions, label: "Deductions (JSON)" },
      { value: draft.attendance?.leaveSchedule, label: "Leave Schedule (JSON)" },
    ];
    for (const f of jsonFields) {
      if (f.value && f.value.trim() !== "") {
        try {
          JSON.parse(f.value);
        } catch {
          toast.error(`${f.label} must be valid JSON`);
          return;
        }
      }
    }
    mutation.mutate(draft);
  };

  return (
    <>
      <Tabs items={tabs} value={tab} onChange={onTabChange} />
      <div className="mt-4 rounded-xl border border-border bg-surface/50 p-6">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-text-primary">
            {tabs.find((t) => t.key === tab)?.label}
          </h2>
          {!editing ? (
            <button
              type="button"
              onClick={() => {
                setDraft(mp);
                setEditing(true);
              }}
              className="inline-flex items-center gap-1 rounded-md border border-border bg-surface-hover px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-active"
            >
              <Pencil size={12} /> Edit Section
            </button>
          ) : (
            <div className="flex gap-2">
              <button
                type="button"
                disabled={mutation.isPending}
                onClick={handleSave}
                className="rounded-md bg-accent px-3 py-1.5 text-xs font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                {mutation.isPending ? "Saving…" : "Save"}
              </button>
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="rounded-md border border-border bg-surface-hover px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-active"
              >
                Cancel
              </button>
            </div>
          )}
        </div>

        {/* Read-only views */}
        {!editing && tab === "OVERVIEW" && (
          <ReadOnlyGrid
            entries={[
              ["Employee Code", master.employeeCode],
              ["Full Name", master.fullName],
              ["Designation", master.designation],
              ["Department", master.department],
              ["Employment Type", master.employmentType],
              ["Category", master.category],
              ["Joining Date", master.joiningDate],
              ["Skill Level", skills.skillLevel],
            ]}
          />
        )}
        {!editing && tab === "PERSONAL" && (
          <ReadOnlyGrid
            entries={[
              ["First Name", master.firstName],
              ["Last Name", master.lastName],
              ["Date of Birth", master.dateOfBirth],
              ["Gender", master.gender],
              ["Nationality", master.nationality],
              ["Contact Number", master.contactNumber],
              ["Email", master.email],
              ["Address", master.address],
              ["Emergency Contact", master.emergencyContact],
              ["Photo URL", master.photoUrl],
            ]}
          />
        )}
        {!editing && tab === "EMPLOYMENT" && (
          <ReadOnlyGrid
            entries={[
              ["Employee Code", master.employeeCode],
              ["Employment Type", master.employmentType],
              ["Designation", master.designation],
              ["Department", master.department],
              ["Joining Date", master.joiningDate],
              ["Exit Date", master.exitDate],
              ["Reporting Manager", master.reportingManagerId],
              ["Company Name", master.companyName],
              ["Work Location", master.workLocation],
              ["Category", master.category],
              ["Sub-Category", master.subCategory],
            ]}
          />
        )}
        {!editing && tab === "SKILLS" && (
          <ReadOnlyGrid
            entries={[
              ["Primary Skills", parseSkillArray(skills.primarySkill).join(", ") || null],
              ["Skill Level", skills.skillLevel],
              ["Experience (years)", skills.experienceYears],
              ["License Details", skills.licenseDetails],
              ["Secondary Skills", parseSkillArray(skills.secondarySkills).join(", ") || null],
              ["Certifications", skills.certifications],
              ["Training Records", skills.trainingRecords],
            ]}
          />
        )}
        {/* Edit views */}
        {editing && tab === "OVERVIEW" && (
          <p className="text-sm text-text-muted">
            Choose another tab to edit specific fields. The Overview tab is read-only.
          </p>
        )}
        {editing && tab === "PERSONAL" && (
          <ManpowerPersonalEditor draft={draft} setDraft={setDraft} />
        )}
        {editing && tab === "EMPLOYMENT" && (
          <ManpowerEmploymentEditor draft={draft} setDraft={setDraft} />
        )}
        {editing && tab === "SKILLS" && (
          <ManpowerSkillsEditor draft={draft} setDraft={setDraft} />
        )}
      </div>
    </>
  );
}

/** Build SearchableSelect options from master rows; inject the current value as a synthetic
 * "(legacy)" option when it doesn't match any master row, so existing data stays visible. */
function masterOptions(
  rows: Array<{ id?: string; name: string }>,
  currentValue: string | null | undefined,
): { value: string; label: string }[] {
  const opts = rows.map((r) => ({ value: r.name, label: r.name }));
  if (currentValue && !opts.some((o) => o.value === currentValue)) {
    opts.unshift({ value: currentValue, label: `${currentValue} (legacy)` });
  }
  return opts;
}

/** Tolerant parse — JSON array, single legacy string, or empty. */
function parseSkillArray(raw: string | null | undefined): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map(String) : [String(parsed)];
  } catch {
    return [raw];
  }
}

function ManpowerPersonalEditor({
  draft,
  setDraft,
}: {
  draft: ManpowerDto;
  setDraft: (d: ManpowerDto) => void;
}) {
  const m = draft.master ?? {};
  const setMaster = (patch: Partial<typeof m>) =>
    setDraft({ ...draft, master: { ...m, ...patch } });

  const { data: nationalitiesData } = useQuery({
    queryKey: ["nationalities"],
    queryFn: () => nationalityMasterApi.list(),
  });
  const nationalities = useMemo(() => nationalitiesData?.data ?? [], [nationalitiesData]);

  return (
    <Grid>
      <TextField label="First Name" value={m.firstName ?? ""} onChange={(v) => setMaster({ firstName: v || null })} />
      <TextField label="Last Name" value={m.lastName ?? ""} onChange={(v) => setMaster({ lastName: v || null })} />
      <DateField label="Date of Birth" value={m.dateOfBirth ?? ""} onChange={(v) => setMaster({ dateOfBirth: v || null })} />
      <SelectField
        label="Gender"
        value={m.gender ?? ""}
        onChange={(v) => setMaster({ gender: v || null })}
        options={[
          { value: "", label: "—" },
          { value: "Male", label: "Male" },
          { value: "Female", label: "Female" },
        ]}
      />
      <FieldWrap label="Nationality">
        <input
          className={inputCls}
          list="nationalities-datalist-edit"
          value={m.nationality ?? ""}
          onChange={(e) => setMaster({ nationality: e.target.value || null })}
          placeholder="Type to search or pick a suggestion…"
        />
        <datalist id="nationalities-datalist-edit">
          {nationalities.map((n) => (
            <option key={n.id} value={n.name} />
          ))}
        </datalist>
      </FieldWrap>
      <TextField label="Contact Number" value={m.contactNumber ?? ""} onChange={(v) => setMaster({ contactNumber: v || null })} />
      <TextField label="Email" value={m.email ?? ""} onChange={(v) => setMaster({ email: v || null })} />
      <TextField label="Address" value={m.address ?? ""} onChange={(v) => setMaster({ address: v || null })} colSpan={2} />
      <TextField label="Emergency Contact" value={m.emergencyContact ?? ""} onChange={(v) => setMaster({ emergencyContact: v || null })} />
      <TextField label="Photo URL" value={m.photoUrl ?? ""} onChange={(v) => setMaster({ photoUrl: v || null })} />
    </Grid>
  );
}

function ManpowerEmploymentEditor({
  draft,
  setDraft,
}: {
  draft: ManpowerDto;
  setDraft: (d: ManpowerDto) => void;
}) {
  const m = draft.master ?? {};
  const setMaster = (patch: Partial<typeof m>) =>
    setDraft({ ...draft, master: { ...m, ...patch } });

  const { data: topCategoriesData } = useQuery({
    queryKey: ["manpower-categories", "top-level"],
    queryFn: () => manpowerCategoryMasterApi.listTopLevel(),
  });
  const topCategories = useMemo(() => topCategoriesData?.data ?? [], [topCategoriesData]);

  const selectedCategoryId = useMemo(
    () => topCategories.find((c) => c.name === m.category)?.id ?? null,
    [topCategories, m.category],
  );

  const { data: subCategoriesData } = useQuery({
    queryKey: ["manpower-categories", "by-parent", selectedCategoryId],
    queryFn: () => manpowerCategoryMasterApi.listByParent(selectedCategoryId!),
    enabled: !!selectedCategoryId,
  });
  const subCategories = useMemo(() => subCategoriesData?.data ?? [], [subCategoriesData]);

  const { data: employmentTypesData } = useQuery({
    queryKey: ["employment-types"],
    queryFn: () => employmentTypeMasterApi.list(),
  });
  const employmentTypes = useMemo(
    () => employmentTypesData?.data ?? [],
    [employmentTypesData],
  );

  const categoryOptions = masterOptions(topCategories, m.category);
  const subCategoryOptions = masterOptions(subCategories, m.subCategory);
  const employmentTypeOptions = masterOptions(employmentTypes, m.employmentType);

  return (
    <Grid>
      <TextField label="Employee Code" value={m.employeeCode ?? ""} onChange={(v) => setMaster({ employeeCode: v || null })} />
      <TextField label="Full Name" value={m.fullName ?? ""} onChange={(v) => setMaster({ fullName: v || null })} />
      <FieldWrap label="Employment Type">
        <SearchableSelect
          options={employmentTypeOptions}
          value={m.employmentType ?? ""}
          onChange={(v) => setMaster({ employmentType: v || null })}
          placeholder="— select employment type —"
        />
      </FieldWrap>
      <FieldWrap label="Category">
        <SearchableSelect
          options={categoryOptions}
          value={m.category ?? ""}
          onChange={(v) => {
            const newSet: Partial<typeof m> = { category: v || null };
            if (v !== m.category) newSet.subCategory = null;
            setMaster(newSet);
          }}
          placeholder="— select category —"
        />
      </FieldWrap>
      <FieldWrap label="Sub-Category">
        <SearchableSelect
          options={subCategoryOptions}
          value={m.subCategory ?? ""}
          onChange={(v) => setMaster({ subCategory: v || null })}
          placeholder={
            !selectedCategoryId
              ? "Pick Category first"
              : subCategoryOptions.length === 0
                ? "No sub-categories defined"
                : "— select sub-category —"
          }
          disabled={!selectedCategoryId}
        />
      </FieldWrap>
      <TextField label="Designation" value={m.designation ?? ""} onChange={(v) => setMaster({ designation: v || null })} />
      <TextField label="Department" value={m.department ?? ""} onChange={(v) => setMaster({ department: v || null })} />
      <DateField label="Joining Date" value={m.joiningDate ?? ""} onChange={(v) => setMaster({ joiningDate: v || null })} />
      <DateField label="Exit Date" value={m.exitDate ?? ""} onChange={(v) => setMaster({ exitDate: v || null })} />
      <TextField label="Reporting Manager (UUID)" value={m.reportingManagerId ?? ""} onChange={(v) => setMaster({ reportingManagerId: v || null })} />
      <TextField label="Company Name" value={m.companyName ?? ""} onChange={(v) => setMaster({ companyName: v || null })} />
      <TextField label="Work Location" value={m.workLocation ?? ""} onChange={(v) => setMaster({ workLocation: v || null })} />
    </Grid>
  );
}

function ManpowerSkillsEditor({
  draft,
  setDraft,
}: {
  draft: ManpowerDto;
  setDraft: (d: ManpowerDto) => void;
}) {
  const s = draft.skills ?? {};
  const setSkills = (patch: Partial<typeof s>) =>
    setDraft({ ...draft, skills: { ...s, ...patch } });

  const { data: skillsData } = useQuery({
    queryKey: ["skills"],
    queryFn: () => skillMasterApi.list(),
  });
  const skills = useMemo(() => skillsData?.data ?? [], [skillsData]);

  const { data: skillLevelsData } = useQuery({
    queryKey: ["skill-levels"],
    queryFn: () => skillLevelMasterApi.list(),
  });
  const skillLevels = useMemo(() => skillLevelsData?.data ?? [], [skillLevelsData]);

  const skillOptions = skills.map((sk) => ({ value: sk.name, label: sk.name }));
  const skillLevelOptions = masterOptions(skillLevels, s.skillLevel);

  const primarySelected = parseSkillArray(s.primarySkill);
  const secondarySelected = parseSkillArray(s.secondarySkills);

  return (
    <Grid>
      <FieldWrap label="Primary Skills" colSpan={2}>
        <MultiSelect
          options={skillOptions}
          value={primarySelected}
          onChange={(arr) =>
            setSkills({ primarySkill: arr.length === 0 ? null : JSON.stringify(arr) })
          }
          placeholder="Pick one or more skills…"
        />
      </FieldWrap>
      <FieldWrap label="Skill Level">
        <SearchableSelect
          options={skillLevelOptions}
          value={s.skillLevel ?? ""}
          onChange={(v) => setSkills({ skillLevel: v || null })}
          placeholder="— select skill level —"
        />
      </FieldWrap>
      <NumberField
        label="Experience (years)"
        value={s.experienceYears}
        onChange={(v) => setSkills({ experienceYears: v == null ? null : Math.trunc(v) })}
      />
      <TextField label="License Details" value={s.licenseDetails ?? ""} onChange={(v) => setSkills({ licenseDetails: v || null })} />
      <FieldWrap label="Secondary Skills" colSpan={2}>
        <MultiSelect
          options={skillOptions}
          value={secondarySelected}
          onChange={(arr) =>
            setSkills({ secondarySkills: arr.length === 0 ? null : JSON.stringify(arr) })
          }
          placeholder="Pick additional skills (if any)…"
        />
      </FieldWrap>
      <TextareaField label="Certifications" value={s.certifications ?? ""} onChange={(v) => setSkills({ certifications: v || null })} colSpan={2} />
      <TextareaField label="Training Records" value={s.trainingRecords ?? ""} onChange={(v) => setSkills({ trainingRecords: v || null })} colSpan={2} />
    </Grid>
  );
}


// ────────────────────────────────────────────────────────────────────────────
// Layout helpers
// ────────────────────────────────────────────────────────────────────────────

function Tabs<T extends string>({
  items,
  value,
  onChange,
}: {
  items: { key: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2 border-b border-border">
      {items.map((item) => (
        <button
          key={item.key}
          type="button"
          onClick={() => onChange(item.key)}
          className={`rounded-t-md px-3 py-2 text-sm font-medium transition-colors ${
            value === item.key
              ? "bg-accent text-text-primary"
              : "text-text-secondary hover:bg-surface-hover/50"
          }`}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}

function ReadOnlyGrid({
  entries,
}: {
  entries: [string, unknown][];
}) {
  const fmt = (v: unknown) => {
    if (v == null || v === "") return "—";
    if (typeof v === "number") return String(v);
    return String(v);
  };
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-3">
      {entries.map(([label, value]) => (
        <div key={label} className="flex flex-col">
          <span className="text-xs text-text-muted">{label}</span>
          <span className="text-sm text-text-primary break-words">{fmt(value)}</span>
        </div>
      ))}
    </div>
  );
}

function Grid({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-1 md:grid-cols-2 gap-4">{children}</div>;
}

const inputCls =
  "mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent";

const labelCls = "block text-sm font-medium text-text-secondary";

function FieldWrap({
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

function TextField({
  label,
  value,
  onChange,
  colSpan,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  colSpan?: 1 | 2;
}) {
  return (
    <FieldWrap label={label} colSpan={colSpan}>
      <input
        className={inputCls}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </FieldWrap>
  );
}

function TextareaField({
  label,
  value,
  onChange,
  colSpan,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  colSpan?: 1 | 2;
}) {
  return (
    <FieldWrap label={label} colSpan={colSpan}>
      <textarea
        className={inputCls}
        rows={3}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </FieldWrap>
  );
}

function NumberField({
  label,
  value,
  onChange,
  colSpan,
}: {
  label: string;
  value: number | null | undefined;
  onChange: (v: number | null) => void;
  colSpan?: 1 | 2;
}) {
  return (
    <FieldWrap label={label} colSpan={colSpan}>
      <input
        type="number"
        step="any"
        className={inputCls}
        value={value == null ? "" : String(value)}
        onChange={(e) => {
          const trimmed = e.target.value.trim();
          if (trimmed === "") {
            onChange(null);
            return;
          }
          const n = Number(trimmed);
          onChange(Number.isFinite(n) ? n : null);
        }}
      />
    </FieldWrap>
  );
}

function DateField({
  label,
  value,
  onChange,
  colSpan,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  colSpan?: 1 | 2;
}) {
  return (
    <FieldWrap label={label} colSpan={colSpan}>
      <input
        type="date"
        className={inputCls}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </FieldWrap>
  );
}

function SelectField({
  label,
  value,
  onChange,
  options,
  colSpan,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
  colSpan?: 1 | 2;
}) {
  return (
    <FieldWrap label={label} colSpan={colSpan}>
      <select className={inputCls} value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </FieldWrap>
  );
}
