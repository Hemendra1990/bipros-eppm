package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.master.NationalityMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NationalityMasterRepository extends JpaRepository<NationalityMaster, UUID> {

  Optional<NationalityMaster> findByCode(String code);

  Optional<NationalityMaster> findByName(String name);
}
