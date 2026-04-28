package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ppe_item_template", schema = "permit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PpeItemTemplate extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "icon_key", length = 60)
    private String iconKey;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
