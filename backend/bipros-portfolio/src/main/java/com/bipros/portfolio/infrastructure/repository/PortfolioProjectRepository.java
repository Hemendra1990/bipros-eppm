package com.bipros.portfolio.infrastructure.repository;

import com.bipros.portfolio.domain.PortfolioProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioProjectRepository extends JpaRepository<PortfolioProject, UUID> {

  List<PortfolioProject> findByPortfolioId(UUID portfolioId);

  Optional<PortfolioProject> findByPortfolioIdAndProjectId(UUID portfolioId, UUID projectId);

  void deleteByPortfolioIdAndProjectId(UUID portfolioId, UUID projectId);
}
