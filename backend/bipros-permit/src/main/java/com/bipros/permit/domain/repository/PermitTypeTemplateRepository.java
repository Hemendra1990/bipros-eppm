package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitTypeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermitTypeTemplateRepository extends JpaRepository<PermitTypeTemplate, UUID> {
    Optional<PermitTypeTemplate> findByCode(String code);

    List<PermitTypeTemplate> findAllByOrderBySortOrderAsc();
}
