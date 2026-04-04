package com.bipros.document.domain.repository;

import com.bipros.document.domain.model.RfiRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RfiRegisterRepository extends JpaRepository<RfiRegister, UUID> {
    List<RfiRegister> findByProjectId(UUID projectId);

    Optional<RfiRegister> findByProjectIdAndId(UUID projectId, UUID id);

    Optional<RfiRegister> findByRfiNumber(String rfiNumber);
}
