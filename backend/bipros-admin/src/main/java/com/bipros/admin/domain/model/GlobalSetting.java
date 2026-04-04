package com.bipros.admin.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "global_settings", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSetting extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String settingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String settingValue;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;
}
