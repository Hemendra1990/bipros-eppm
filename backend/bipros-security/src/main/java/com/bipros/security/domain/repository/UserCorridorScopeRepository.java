package com.bipros.security.domain.repository;

import com.bipros.security.domain.model.UserCorridorScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserCorridorScopeRepository extends JpaRepository<UserCorridorScope, UUID> {

    List<UserCorridorScope> findByUserId(UUID userId);
}
