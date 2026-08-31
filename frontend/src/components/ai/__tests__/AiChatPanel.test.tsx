import { describe, it, expect } from "vitest";
import { friendlyToolLabel, TOOL_PROGRESS_LABELS } from "../AiChatPanel";

describe("friendlyToolLabel", () => {
  it("returns the fallback 'Working' for unknown tool names", () => {
    expect(friendlyToolLabel("not_a_real_tool")).toBe("Working");
    expect(friendlyToolLabel("")).toBe("Working");
  });

  it.each(Object.entries(TOOL_PROGRESS_LABELS))(
    "maps %s -> %s",
    (toolName, expectedLabel) => {
      expect(friendlyToolLabel(toolName)).toBe(expectedLabel);
    },
  );

  describe("Site Manager tools", () => {
    it.each([
      ["analyze_labour_utilization", "Reading crew utilization"],
      ["analyze_machine_idle_time", "Checking machine idle time"],
      ["analyze_material_wastage", "Reading material wastage"],
      ["check_stockpile_vs_plan", "Comparing stockpile vs plan"],
    ])("%s -> %s", (tool, label) => {
      expect(friendlyToolLabel(tool)).toBe(label);
    });
  });

  describe("Project Engineer tools", () => {
    it.each([
      ["analyze_productivity_factor", "Reading productivity vs norm"],
      ["analyze_yield_variance", "Reading yield variance"],
      ["analyze_equipment_cycle_time", "Reading equipment cycle times"],
    ])("%s -> %s", (tool, label) => {
      expect(friendlyToolLabel(tool)).toBe(label);
    });
  });

  describe("QC Manager tools", () => {
    it.each([
      ["analyze_ncr_trends", "Reading NCR trends"],
      ["audit_traceability", "Auditing traceability"],
      ["analyze_quality_data_gaps", "Looking for quality data gaps"],
    ])("%s -> %s", (tool, label) => {
      expect(friendlyToolLabel(tool)).toBe(label);
    });
  });

  describe("Project Manager tools", () => {
    it.each([
      ["analyze_labour_cost_per_unit", "Reading labour cost per unit"],
      ["analyze_material_burn_rate", "Reading material burn rate"],
      ["analyze_equipment_utilization_cost", "Reading equipment utilization cost"],
    ])("%s -> %s", (tool, label) => {
      expect(friendlyToolLabel(tool)).toBe(label);
    });
  });

  describe("BIM / Data Coordinator tools", () => {
    it.each([
      ["audit_dpr_data_quality", "Auditing DPR data quality"],
      ["report_data_lag", "Reading data entry lag"],
    ])("%s -> %s", (tool, label) => {
      expect(friendlyToolLabel(tool)).toBe(label);
    });
  });

  describe("Pre-existing tagged tools", () => {
    it.each([
      ["portfolio_kpi", "Reading portfolio KPIs"],
      ["analyze_cost", "Reading cost performance"],
      ["analyze_risk", "Reading risk register"],
      ["analyze_schedule", "Reading schedule health"],
      ["forecast_completion", "Running forecast"],
    ])("%s -> %s", (tool, label) => {
      expect(friendlyToolLabel(tool)).toBe(label);
    });
  });

  it("never leaks a raw tool name (always returns a sentence-cased English label)", () => {
    for (const [, label] of Object.entries(TOOL_PROGRESS_LABELS)) {
      expect(label).not.toMatch(/_/);
      expect(label[0]).toBe(label[0].toUpperCase());
    }
  });
});
