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

/** Junction: which permit types appear in which industry pack. */
@Entity
@Table(name = "permit_pack_type", schema = "permit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pack_type", columnNames = {"pack_id", "permit_type_template_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitPackType extends BaseEntity {

    @Column(name = "pack_id", nullable = false)
    private UUID packId;

    @Column(name = "permit_type_template_id", nullable = false)
    private UUID permitTypeTemplateId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
