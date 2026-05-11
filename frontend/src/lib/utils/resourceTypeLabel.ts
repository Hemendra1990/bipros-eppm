// Display-only alias: master data uses "Labor" but planners prefer "Manpower".
// Filtering and data still use the raw resourceTypeName from master data.
export const displayResourceTypeName = (name: string | null | undefined): string => {
  if (!name) return "Other";
  const lower = name.toLowerCase();
  if (lower === "labor" || lower === "labour") return "Manpower";
  return name;
};
