package com.bipros.project.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Repository for counting activities from the activity schema.
 * Uses EntityManager with native queries to avoid circular dependency between modules.
 */
@Component
public class ProjectActivityCounter {

  @PersistenceContext
  private EntityManager entityManager;

  public long countActivitiesByProjectId(UUID projectId) {
    return ((Number) entityManager
        .createNativeQuery("SELECT COUNT(*) FROM activity.activities WHERE project_id = ?1")
        .setParameter(1, projectId)
        .getSingleResult()).longValue();
  }

  public long countActivitiesByWbsNodeId(UUID wbsNodeId) {
    return ((Number) entityManager
        .createNativeQuery("SELECT COUNT(*) FROM activity.activities WHERE wbs_node_id = ?1")
        .setParameter(1, wbsNodeId)
        .getSingleResult()).longValue();
  }
}
