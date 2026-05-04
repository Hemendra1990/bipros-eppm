package com.bipros.resource.domain.model.master;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Master for Manpower Category and Sub-Category. Self-referencing — a row with
 * {@code parent_id IS NULL} is a top-level Category (Skilled / Unskilled / Staff /
 * admin-defined); a row with a non-null {@code parent_id} is a Sub-Category of that parent
 * (Mason under Skilled, Helper under Unskilled, etc.).
 *
 * <p>Used by the Manpower resource form: pick a Category, then Sub-Category options filter to
 * children of the picked Category. Stored on {@code ManpowerMaster.category} /
 * {@code ManpowerMaster.subCategory} as the master row's {@code name}, not its UUID — keeps the
 * existing string columns and avoids any FK migration on existing data.
 */
@Entity
@Table(
    name = "manpower_category_master",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_manpower_category_master_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_manpower_category_master_parent", columnList = "parent_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManpowerCategoryMaster extends BaseEntity {

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "sort_order", nullable = false)
  @Default
  private Integer sortOrder = 0;

  @Column(nullable = false)
  @Default
  private Boolean active = true;
}
