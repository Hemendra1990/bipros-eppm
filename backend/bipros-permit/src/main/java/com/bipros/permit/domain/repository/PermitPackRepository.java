package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitPack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermitPackRepository extends JpaRepository<PermitPack, UUID> {
    Optional<PermitPack> findByCode(String code);

    List<PermitPack> findAllByActiveTrueOrderBySortOrderAsc();
}
