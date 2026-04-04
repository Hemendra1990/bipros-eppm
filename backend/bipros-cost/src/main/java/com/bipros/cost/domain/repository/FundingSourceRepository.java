package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.FundingSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FundingSourceRepository extends JpaRepository<FundingSource, UUID> {
    Optional<FundingSource> findByCode(String code);
}
