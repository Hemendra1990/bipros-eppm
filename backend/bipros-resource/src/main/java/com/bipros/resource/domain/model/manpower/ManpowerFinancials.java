package com.bipros.resource.domain.model.manpower;

import com.bipros.resource.domain.model.enums.PaymentMode;
import com.bipros.resource.domain.model.enums.SalaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "manpower_financials", schema = "resource")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManpowerFinancials {

  @Id
  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "salary_type", length = 20)
  private SalaryType salaryType;

  @Column(name = "base_salary", precision = 15, scale = 2)
  private BigDecimal baseSalary;

  @Column(name = "hourly_rate", precision = 15, scale = 4)
  private BigDecimal hourlyRate;

  @Column(name = "overtime_rate", precision = 15, scale = 4)
  private BigDecimal overtimeRate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String allowances;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String deductions;

  @Column(length = 3, columnDefinition = "char(3)")
  private String currency;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "bank_account_details", columnDefinition = "jsonb")
  private String bankAccountDetails;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_mode", length = 30)
  private PaymentMode paymentMode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tax_details", columnDefinition = "jsonb")
  private String taxDetails;

  @Column(name = "pf_number", length = 40)
  private String pfNumber;

  @Column(name = "esi_number", length = 40)
  private String esiNumber;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @LastModifiedBy
  @Column(name = "updated_by")
  private String updatedBy;

  @Version
  @Column(name = "version")
  private Long version;
}
