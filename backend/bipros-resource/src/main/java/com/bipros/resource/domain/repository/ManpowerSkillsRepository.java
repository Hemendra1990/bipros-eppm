package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.manpower.ManpowerSkills;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ManpowerSkillsRepository extends JpaRepository<ManpowerSkills, UUID> {

  long countBySkillLevel(String skillLevel);
}
