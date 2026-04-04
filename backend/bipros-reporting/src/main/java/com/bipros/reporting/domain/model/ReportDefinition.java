package com.bipros.reporting.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "report_definitions", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReportDefinition extends BaseEntity {

  @Column(nullable = false, length = 255)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReportType reportType;

  @Column(name = "is_built_in")
  private Boolean isBuiltIn = false;

  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  @Column(name = "config_json", columnDefinition = "TEXT")
  private String configJson;
}
