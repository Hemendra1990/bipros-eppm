package com.bipros.scheduling.domain.repository;

import com.bipros.scheduling.domain.model.ScheduleActivityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleActivityResultRepository extends JpaRepository<ScheduleActivityResult, UUID> {

  List<ScheduleActivityResult> findByScheduleResultId(UUID scheduleResultId);

  List<ScheduleActivityResult> findByScheduleResultIdAndIsCritical(UUID scheduleResultId, Boolean isCritical);
}
