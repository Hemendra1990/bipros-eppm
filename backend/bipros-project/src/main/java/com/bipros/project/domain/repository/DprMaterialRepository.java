package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DprMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DprMaterialRepository extends JpaRepository<DprMaterial, UUID> {

    List<DprMaterial> findByDprIdOrderByMaterialNameAsc(UUID dprId);

    List<DprMaterial> findByDprIdIn(Collection<UUID> dprIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByDprId(UUID dprId);
}
