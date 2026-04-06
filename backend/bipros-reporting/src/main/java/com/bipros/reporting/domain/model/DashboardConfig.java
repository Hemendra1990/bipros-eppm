package com.bipros.reporting.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dashboard_configs", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardConfig extends BaseEntity {

    @Column(name = "tier", nullable = false)
    @Enumerated(EnumType.STRING)
    private DashboardTier tier;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "layout_config", columnDefinition = "TEXT")
    @Lob
    private String layoutConfig;

    @Column(name = "is_default")
    private Boolean isDefault;

    public enum DashboardTier {
        EXECUTIVE, PROGRAMME, PROJECT_MANAGER, OPERATIONAL, FIELD
    }
}
