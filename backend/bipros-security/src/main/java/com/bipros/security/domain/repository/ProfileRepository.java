package com.bipros.security.domain.repository;

import com.bipros.security.domain.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByCode(String code);

    boolean existsByCode(String code);
}
