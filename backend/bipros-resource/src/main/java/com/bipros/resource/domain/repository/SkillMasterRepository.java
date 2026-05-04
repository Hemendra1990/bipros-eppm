package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.master.SkillMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillMasterRepository extends JpaRepository<SkillMaster, UUID> {

  Optional<SkillMaster> findByCode(String code);

  Optional<SkillMaster> findByName(String name);
}
