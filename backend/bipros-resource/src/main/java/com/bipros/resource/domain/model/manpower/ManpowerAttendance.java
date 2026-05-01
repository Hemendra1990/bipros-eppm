package com.bipros.resource.domain.model.manpower;

import com.bipros.resource.domain.model.enums.AttendanceStatus;
import com.bipros.resource.domain.model.enums.ShiftType;
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
@Table(name = "manpower_attendance", schema = "resource")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManpowerAttendance {

  @Id
  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "daily_attendance_status", length = 20)
  private AttendanceStatus dailyAttendanceStatus;

  @Column(name = "last_check_in_time")
  private Instant lastCheckInTime;

  @Column(name = "last_check_out_time")
  private Instant lastCheckOutTime;

  @Column(name = "working_hours_per_day", precision = 5, scale = 2)
  private BigDecimal workingHoursPerDay;

  @Enumerated(EnumType.STRING)
  @Column(name = "shift_type", length = 20)
  private ShiftType shiftType;

  @Column(name = "total_work_hours_mtd", precision = 8, scale = 2)
  private BigDecimal totalWorkHoursMtd;

  @Column(name = "overtime_hours_mtd", precision = 8, scale = 2)
  private BigDecimal overtimeHoursMtd;

  @Column(name = "leave_balance", precision = 6, scale = 2)
  private BigDecimal leaveBalance;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "leave_schedule", columnDefinition = "jsonb")
  private String leaveSchedule;

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
