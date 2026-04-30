package com.bipros.analytics.etl.watermark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EtlWatermarkRepository extends JpaRepository<EtlWatermark, EtlWatermark.WatermarkId> {
}
