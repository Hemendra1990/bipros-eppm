package com.bipros.permit.application.service;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.permit.application.dto.DashboardSummaryResponse;
import com.bipros.permit.application.dto.PermitSummary;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.model.PermitWorker;
import com.bipros.permit.domain.repository.PermitRepository;
import com.bipros.permit.domain.repository.PermitTypeTemplateRepository;
import com.bipros.permit.domain.repository.PermitWorkerRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermitDashboardService {

    private final PermitRepository permitRepository;
    private final PermitWorkerRepository workerRepository;
    private final PermitTypeTemplateRepository typeTemplateRepository;
    private final ProjectAccessGuard projectAccess;
    private final EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public DashboardSummaryResponse summary(UUID projectId) {
        if (projectId != null) projectAccess.requireRead(projectId);
        Set<UUID> accessibleProjects = projectAccess.getAccessibleProjectIdsForCurrentUser();

        Instant now = Instant.now();
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant todayEnd = todayStart.plusSeconds(86400);
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();
        if (projectId != null) {
            where.append("AND p.project_id = :projectId ");
            params.put("projectId", projectId);
        } else if (accessibleProjects != null) {
            if (accessibleProjects.isEmpty()) {
                return new DashboardSummaryResponse(0, 0, 0, 0, new EnumMap<>(PermitStatus.class), List.of(), List.of());
            }
            where.append("AND p.project_id IN :pids ");
            params.put("pids", accessibleProjects);
        }

        // Status breakdown — single grouped query.
        String statusSql = "SELECT p.status, COUNT(*) FROM permit.permit p " + where + " GROUP BY p.status";
        var statusQuery = entityManager.createNativeQuery(statusSql);
        params.forEach(statusQuery::setParameter);
        List<Object[]> statusRows = statusQuery.getResultList();
        Map<PermitStatus, Long> breakdown = new EnumMap<>(PermitStatus.class);
        long activePermits = 0, pendingReview = 0, expiringToday = 0, closedThisMonth = 0;
        for (Object[] row : statusRows) {
            PermitStatus s = PermitStatus.valueOf((String) row[0]);
            long count = ((Number) row[1]).longValue();
            breakdown.put(s, count);
            if (s == PermitStatus.ISSUED || s == PermitStatus.IN_PROGRESS) activePermits += count;
            if (s == PermitStatus.PENDING_SITE_ENGINEER || s == PermitStatus.PENDING_HSE
                    || s == PermitStatus.AWAITING_GAS_TEST || s == PermitStatus.PENDING_PM) pendingReview += count;
        }

        // Expiring today.
        String expiringSql = "SELECT COUNT(*) FROM permit.permit p " + where
                + " AND p.end_at >= :s AND p.end_at < :e AND p.status IN ('ISSUED','IN_PROGRESS')";
        var expQuery = entityManager.createNativeQuery(expiringSql);
        params.forEach(expQuery::setParameter);
        expQuery.setParameter("s", todayStart);
        expQuery.setParameter("e", todayEnd);
        expiringToday = ((Number) expQuery.getSingleResult()).longValue();

        // Closed this month.
        String closedSql = "SELECT COUNT(*) FROM permit.permit p " + where
                + " AND p.closed_at >= :ms AND p.status = 'CLOSED'";
        var closedQuery = entityManager.createNativeQuery(closedSql);
        params.forEach(closedQuery::setParameter);
        closedQuery.setParameter("ms", monthStart);
        closedThisMonth = ((Number) closedQuery.getSingleResult()).longValue();

        // Active permits by type — for the bar chart.
        String byTypeSql = "SELECT p.permit_type_template_id, COUNT(*) FROM permit.permit p " + where
                + " AND p.status IN ('ISSUED','IN_PROGRESS') GROUP BY p.permit_type_template_id ORDER BY COUNT(*) DESC LIMIT 8";
        var typeQuery = entityManager.createNativeQuery(byTypeSql);
        params.forEach(typeQuery::setParameter);
        List<Object[]> typeRows = typeQuery.getResultList();
        Set<UUID> typeIds = typeRows.stream().map(r -> (UUID) r[0]).collect(Collectors.toSet());
        Map<UUID, PermitTypeTemplate> typeCache = typeTemplateRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(PermitTypeTemplate::getId, x -> x));
        List<DashboardSummaryResponse.PermitTypeCount> activeByType = typeRows.stream()
                .map(row -> {
                    UUID id = (UUID) row[0];
                    PermitTypeTemplate t = typeCache.get(id);
                    long count = ((Number) row[1]).longValue();
                    return new DashboardSummaryResponse.PermitTypeCount(
                            t != null ? t.getCode() : "?",
                            t != null ? t.getName() : "?",
                            t != null ? t.getColorHex() : null,
                            count);
                })
                .toList();

        // Recent activity — top 5 by createdAt.
        var recentPage = projectId != null
                ? permitRepository.findByProjectIdRecent(projectId,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                : permitRepository.findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<Permit> recent = recentPage.getContent();
        Map<UUID, PermitTypeTemplate> recentTypeCache = typeTemplateRepository.findAllById(
                recent.stream().map(Permit::getPermitTypeTemplateId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PermitTypeTemplate::getId, x -> x));

        Map<UUID, PermitWorker> principalByPermit = new HashMap<>();
        for (Permit p : recent) {
            workerRepository.findByPermitId(p.getId()).stream().findFirst()
                    .ifPresent(w -> principalByPermit.put(p.getId(), w));
        }
        List<PermitSummary> recentActivity = recent.stream()
                .map(p -> {
                    PermitTypeTemplate t = recentTypeCache.get(p.getPermitTypeTemplateId());
                    PermitWorker w = principalByPermit.get(p.getId());
                    return new PermitSummary(
                            p.getId(), p.getPermitCode(), p.getProjectId(), p.getPermitTypeTemplateId(),
                            t != null ? t.getCode() : null, t != null ? t.getName() : null,
                            t != null ? t.getColorHex() : null, t != null ? t.getIconKey() : null,
                            p.getStatus(), p.getRiskLevel(), p.getShift(), p.getTaskDescription(),
                            w != null ? w.getFullName() : null, w != null ? w.getNationality() : null,
                            p.getStartAt(), p.getEndAt());
                }).toList();

        return new DashboardSummaryResponse(activePermits, pendingReview, expiringToday, closedThisMonth,
                breakdown, activeByType, recentActivity);
    }
}
