package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.enums.AttendanceStatus;
import com.bipros.resource.domain.model.enums.ShiftType;
import com.bipros.resource.domain.model.manpower.ManpowerAttendance;

import java.math.BigDecimal;
import java.time.Instant;

public record ManpowerAttendanceDto(
    AttendanceStatus dailyAttendanceStatus,
    Instant lastCheckInTime,
    Instant lastCheckOutTime,
    BigDecimal workingHoursPerDay,
    ShiftType shiftType,
    BigDecimal totalWorkHoursMtd,
    BigDecimal overtimeHoursMtd,
    BigDecimal leaveBalance,
    String leaveSchedule
) {

  public static ManpowerAttendanceDto from(ManpowerAttendance a) {
    if (a == null) return null;
    return new ManpowerAttendanceDto(
        a.getDailyAttendanceStatus(),
        a.getLastCheckInTime(),
        a.getLastCheckOutTime(),
        a.getWorkingHoursPerDay(),
        a.getShiftType(),
        a.getTotalWorkHoursMtd(),
        a.getOvertimeHoursMtd(),
        a.getLeaveBalance(),
        a.getLeaveSchedule());
  }
}
