package com.bipros.api.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentMailLogRepository extends JpaRepository<AgentMailLog, UUID> {

    List<AgentMailLog> findTop100ByProjectIdOrderBySentAtDescIdDesc(UUID projectId);
}
