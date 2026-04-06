"use client";

import { Hash, DollarSign, Calendar, Type, Tag, ToggleLeft } from "lucide-react";

interface UdfValue {
  fieldId: string;
  fieldName: string;
  dataType: "TEXT" | "NUMBER" | "COST" | "DATE" | "INDICATOR" | "CODE";
  value: string | null;
}

interface UdfValueRendererProps {
  fields: UdfValue[];
  onValueChange?: (fieldId: string, value: string) => void;
  readOnly?: boolean;
}

const TYPE_ICONS: Record<string, typeof Type> = {
  TEXT: Type,
  NUMBER: Hash,
  COST: DollarSign,
  DATE: Calendar,
  INDICATOR: ToggleLeft,
  CODE: Tag,
};

export function UdfValueRenderer({
  fields,
  onValueChange,
  readOnly = false,
}: UdfValueRendererProps) {
  if (fields.length === 0) {
    return (
      <p className="py-4 text-center text-sm text-slate-500">
        No custom fields defined for this item.
      </p>
    );
  }

  const inputClass =
    "block w-full rounded-md border border-slate-700 bg-slate-800/50 px-3 py-1.5 text-sm text-white placeholder-gray-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500";

  const renderInput = (field: UdfValue) => {
    if (readOnly) {
      return (
        <span className="text-sm text-slate-300">
          {formatValue(field) || "—"}
        </span>
      );
    }

    switch (field.dataType) {
      case "DATE":
        return (
          <input
            type="date"
            value={field.value ?? ""}
            onChange={(e) => onValueChange?.(field.fieldId, e.target.value)}
            className={inputClass}
          />
        );
      case "NUMBER":
      case "COST":
        return (
          <input
            type="number"
            step={field.dataType === "COST" ? "0.01" : "1"}
            value={field.value ?? ""}
            onChange={(e) => onValueChange?.(field.fieldId, e.target.value)}
            placeholder={field.dataType === "COST" ? "0.00" : "0"}
            className={inputClass}
          />
        );
      case "INDICATOR":
        return (
          <select
            value={field.value ?? ""}
            onChange={(e) => onValueChange?.(field.fieldId, e.target.value)}
            className={inputClass}
          >
            <option value="">— Select —</option>
            <option value="GREEN">Green</option>
            <option value="YELLOW">Yellow</option>
            <option value="RED">Red</option>
            <option value="BLUE">Blue</option>
            <option value="NONE">None</option>
          </select>
        );
      default:
        return (
          <input
            type="text"
            value={field.value ?? ""}
            onChange={(e) => onValueChange?.(field.fieldId, e.target.value)}
            placeholder="Enter value"
            className={inputClass}
          />
        );
    }
  };

  return (
    <div className="space-y-3">
      {fields.map((field) => {
        const Icon = TYPE_ICONS[field.dataType] ?? Type;
        return (
          <div key={field.fieldId} className="flex items-center gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded bg-slate-800">
              <Icon size={14} className="text-blue-400" />
            </div>
            <div className="w-36 shrink-0">
              <span className="text-sm font-medium text-slate-300">
                {field.fieldName}
              </span>
            </div>
            <div className="flex-1">{renderInput(field)}</div>
          </div>
        );
      })}
    </div>
  );
}

function formatValue(field: UdfValue): string {
  if (!field.value) return "";
  switch (field.dataType) {
    case "COST":
      return `$${parseFloat(field.value).toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })}`;
    case "DATE":
      return new Date(field.value).toLocaleDateString();
    case "INDICATOR": {
      const colors: Record<string, string> = {
        GREEN: "Green",
        YELLOW: "Yellow",
        RED: "Red",
        BLUE: "Blue",
        NONE: "None",
      };
      return colors[field.value] ?? field.value;
    }
    default:
      return field.value;
  }
}
