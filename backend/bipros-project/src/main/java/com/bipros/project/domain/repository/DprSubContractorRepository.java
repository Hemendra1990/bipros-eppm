package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DprSubContractor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DprSubContractorRepository extends JpaRepository<DprSubContractor, UUID> {

  List<DprSubContractor> findByDprIdOrderBySubContractorNameAsc(UUID dprId);

  List<DprSubContractor> findByDprIdIn(Collection<UUID> dprIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  void deleteByDprId(UUID dprId);
}
