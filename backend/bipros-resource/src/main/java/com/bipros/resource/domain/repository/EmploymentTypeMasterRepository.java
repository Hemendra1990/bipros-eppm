package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.master.EmploymentTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmploymentTypeMasterRepository
    extends JpaRepository<EmploymentTypeMaster, UUID> {

  Optional<EmploymentTypeMaster> findByCode(String code);

  Optional<EmploymentTypeMaster> findByName(String name);
}
