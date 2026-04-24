package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Individual lab-test record on a {@link MaterialSource}. The rollup
 * {@link com.bipros.resource.domain.model.LabTestStatus} on {@code MaterialSource} is computed
 * from the set of rows here.
 */
@Entity
@Table(
    name = "material_source_lab_test",
    schema = "resource",
    indexes = @Index(name = "idx_material_source_lab_test_source", columnList = "source_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialSourceLabTest extends BaseEntity {

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    /** Test name (e.g. "CBR", "Sieve Analysis", "Aggregate Impact Value"). */
    @Column(name = "test_name", nullable = false, length = 120)
    private String testName;

    /** IS / IRC / MoRTH clause reference for the test method. */
    @Column(name = "standard_reference", length = 120)
    private String standardReference;

    @Column(name = "result_value", precision = 12, scale = 4)
    private BigDecimal resultValue;

    @Column(name = "result_unit", length = 20)
    private String resultUnit;

    /** Pass/fail flag. {@code null} = pending. */
    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "test_date")
    private LocalDate testDate;

    @Column(name = "remarks", length = 500)
    private String remarks;
}
