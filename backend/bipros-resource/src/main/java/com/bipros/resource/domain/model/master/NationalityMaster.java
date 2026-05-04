package com.bipros.resource.domain.model.master;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Master for Nationalities — populates the Manpower form's nationality datalist. The form input
 * uses native HTML autocomplete-with-free-text: users can pick a suggestion or type any value
 * not yet in the master.
 */
@Entity
@Table(
    name = "nationality_master",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_nationality_master_code", columnNames = {"code"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NationalityMaster extends BaseEntity {

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(name = "sort_order", nullable = false)
  @Default
  private Integer sortOrder = 0;

  @Column(nullable = false)
  @Default
  private Boolean active = true;
}
