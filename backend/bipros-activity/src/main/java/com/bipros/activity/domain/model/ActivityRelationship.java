package com.bipros.activity.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "activity_relationships", schema = "activity", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"predecessor_activity_id", "successor_activity_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActivityRelationship extends BaseEntity {

  @Column(name = "predecessor_activity_id", nullable = false)
  private UUID predecessorActivityId;

  @Column(name = "successor_activity_id", nullable = false)
  private UUID successorActivityId;

  @Enumerated(EnumType.STRING)
  @Column(name = "relationship_type", nullable = false)
  private RelationshipType relationshipType = RelationshipType.FINISH_TO_START;

  @Column(nullable = false)
  private Double lag = 0.0;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "is_external", nullable = false)
  private Boolean isExternal = false;
}
