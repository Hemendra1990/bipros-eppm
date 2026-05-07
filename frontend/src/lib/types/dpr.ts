// Shared DPR domain types — mirror the backend records in
// backend/bipros-project/.../dto. Kept in /lib/types so non-API callers (forms,
// charts, AI insights) can import without dragging the axios client along.

export type Side = "LHS" | "RHS" | "CENTER";
export type Shift = "DAY" | "NIGHT";
export type DprApprovalStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";
export type SafetyIncidentType = "NONE" | "NEAR_MISS" | "INCIDENT";
export type ManpowerCategory = "SKILLED" | "SEMI_SKILLED" | "UNSKILLED";
export type EquipmentOwnership = "OWNED" | "HIRED" | "SUBCONTRACTOR";
export type EquipmentAvailability = "AVAILABLE" | "UTILIZED" | "IDLE" | "BREAKDOWN";

export type Unit = "Cum" | "MT" | "Rm" | "Each" | "Sqm" | "R/mtr" | "Nr";

export type RateBasis = "HOUR" | "DAY" | "EACH";

export interface DprManpowerRow {
  id?: string | null;
  /** Required: ResourceAssignment that backs this row. Picked via the searchable dropdown. */
  resourceAssignmentId: string;
  resourceId?: string | null;
  trade: string;
  category?: ManpowerCategory | null;
  nos?: number | null;
  workingHours?: number | null;
  otHours?: number | null;
  unitRate?: number | null;
  unitRateBasis?: RateBasis | null;
  lineCost?: number | null;
  contractorName?: string | null;
  remarks?: string | null;
}

export interface DprEquipmentRow {
  id?: string | null;
  resourceAssignmentId: string;
  resourceId?: string | null;
  equipmentType: string;
  fleetNo?: string | null;
  ownership?: EquipmentOwnership | null;
  nos?: number | null;
  workingHours?: number | null;
  idleHours?: number | null;
  breakdownHours?: number | null;
  fuelLitres?: number | null;
  unitRate?: number | null;
  lineCost?: number | null;
  operatorName?: string | null;
  availabilityStatus?: EquipmentAvailability | null;
  remarks?: string | null;
}

export interface DprMaterialRow {
  id?: string | null;
  resourceAssignmentId: string;
  materialId?: string | null;
  resourceId?: string | null;
  materialName: string;
  quantity?: number | null;
  unit?: string | null;
  source?: string | null;
  batchNo?: string | null;
  vendorName?: string | null;
  unitRate?: number | null;
  lineCost?: number | null;
  remarks?: string | null;
}

/** Picker-mode option returned by GET .../resource-assignments/activity/{id}/picker?kind=… */
export interface AssignedResourceOption {
  assignmentId: string;
  resourceId: string;
  resourceName: string;
  resourceCode?: string | null;
  unit?: string | null;
  unitRateBasis?: RateBasis | null;
  unitRate?: number | null;
  rateType?: string | null;
  plannedUnits?: number | null;
  actualUnits?: number | null;
  plannedCost?: number | null;
  actualCost?: number | null;
  kind?: "MANPOWER" | "EQUIPMENT" | "MATERIAL" | null;
}

export interface DprBaseFields {
  reportDate: string;
  supervisorResourceId?: string | null;
  supervisorName: string;
  chainageFromM?: number | null;
  chainageToM?: number | null;
  /** Soft FK to activity.activities.id. Required to load assigned resources for the picker. */
  activityId?: string | null;
  activityName: string;
  wbsNodeId?: string | null;
  boqItemNo?: string | null;
  unit: string;
  qtyExecuted: number;
  weatherCondition?: string | null;
  remarks?: string | null;

  side?: Side | null;
  landmark?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  shift?: Shift | null;
  approvalStatus?: DprApprovalStatus | null;
  contractorName?: string | null;
  delayReason?: string | null;
  safetyObservation?: string | null;
  safetyIncidentType?: SafetyIncidentType | null;

  manpower?: DprManpowerRow[];
  equipment?: DprEquipmentRow[];
  materials?: DprMaterialRow[];
}

export interface DailyProgressReportResponse extends DprBaseFields {
  id: string;
  projectId: string;
  cumulativeQty: number | null;
  manpower: DprManpowerRow[];
  equipment: DprEquipmentRow[];
  materials: DprMaterialRow[];
  /** Server-side warnings (e.g. rate-missing:trade-name, assignment-not-found:uuid). */
  warnings?: string[];
}

export type CreateDailyProgressReportRequest = DprBaseFields;
export type UpdateDailyProgressReportRequest = DprBaseFields;
