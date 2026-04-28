package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.ApprovalStepTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalStepTemplateRepository extends JpaRepository<ApprovalStepTemplate, UUID> {
    List<ApprovalStepTemplate> findByPermitTypeTemplateIdOrderByStepNoAsc(UUID permitTypeTemplateId);
}
