package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DprIssueStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DprIssueStatusHistoryRepository extends JpaRepository<DprIssueStatusHistory, UUID> {

    List<DprIssueStatusHistory> findByIssueIdOrderByCreatedAtAsc(UUID issueId);
}
