package com.bipros.project.domain.model;

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
 * Join between a {@link Stretch} and a BOQ item, implementing the "Work Activities" multi-select
 * on the PMS MasterData Stretch Master screen.
 */
@Entity
@Table(
    name = "stretch_activity_link",
    schema = "project",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_stretch_activity",
        columnNames = {"stretch_id", "boq_item_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StretchActivityLink extends BaseEntity {

    @Column(name = "stretch_id", nullable = false)
    private UUID stretchId;

    @Column(name = "boq_item_id", nullable = false)
    private UUID boqItemId;
}
