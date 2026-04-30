package com.bipros.analytics.etl.watermark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EtlDeadLetterRepository extends JpaRepository<EtlDeadLetter, UUID> {
}
