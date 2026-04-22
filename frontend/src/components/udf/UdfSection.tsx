"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Settings2 } from "lucide-react";
import toast from "react-hot-toast";
import { udfApi, type UdfSubject, type UdfDataType, type UdfValueResponse, type UserDefinedFieldDto, type SetUdfValueRequest, type IndicatorColor } from "@/lib/api/udfApi";
import { UdfValueRenderer } from "./UdfValueRenderer";

interface UdfSectionProps {
  entityId: string;
  subject: UdfSubject;
  projectId?: string;
  readOnly?: boolean;
  title?: string;
}

function extractValue(dataType: UdfDataType, val: UdfValueResponse): string | null {
  switch (dataType) {
    case "TEXT":
      return val.textValue;
    case "NUMBER":
      return val.numberValue != null ? String(val.numberValue) : null;
    case "COST":
      return val.costValue != null ? String(val.costValue) : null;
    case "DATE":
      return val.dateValue;
    case "INDICATOR":
      return val.indicatorValue;
    case "CODE":
      return val.codeValue;
    default:
      return val.textValue;
  }
}

const VALID_INDICATORS: ReadonlySet<string> = new Set<IndicatorColor>(["NONE", "RED", "YELLOW", "GREEN", "BLUE"]);

function buildSetRequest(dataType: UdfDataType, value: string): SetUdfValueRequest {
  switch (dataType) {
    case "TEXT":
      return { textValue: value };
    case "NUMBER": {
      const num = parseFloat(value);
      return { numberValue: value && Number.isFinite(num) ? num : undefined };
    }
    case "COST": {
      const cost = parseFloat(value);
      return { costValue: value && Number.isFinite(cost) ? cost : undefined };
    }
    case "DATE":
      return { dateValue: value || undefined };
    case "INDICATOR":
      return { indicatorValue: VALID_INDICATORS.has(value) ? (value as IndicatorColor) : undefined };
    case "CODE":
      return { codeValue: value };
    default:
      return { textValue: value };
  }
}

export function UdfSection({ entityId, subject, projectId, readOnly = false, title = "Custom Fields" }: UdfSectionProps) {
  const queryClient = useQueryClient();

  const { data: fieldsRes } = useQuery({
    queryKey: ["udf-fields", subject, projectId],
    queryFn: () => udfApi.listFields(subject, undefined, projectId),
  });

  const { data: valuesRes } = useQuery({
    queryKey: ["udf-values", entityId],
    queryFn: () => udfApi.getEntityValues(entityId),
    enabled: !!entityId,
  });

  const setValueMutation = useMutation({
    mutationFn: ({ fieldId, data }: { fieldId: string; data: SetUdfValueRequest }) =>
      udfApi.setValue(fieldId, entityId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["udf-values", entityId] });
    },
    onError: () => {
      toast.error("Failed to save custom field value");
    },
  });

  const fields = fieldsRes?.data ?? [];
  const values = valuesRes?.data ?? [];

  if (fields.length === 0) return null;

  const valueMap = new Map<string, UdfValueResponse>();
  for (const v of values) {
    valueMap.set(v.userDefinedFieldId, v);
  }

  const mappedFields = fields.map((f: UserDefinedFieldDto) => {
    const val = valueMap.get(f.id);
    return {
      fieldId: f.id,
      fieldName: f.name,
      dataType: f.dataType,
      value: val ? extractValue(f.dataType, val) : (f.defaultValue ?? null),
    };
  });

  const handleValueChange = (fieldId: string, value: string) => {
    const field = fields.find((f: UserDefinedFieldDto) => f.id === fieldId);
    if (!field) return;
    setValueMutation.mutate({ fieldId, data: buildSetRequest(field.dataType, value) });
  };

  return (
    <div className="rounded-xl border border-border bg-surface/50 p-6">
      <div className="mb-4 flex items-center gap-2">
        <Settings2 size={16} className="text-text-secondary" />
        <h3 className="text-sm font-semibold text-text-secondary">{title}</h3>
      </div>
      <UdfValueRenderer
        fields={mappedFields}
        onValueChange={readOnly ? undefined : handleValueChange}
        readOnly={readOnly}
      />
    </div>
  );
}
