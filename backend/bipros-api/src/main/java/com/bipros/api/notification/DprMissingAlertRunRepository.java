package com.bipros.api.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface DprMissingAlertRunRepository extends JpaRepository<DprMissingAlertRun, UUID> {
    boolean existsByProjectIdAndAlertDate(UUID projectId, LocalDate alertDate);

    java.util.Optional<DprMissingAlertRun> findTopByProjectIdOrderByGeneratedAtDesc(UUID projectId);
}
