package com.bipros.ai.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WbsAiJobRepository extends JpaRepository<WbsAiJob, UUID> {
}
