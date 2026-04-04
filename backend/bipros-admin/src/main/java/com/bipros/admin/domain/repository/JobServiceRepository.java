package com.bipros.admin.domain.repository;

import com.bipros.admin.domain.model.JobService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobServiceRepository extends JpaRepository<JobService, UUID> {
    Optional<JobService> findByName(String name);
}
