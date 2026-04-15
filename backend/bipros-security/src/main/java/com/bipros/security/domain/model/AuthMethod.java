package com.bipros.security.domain.model;

/**
 * IC-PMS authentication methods supported per user (MasterData_OrgUsers).
 */
public enum AuthMethod {
    AADHAAR_OTP,
    NIC_SSO,
    DSC_CLASS_3,
    USERNAME_PASSWORD
}
