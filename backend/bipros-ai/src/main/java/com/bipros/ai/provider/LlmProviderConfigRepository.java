package com.bipros.ai.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LlmProviderConfigRepository extends JpaRepository<LlmProviderConfig, UUID> {

    Optional<LlmProviderConfig> findByName(String name);

    Optional<LlmProviderConfig> findByIsDefaultTrue();

    Optional<LlmProviderConfig> findByIsDefaultTrueAndIsActiveTrue();

    Optional<LlmProviderConfig> findFirstByIsActiveTrueOrderByIsDefaultDescCreatedAtAsc();
}
