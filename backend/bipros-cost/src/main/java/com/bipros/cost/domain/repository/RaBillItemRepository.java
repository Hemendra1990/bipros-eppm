package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.RaBillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RaBillItemRepository extends JpaRepository<RaBillItem, UUID> {
    List<RaBillItem> findByRaBillIdOrderByCreatedAt(UUID raBillId);
    void deleteByRaBillId(UUID raBillId);
}
