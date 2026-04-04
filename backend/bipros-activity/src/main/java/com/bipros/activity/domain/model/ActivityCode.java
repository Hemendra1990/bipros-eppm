package com.bipros.activity.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "activity_codes", schema = "activity")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActivityCode extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CodeScope scope;

  @Column(name = "eps_node_id")
  private UUID epsNodeId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "sort_order")
  private Integer sortOrder;
}
