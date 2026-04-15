package com.bipros.resource.application.service;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceDailyLog;
import com.bipros.resource.domain.model.UtilisationStatus;
import com.bipros.resource.domain.repository.ResourceDailyLogRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * IC-PMS M8 aggregator: records a daily deployment row and rolls its utilisation %
 * onto the parent Resource, setting utilisationStatus band.
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
        Resource resource = resourceRepository.findById(resourceId)
            .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourceId));

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
        ResourceDailyLog saved = logRepository.save(log);

        // Roll onto parent resource
        resource.setPlannedUnitsToday(plannedUnits);
        resource.setActualUnitsToday(actualUnits);
        resource.setUtilisationPercent(pct);
        resource.setUtilisationStatus(deriveStatus(resource, pct));
        if (wbsPackageCode != null) {
            resource.setWbsAssignmentId(wbsPackageCode);
        }
        resourceRepository.save(resource);

        return saved;
    }

    /**
     * Band rule (Excel spec):
     * <100: ACTIVE, >=90 && <100: OVER_90, >=100: CRITICAL_100.
     * Non-mobilised / procurement states stay as-set on the Resource.
     */
    public static UtilisationStatus deriveStatus(Resource resource, Double utilisationPercent) {
        UtilisationStatus current = resource.getUtilisationStatus();
        if (current == UtilisationStatus.ON_HOLD_NOT_MOBILISED
            || current == UtilisationStatus.PROCUREMENT
            || current == UtilisationStatus.DELIVERY_ONGOING
            || current == UtilisationStatus.LAYING) {
            return current;
        }
        if (utilisationPercent == null) {
            return UtilisationStatus.ACTIVE;
        }
        if (utilisationPercent >= 100.0) {
            return UtilisationStatus.CRITICAL_100;
        }
        if (utilisationPercent >= 90.0) {
            return UtilisationStatus.OVER_90;
        }
        return UtilisationStatus.ACTIVE;
    }
}
