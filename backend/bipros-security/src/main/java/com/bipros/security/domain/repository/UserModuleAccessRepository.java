package com.bipros.security.domain.repository;

import com.bipros.security.domain.model.IcpmsModule;
import com.bipros.security.domain.model.UserModuleAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserModuleAccessRepository extends JpaRepository<UserModuleAccess, UUID> {

    List<UserModuleAccess> findByUserId(UUID userId);

    Optional<UserModuleAccess> findByUserIdAndModule(UUID userId, IcpmsModule module);
}
