package com.bipros.portfolio.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "portfolios", schema = "portfolio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column
  private String description;

  @Column
  private UUID ownerId;

  @Column(nullable = false)
  private Boolean isActive = true;
}
