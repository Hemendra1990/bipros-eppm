package com.bipros.integration.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "gstn_verifications", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GstnVerification extends BaseEntity {

    @Column(name = "contractor_name", nullable = false, length = 255)
    private String contractorName;

    @Column(name = "gstin", nullable = false, length = 15)
    private String gstin;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "legal_name", length = 500)
    private String legalName;

    @Column(name = "trade_name", length = 500)
    private String tradeName;

    @Column(name = "gst_status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private GstStatus gstStatus = GstStatus.UNKNOWN;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "is_compliant", nullable = false)
    private Boolean isCompliant = false;

    public enum GstStatus {
        ACTIVE,
        SUSPENDED,
        CANCELLED,
        UNKNOWN
    }
}
