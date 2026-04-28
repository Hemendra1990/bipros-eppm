package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ResourceDailyLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceDailyLogRepository extends JpaRepository<ResourceDailyLog, UUID> {

    Page<ResourceDailyLog> findByUpdatedAtAfter(Instant since, Pageable pageable);


    List<ResourceDailyLog> findByResourceIdOrderByLogDateDesc(UUID resourceId);

    Optional<ResourceDailyLog> findByResourceIdAndLogDate(UUID resourceId, LocalDate logDate);

    List<ResourceDailyLog> findByLogDate(LocalDate logDate);
}
