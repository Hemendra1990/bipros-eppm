package com.bipros.resource.domain.model;

/**
 * Coarse "Unit Rate Master" bucket used by the Daily Cost Report Section A view. Manpower
 * intentionally lives on {@link Role} (ResourceRole) — this enum applies to {@link Resource} only.
 */
public enum CostCategory {
  EQUIPMENT,
  MATERIAL,
  SUB_CONTRACT
}
