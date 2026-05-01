package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.manpower.ManpowerMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManpowerMasterRepository extends JpaRepository<ManpowerMaster, UUID> {

  Optional<ManpowerMaster> findByEmployeeCode(String code);
}
