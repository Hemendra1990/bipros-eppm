package com.bipros.activity.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "activity_code_assignments", schema = "activity", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"activity_id", "activity_code_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActivityCodeAssignment extends BaseEntity {

  @Column(name = "activity_id", nullable = false)
  private UUID activityId;

  @Column(name = "activity_code_id", nullable = false)
  private UUID activityCodeId;

  @Column(name = "code_value", nullable = false, length = 100)
  private String codeValue;
}
