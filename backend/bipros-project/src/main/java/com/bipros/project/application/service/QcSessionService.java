package com.bipros.project.application.service;

import com.bipros.common.event.QcTestFailedEvent;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.application.dto.CreateQcSessionRequest;
import com.bipros.project.application.dto.QcActivitySummary;
import com.bipros.project.application.dto.QcDashboardResponse;
import com.bipros.project.application.dto.QcSessionResponse;
import com.bipros.project.application.dto.QcTestItemResponse;
import com.bipros.project.application.dto.QcTestItemRow;
import com.bipros.project.application.dto.UpdateQcSessionRequest;
import com.bipros.project.domain.model.QcOutcome;
import com.bipros.project.domain.model.QcSession;
import com.bipros.project.domain.model.QcTestItem;
import com.bipros.project.domain.model.QcTestType;
import com.bipros.project.domain.repository.QcSessionRepository;
import com.bipros.project.domain.repository.QcTestItemRepository;
import com.bipros.project.domain.repository.QcTestTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class QcSessionService {

    private final QcSessionRepository sessionRepository;
    private final QcTestItemRepository itemRepository;
    private final QcTestTypeRepository typeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<QcSessionResponse> list(
            UUID projectId,
            UUID activityId,
            QcOutcome outcome,
            LocalDate from,
            LocalDate to) {
        return sessionRepository.findAllByProjectIdAndFilters(projectId, activityId, outcome, from, to)
            .stream()
            .map(QcSessionResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public QcSessionResponse get(UUID projectId, UUID id) {
        return QcSessionResponse.from(findSession(projectId, id));
    }

    public QcSessionResponse create(UUID projectId, CreateQcSessionRequest request) {
        QcSession session = QcSession.builder()
            .projectId(projectId)
            .activityId(request.activityId())
            .activityName(request.activityName())
            .testDate(request.testDate())
            .chainageFrom(request.chainageFrom())
            .chainageTo(request.chainageTo())
            .build();

        session = sessionRepository.save(session);
        session.setItems(buildItems(session, request.items(), projectId));
        session = sessionRepository.save(session);

        logFailEvents(session);
        return QcSessionResponse.from(session);
    }

    public QcSessionResponse update(UUID projectId, UUID id, UpdateQcSessionRequest request) {
        QcSession session = findSession(projectId, id);

        session.setActivityId(request.activityId());
        session.setActivityName(request.activityName());
        session.setTestDate(request.testDate());
        session.setChainageFrom(request.chainageFrom());
        session.setChainageTo(request.chainageTo());

        // Full-replacement semantics: delete all items and re-insert
        itemRepository.deleteBySessionId(session.getId());
        session.getItems().clear();
        session.getItems().addAll(buildItems(session, request.items(), projectId));
        session = sessionRepository.save(session);

        logFailEvents(session);
        return QcSessionResponse.from(session);
    }

    public void delete(UUID projectId, UUID id) {
        QcSession session = findSession(projectId, id);
        sessionRepository.delete(session);
    }

    @Transactional(readOnly = true)
    public QcDashboardResponse dashboard(UUID projectId) {
        long passCount = sessionRepository.countItemsByProjectIdAndOutcome(projectId, QcOutcome.PASS);
        long failCount = sessionRepository.countItemsByProjectIdAndOutcome(projectId, QcOutcome.FAIL);
        long repeatCount = sessionRepository.countItemsByProjectIdAndOutcome(projectId, QcOutcome.REPEAT);
        long totalTests = passCount + failCount + repeatCount;

        Double passRate = totalTests > 0
            ? BigDecimal.valueOf(passCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTests), 2, RoundingMode.HALF_UP)
                .doubleValue()
            : 0.0;

        long todayTests = sessionRepository.countTodayItems(projectId, LocalDate.now());

        List<QcActivitySummary> byActivity = buildActivitySummaries(projectId);

        List<QcTestItemResponse> recentFails = itemRepository
            .findByProjectIdAndOutcome(projectId, QcOutcome.FAIL, PageRequest.of(0, 5))
            .stream()
            .map(QcTestItemResponse::from)
            .toList();

        return new QcDashboardResponse(
            totalTests, passCount, failCount, repeatCount, passRate, todayTests, byActivity, recentFails);
    }

    private List<QcTestItem> buildItems(QcSession session, List<QcTestItemRow> rows, UUID projectId) {
        List<QcTestItem> items = new ArrayList<>();
        for (QcTestItemRow row : rows) {
            QcTestType type = typeRepository.findByIdAndProjectId(row.testTypeId(), projectId)
                .orElseThrow(() -> new ResourceNotFoundException("QcTestType", row.testTypeId()));

            BigDecimal requiredIrc = row.requiredIrc() != null ? row.requiredIrc() : type.getIrcThreshold();

            items.add(QcTestItem.builder()
                .session(session)
                .testTypeId(type.getId())
                .testTypeName(type.getName())
                .sampleRefNo(row.sampleRefNo())
                .testResult(row.testResult())
                .requiredIrc(requiredIrc)
                .outcome(row.outcome())
                .labInspector(row.labInspector())
                .build());
        }
        return items;
    }

    private void logFailEvents(QcSession session) {
        for (QcTestItem item : session.getItems()) {
            if (item.getOutcome() == QcOutcome.FAIL) {
                log.warn("QC FAIL — project={}, activity={}, testType={}, chainage={}-{}",
                    session.getProjectId(), session.getActivityName(),
                    item.getTestTypeName(), session.getChainageFrom(), session.getChainageTo());
                eventPublisher.publishEvent(new QcTestFailedEvent(
                    session.getProjectId(), session.getActivityId(), item.getId(),
                    item.getTestTypeName(), session.getChainageFrom(), item.getSampleRefNo()));
            }
        }
    }

    private List<QcActivitySummary> buildActivitySummaries(UUID projectId) {
        List<Object[]> rows = sessionRepository.groupByActivityAndOutcome(projectId);
        Map<UUID, QcActivitySummaryBuilder> builders = new HashMap<>();
        for (Object[] row : rows) {
            UUID activityId = (UUID) row[0];
            String activityName = (String) row[1];
            QcOutcome outcome = (QcOutcome) row[2];
            Long count = (Long) row[3];
            QcActivitySummaryBuilder b = builders.computeIfAbsent(activityId,
                k -> new QcActivitySummaryBuilder(activityId, activityName));
            switch (outcome) {
                case PASS -> b.pass += count;
                case FAIL -> b.fail += count;
                case REPEAT -> b.repeat += count;
            }
        }
        return builders.values().stream()
            .map(b -> new QcActivitySummary(b.activityId, b.activityName, b.pass, b.fail, b.repeat))
            .toList();
    }

    private QcSession findSession(UUID projectId, UUID id) {
        return sessionRepository.findByIdAndProjectId(id, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("QcSession", id));
    }

    private static class QcActivitySummaryBuilder {
        final UUID activityId;
        final String activityName;
        long pass;
        long fail;
        long repeat;

        QcActivitySummaryBuilder(UUID activityId, String activityName) {
            this.activityId = activityId;
            this.activityName = activityName;
        }
    }
}
