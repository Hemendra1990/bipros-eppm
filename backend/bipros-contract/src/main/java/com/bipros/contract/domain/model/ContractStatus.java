package com.bipros.contract.domain.model;

/**
 * IC-PMS contract lifecycle — extended with risk/progress bands per Excel M5.
 */
public enum ContractStatus {
    DRAFT,
    MOBILISATION,
    ACTIVE,
    ACTIVE_AT_RISK,
    ACTIVE_DELAYED,
    DELAYED,
    SUSPENDED,
    COMPLETED,
    TERMINATED,
    DLP
}
