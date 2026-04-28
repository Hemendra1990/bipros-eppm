package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permit_gas_test", schema = "permit", indexes = {
        @Index(name = "ix_permit_gas_test_permit", columnList = "permit_id, tested_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitGasTest extends BaseEntity {

    @Column(name = "permit_id", nullable = false)
    private UUID permitId;

    @Column(name = "lel_pct", precision = 5, scale = 2)
    private BigDecimal lelPct;

    @Column(name = "o2_pct", precision = 5, scale = 2)
    private BigDecimal o2Pct;

    @Column(name = "h2s_ppm", precision = 7, scale = 2)
    private BigDecimal h2sPpm;

    @Column(name = "co_ppm", precision = 7, scale = 2)
    private BigDecimal coPpm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GasTestResult result = GasTestResult.PASS;

    @Column(name = "tested_by")
    private UUID testedBy;

    @Column(name = "tested_at", nullable = false)
    private Instant testedAt;

    @Column(name = "instrument_serial", length = 80)
    private String instrumentSerial;
}
