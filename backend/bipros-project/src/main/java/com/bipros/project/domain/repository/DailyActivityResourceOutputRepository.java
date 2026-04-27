package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DailyActivityResourceOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyActivityResourceOutputRepository
    extends JpaRepository<DailyActivityResourceOutput, UUID> {

  List<DailyActivityResourceOutput>
      findByProjectIdOrderByOutputDateDescIdAsc(UUID projectId);

  List<DailyActivityResourceOutput>
      findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(
          UUID projectId, LocalDate fromDate, LocalDate toDate);

  List<DailyActivityResourceOutput>
      findByProjectIdAndActivityIdOrderByOutputDateDescIdAsc(UUID projectId, UUID activityId);

  List<DailyActivityResourceOutput>
      findByProjectIdAndResourceIdOrderByOutputDateDescIdAsc(UUID projectId, UUID resourceId);

  Optional<DailyActivityResourceOutput>
      findFirstByProjectIdAndOutputDateAndActivityIdAndResourceId(
          UUID projectId, LocalDate outputDate, UUID activityId, UUID resourceId);
}
