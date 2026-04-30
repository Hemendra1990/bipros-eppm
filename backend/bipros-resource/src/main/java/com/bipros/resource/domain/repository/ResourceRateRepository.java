package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ResourceRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResourceRateRepository extends JpaRepository<ResourceRate, UUID> {

  List<ResourceRate> findByResourceId(UUID resourceId);

  List<ResourceRate> findByResourceIdOrderByEffectiveDateDesc(UUID resourceId);

  List<ResourceRate> findByResourceIdAndRateTypeOrderByEffectiveDateDesc(
      UUID resourceId, String rateType);
}
