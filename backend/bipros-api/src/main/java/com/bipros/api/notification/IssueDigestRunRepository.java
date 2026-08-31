package com.bipros.api.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface IssueDigestRunRepository extends JpaRepository<IssueDigestRun, UUID> {
    boolean existsByProjectIdAndWeekStart(UUID projectId, LocalDate weekStart);
}
