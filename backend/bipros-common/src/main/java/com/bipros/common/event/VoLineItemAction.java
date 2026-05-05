package com.bipros.common.event;

/**
 * The mutation a VO line item applies to the matching BOQ row when the VO is approved.
 * Shared between {@code bipros-contract} (entity owner) and {@code bipros-project} (the
 * listener that applies the mutation) — kept in {@code bipros-common} so neither module
 * has to depend on the other.
 */
public enum VoLineItemAction {
  /** New BOQ row created from {@code newItemNo / newItemDescription / newItemUnit / revisedQty / revisedRate}. */
  ADD_ITEM,
  /** Existing BOQ row's {@code boqQty} replaced by {@code revisedQty}. */
  REVISE_QTY,
  /** Existing BOQ row's {@code boqRate} replaced by {@code revisedRate}. */
  REVISE_RATE,
  /** Existing BOQ row deleted. */
  DELETE_ITEM
}
