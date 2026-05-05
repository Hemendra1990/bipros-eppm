package com.bipros.common.event;

/**
 * What kind of DPR mutation triggered the {@link DprSubmittedEvent}. Listeners use this to
 * decide whether to add, subtract, or rebalance derived state (BOQ executed quantity, ETL
 * fact-table rows). For UPDATED and DELETED, the event carries the prior {@code oldQty} /
 * {@code oldBoqItemNo} so listeners can apply a delta without re-reading the database.
 */
public enum DprMutationType {
  CREATED,
  UPDATED,
  DELETED
}
