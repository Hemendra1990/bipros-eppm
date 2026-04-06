package com.bipros.scheduling.domain.algorithm;

import java.time.LocalDate;
import java.util.UUID;

public class ScheduledActivity {

  private final UUID activityId;
  private LocalDate earlyStart;
  private LocalDate earlyFinish;
  private LocalDate lateStart;
  private LocalDate lateFinish;
  private double totalFloat;
  private double freeFloat;
  private boolean isCritical;
  private double remainingDuration;

  public ScheduledActivity(UUID activityId, double remainingDuration) {
    this.activityId = activityId;
    this.remainingDuration = remainingDuration;
    this.totalFloat = 0.0;
    this.freeFloat = Double.MAX_VALUE;
    this.isCritical = false;
  }

  public UUID getActivityId() {
    return activityId;
  }

  public LocalDate getEarlyStart() {
    return earlyStart;
  }

  public void setEarlyStart(LocalDate earlyStart) {
    this.earlyStart = earlyStart;
  }

  public LocalDate getEarlyFinish() {
    return earlyFinish;
  }

  public void setEarlyFinish(LocalDate earlyFinish) {
    this.earlyFinish = earlyFinish;
  }

  public LocalDate getLateStart() {
    return lateStart;
  }

  public void setLateStart(LocalDate lateStart) {
    this.lateStart = lateStart;
  }

  public LocalDate getLateFinish() {
    return lateFinish;
  }

  public void setLateFinish(LocalDate lateFinish) {
    this.lateFinish = lateFinish;
  }

  public double getTotalFloat() {
    return totalFloat;
  }

  public void setTotalFloat(double totalFloat) {
    this.totalFloat = totalFloat;
  }

  public double getFreeFloat() {
    return freeFloat;
  }

  public void setFreeFloat(double freeFloat) {
    this.freeFloat = freeFloat;
  }

  public boolean isCritical() {
    return isCritical;
  }

  public void setCritical(boolean critical) {
    isCritical = critical;
  }

  public double getRemainingDuration() {
    return remainingDuration;
  }

  public void setRemainingDuration(double remainingDuration) {
    this.remainingDuration = remainingDuration;
  }
}
