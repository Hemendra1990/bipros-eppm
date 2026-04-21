package com.bipros.resource.domain.model;

/**
 * IC-PMS M8 utilisation status band. Derived from utilisationPercent and mobilisation
 * state. OVER_90 and CRITICAL_100 trigger dashboard alerts.
 */
public enum UtilisationStatus {
    ACTIVE,
    OVER_90,
    CRITICAL_100,
    ON_HOLD_NOT_MOBILISED,
    PROCUREMENT,
    DELIVERY_ONGOING,
    LAYING
}
