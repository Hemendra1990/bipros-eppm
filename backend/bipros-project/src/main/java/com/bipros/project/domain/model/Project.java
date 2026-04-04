package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects", schema = "project", uniqueConstraints = {
    @UniqueConstraint(columnNames = "code")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Project extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "eps_node_id", nullable = false)
    private UUID epsNodeId;

    @Column(name = "obs_node_id")
    private UUID obsNodeId;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @Column(name = "planned_finish_date")
    private LocalDate plannedFinishDate;

    @Column(name = "data_date")
    private LocalDate dataDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.PLANNED;

    @Column(name = "must_finish_by_date")
    private LocalDate mustFinishByDate;

    @Column(nullable = false)
    private Integer priority = 50;
}
