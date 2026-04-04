package com.bipros.gis.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "gis_layers", schema = "gis")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GisLayer extends BaseEntity {

    @NotNull(message = "Project ID is required")
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @NotBlank(message = "Layer name is required")
    @Column(name = "layer_name", nullable = false, length = 100)
    private String layerName;

    @NotNull(message = "Layer type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "layer_type", nullable = false, length = 50)
    private GisLayerType layerType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible = true;

    @Column(name = "opacity", nullable = false)
    private Double opacity = 1.0;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
