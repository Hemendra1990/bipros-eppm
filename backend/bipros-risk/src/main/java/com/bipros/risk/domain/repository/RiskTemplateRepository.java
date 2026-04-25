package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.RiskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskTemplateRepository extends JpaRepository<RiskTemplate, UUID> {

    Optional<RiskTemplate> findByCode(String code);

    List<RiskTemplate> findByActive(Boolean active);

    List<RiskTemplate> findByIndustry(Industry industry);
}
