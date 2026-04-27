package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import com.bipros.common.model.HierarchyNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "eps_nodes", schema = "project", uniqueConstraints = {
    @UniqueConstraint(columnNames = "code")
}, indexes = {
    @Index(name = "idx_eps_nodes_parent_id", columnList = "parent_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EpsNode extends BaseEntity implements HierarchyNode {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "obs_id")
    private UUID obsId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Override
    public int getSortOrder() {
        return sortOrder != null ? sortOrder : 0;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getParentId() {
        return parentId;
    }
}
