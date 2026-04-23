package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.ProductivityNormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductivityNormRepository extends JpaRepository<ProductivityNorm, UUID> {
  List<ProductivityNorm> findByNormType(ProductivityNormType normType);

  List<ProductivityNorm> findByActivityNameIgnoreCase(String activityName);
}
