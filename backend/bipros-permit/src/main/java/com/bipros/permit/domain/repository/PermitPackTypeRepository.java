package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitPackType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermitPackTypeRepository extends JpaRepository<PermitPackType, UUID> {
    List<PermitPackType> findByPackIdOrderBySortOrderAsc(UUID packId);

    Optional<PermitPackType> findByPackIdAndPermitTypeTemplateId(UUID packId, UUID permitTypeTemplateId);
}
