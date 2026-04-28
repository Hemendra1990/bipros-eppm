package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitTypePpe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermitTypePpeRepository extends JpaRepository<PermitTypePpe, UUID> {
    List<PermitTypePpe> findByPermitTypeTemplateId(UUID permitTypeTemplateId);

    Optional<PermitTypePpe> findByPermitTypeTemplateIdAndPpeItemTemplateId(UUID permitTypeTemplateId, UUID ppeItemTemplateId);
}
