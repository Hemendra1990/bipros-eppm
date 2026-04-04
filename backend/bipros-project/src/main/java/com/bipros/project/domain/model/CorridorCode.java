package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "corridor_codes", schema = "project", uniqueConstraints = {
    @UniqueConstraint(columnNames = "project_id"),
    @UniqueConstraint(columnNames = "generated_code")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CorridorCode extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "corridor_prefix", nullable = false, length = 20)
    private String corridorPrefix; // e.g. "DMIC", "CBIC", "VCIC"

    @Column(name = "zone_code", length = 20)
    private String zoneCode;

    @Column(name = "node_code", length = 20)
    private String nodeCode;

    @Column(name = "generated_code", nullable = false, length = 100)
    private String generatedCode; // e.g. "DMIC-WEST-NODE01-PKG005"
}
