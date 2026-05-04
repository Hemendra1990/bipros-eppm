package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.master.SkillLevelMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillLevelMasterRepository extends JpaRepository<SkillLevelMaster, UUID> {

  Optional<SkillLevelMaster> findByCode(String code);

  Optional<SkillLevelMaster> findByName(String name);
}
