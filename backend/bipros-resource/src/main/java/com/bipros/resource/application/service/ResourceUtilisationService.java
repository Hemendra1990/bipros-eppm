package com.bipros.resource.application.service;

import com.bipros.resource.domain.model.ResourceDailyLog;
import com.bipros.resource.domain.repository.ResourceDailyLogRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Records a daily deployment row for a resource. The post-rewrite Resource entity no longer carries
 * the M8 dashboard fields (utilisationStatus, dailyCostLakh etc.), so this service now just persists
 * the {@link ResourceDailyLog}; downstream dashboards aggregate over those rows directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceUtilisationService {

    private final ResourceRepository resourceRepository;
    private final ResourceDailyLogRepository logRepository;

    @Transactional
    public ResourceDailyLog recordDaily(UUID resourceId, LocalDate logDate,
                                        Double plannedUnits, Double actualUnits,
                                        String wbsPackageCode, String remarks) {
        if (!resourceRepository.existsById(resourceId)) {
            throw new IllegalArgumentException("Resource not found: " + resourceId);
        }

        Double pct = null;
        if (plannedUnits != null && plannedUnits > 0 && actualUnits != null) {
            pct = (actualUnits / plannedUnits) * 100.0;
        }

        ResourceDailyLog log = logRepository
            .findByResourceIdAndLogDate(resourceId, logDate)
            .orElseGet(ResourceDailyLog::new);
        log.setResourceId(resourceId);
        log.setLogDate(logDate);
        log.setPlannedUnits(plannedUnits);
        log.setActualUnits(actualUnits);
        log.setUtilisationPercent(pct);
        log.setWbsPackageCode(wbsPackageCode);
        log.setRemarks(remarks);
        return logRepository.save(log);
    }
}
