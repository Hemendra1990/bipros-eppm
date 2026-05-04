package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManpowerCategoryMasterRepository
    extends JpaRepository<ManpowerCategoryMaster, UUID> {

  Optional<ManpowerCategoryMaster> findByCode(String code);

  Optional<ManpowerCategoryMaster> findByName(String name);

  List<ManpowerCategoryMaster> findByParentIdIsNull();

  List<ManpowerCategoryMaster> findByParentId(UUID parentId);

  long countByParentId(UUID parentId);
}
