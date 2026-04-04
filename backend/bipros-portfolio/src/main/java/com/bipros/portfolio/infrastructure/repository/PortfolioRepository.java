package com.bipros.portfolio.infrastructure.repository;

import com.bipros.portfolio.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

  List<Portfolio> findByOwnerId(UUID ownerId);

  List<Portfolio> findByIsActiveTrue();
}
