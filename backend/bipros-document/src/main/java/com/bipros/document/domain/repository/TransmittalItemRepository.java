package com.bipros.document.domain.repository;

import com.bipros.document.domain.model.TransmittalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransmittalItemRepository extends JpaRepository<TransmittalItem, UUID> {
    List<TransmittalItem> findByTransmittalId(UUID transmittalId);
}
