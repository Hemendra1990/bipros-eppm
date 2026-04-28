package com.bipros.analytics.domain.repository;

import com.bipros.analytics.domain.model.UserLlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserLlmProviderRepository extends JpaRepository<UserLlmProvider, UUID> {

    List<UserLlmProvider> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<UserLlmProvider> findByUserIdAndIsDefaultTrue(UUID userId);

    Optional<UserLlmProvider> findByIdAndUserId(UUID id, UUID userId);
}
