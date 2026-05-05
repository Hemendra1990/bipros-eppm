package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.VoLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VoLineItemRepository extends JpaRepository<VoLineItem, UUID> {

  List<VoLineItem> findByVariationOrderId(UUID variationOrderId);

  void deleteByVariationOrderId(UUID variationOrderId);
}
