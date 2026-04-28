package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PpeItemTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PpeItemTemplateRepository extends JpaRepository<PpeItemTemplate, UUID> {
    Optional<PpeItemTemplate> findByCode(String code);

    List<PpeItemTemplate> findAllByOrderBySortOrderAsc();
}
