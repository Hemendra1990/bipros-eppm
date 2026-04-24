package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Join between a {@link Material} and a BOQ item ("Applicable BOQ Items" multi-select on
 * Screen 09a). Drives the consumption-vs-BOQ-norms report.
 */
@Entity
@Table(
    name = "material_boq_link",
    schema = "resource",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_material_boq",
        columnNames = {"material_id", "boq_item_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaterialBoqLink extends BaseEntity {

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "boq_item_id", nullable = false)
    private UUID boqItemId;
}
