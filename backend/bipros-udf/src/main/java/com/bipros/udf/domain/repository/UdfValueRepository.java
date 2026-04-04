package com.bipros.udf.domain.repository;

import com.bipros.udf.domain.model.UdfValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UdfValueRepository extends JpaRepository<UdfValue, UUID> {
    List<UdfValue> findByEntityId(UUID entityId);

    List<UdfValue> findByUserDefinedFieldId(UUID userDefinedFieldId);

    Optional<UdfValue> findByUserDefinedFieldIdAndEntityId(UUID userDefinedFieldId, UUID entityId);
}
