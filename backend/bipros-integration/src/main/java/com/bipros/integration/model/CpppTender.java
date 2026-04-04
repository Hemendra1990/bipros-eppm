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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cppp_tenders", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CpppTender extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "tender_id")
    private UUID tenderId;

    @Column(name = "cppp_tender_number", unique = true, nullable = false, length = 100)
    private String cpppTenderNumber;

    @Column(name = "nit_reference_number", nullable = false, length = 100)
    private String nitReferenceNumber;

    @Column(name = "published_date", nullable = false)
    private LocalDate publishedDate;

    @Column(name = "bid_submission_deadline", nullable = false)
    private LocalDate bidSubmissionDeadline;

    @Column(name = "cppp_url", length = 500)
    private String cpppUrl;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private CpppTenderStatus status = CpppTenderStatus.PUBLISHED;

    public enum CpppTenderStatus {
        PUBLISHED,
        LIVE,
        CLOSED,
        WITHDRAWN
    }
}
