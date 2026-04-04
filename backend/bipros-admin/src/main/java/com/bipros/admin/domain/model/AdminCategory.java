package com.bipros.admin.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "admin_categories", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminCategory extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String categoryType;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column
    private UUID parentId;

    @Column
    private int sortOrder;
}
