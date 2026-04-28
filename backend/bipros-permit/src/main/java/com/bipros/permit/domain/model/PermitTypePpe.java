package com.bipros.permit.domain.model;

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

@Entity
@Table(name = "permit_type_ppe", schema = "permit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_type_ppe", columnNames = {"permit_type_template_id", "ppe_item_template_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitTypePpe extends BaseEntity {

    @Column(name = "permit_type_template_id", nullable = false)
    private UUID permitTypeTemplateId;

    @Column(name = "ppe_item_template_id", nullable = false)
    private UUID ppeItemTemplateId;

    @Column(nullable = false)
    private boolean required = true;
}
