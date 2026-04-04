package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.AssetClass;
import com.bipros.project.domain.model.WbsTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WbsTemplateRepository extends JpaRepository<WbsTemplate, UUID> {

    Optional<WbsTemplate> findByCode(String code);

    List<WbsTemplate> findByIsActiveTrueOrderByAssetClassAscNameAsc();

    List<WbsTemplate> findByAssetClassOrderByNameAsc(AssetClass assetClass);
}
