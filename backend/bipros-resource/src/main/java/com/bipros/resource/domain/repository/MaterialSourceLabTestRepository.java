package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.MaterialSourceLabTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialSourceLabTestRepository
    extends JpaRepository<MaterialSourceLabTest, UUID> {

    List<MaterialSourceLabTest> findBySourceId(UUID sourceId);

    void deleteBySourceId(UUID sourceId);
}
