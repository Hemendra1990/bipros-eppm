package com.bipros.project.application.service;

import com.bipros.common.event.DprIssueChangedEvent;
import com.bipros.common.event.DprMutationType;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDprIssueRequest;
import com.bipros.project.application.dto.DprIssueRow;
import com.bipros.project.application.dto.DprIssueStatusHistoryRow;
import com.bipros.project.application.dto.UpdateDprIssueRequest;
import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.model.DprIssueStatusHistory;
import com.bipros.project.domain.model.IssueCategory;
import com.bipros.project.domain.model.IssueSeverity;
import com.bipros.project.domain.model.IssueStatus;
import com.bipros.project.domain.repository.DprIssueRepository;
import com.bipros.project.domain.repository.DprIssueStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Handles DPR-issue mutations that happen <i>outside</i> the parent DPR's create/update flow:
 * direct PATCH for status flips, reassignment, resolution notes; DELETE for hard removal; GET
 * for the future Issues dashboard. Creation always routes through
 * {@link DailyProgressReportService} so no orphan issues can exist.
 *
 * <p>Status transitions auto-manage {@code resolvedAt}: entering RESOLVED/CLOSED stamps now();
 * leaving them clears it. Publishes a {@link DprIssueChangedEvent} so the analytics listener can
 * upsert the row in {@code fact_dpr_issues_daily} without touching the rest of the DPR.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DprIssueService {

    private final DprIssueRepository issueRepository;
    private final DprIssueStatusHistoryRepository historyRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectAccessGuard projectAccessGuard;

    @Transactional(readOnly = true)
    public List<DprIssueRow> list(
            UUID projectId,
            IssueStatus status,
            IssueSeverity severity,
            IssueCategory category,
            UUID supervisorUserId,
            UUID activityId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String q) {
        return issueRepository.findByProjectIdOrderByOpenedAtDesc(projectId).stream()
                .filter(i -> status == null || i.getStatus() == status)
                .filter(i -> severity == null || i.getSeverity() == severity)
                .filter(i -> category == null || i.getCategory() == category)
                .filter(i -> supervisorUserId == null
                        || supervisorUserId.equals(i.getSupervisorUserId()))
                .filter(i -> activityId == null || activityId.equals(i.getActivityId()))
                .filter(i -> dateFrom == null || !i.getReportDate().isBefore(dateFrom))
                .filter(i -> dateTo == null || !i.getReportDate().isAfter(dateTo))
                .filter(i -> q == null || q.isBlank() || matchesQ(i, q))
                .map(DprIssueRow::from)
                .toList();
    }

    private static boolean matchesQ(DprIssue i, String q) {
        String needle = q.toLowerCase();
        boolean inTitle = i.getTitle() != null && i.getTitle().toLowerCase().contains(needle);
        boolean inDesc = i.getDescription() != null && i.getDescription().toLowerCase().contains(needle);
        return inTitle || inDesc;
    }

    @Transactional(readOnly = true)
    public DprIssueRow get(UUID projectId, UUID id) {
        return DprIssueRow.from(findIssue(projectId, id));
    }

    public DprIssueRow patch(UUID projectId, UUID id, UpdateDprIssueRequest request) {
        DprIssue issue = findIssue(projectId, id);
        DprIssueRow before = DprIssueRow.from(issue);
        IssueStatus oldStatus = issue.getStatus();

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BusinessRuleException("DPR_ISSUE_INVALID", "Issue title cannot be blank.");
            }
            issue.setTitle(request.title());
        }
        if (request.description() != null) issue.setDescription(request.description());
        if (request.category() != null) issue.setCategory(request.category());
        if (request.severity() != null) issue.setSeverity(request.severity());
        if (request.supervisorUserId() != null) {
            issue.setSupervisorUserId(request.supervisorUserId());
        }
        if (request.supervisorName() != null) issue.setSupervisorName(request.supervisorName());
        if (request.assignedToUserId() != null) {
            issue.setAssignedToUserId(request.assignedToUserId());
        }
        if (request.assignedToName() != null) issue.setAssignedToName(request.assignedToName());
        if (request.resolutionNotes() != null) issue.setResolutionNotes(request.resolutionNotes());
        if (request.activityId() != null) issue.setActivityId(request.activityId());
        if (request.activityName() != null) issue.setActivityName(request.activityName());
        // Unconditional (NOT null-guarded): the IssueForm always sends the full body, so clearing the
        // HSE-type dropdown to blank OR re-categorizing away from SAFETY/ENVIRONMENTAL (both send
        // hseIncidentType=null) correctly UN-classifies the incident and lowers the headline count.
        // A future partial-PATCH caller must therefore always include hseIncidentType in its body.
        issue.setHseIncidentType(request.hseIncidentType());

        IssueStatus newStatus = request.status() != null ? request.status() : oldStatus;
        if (request.status() != null && newStatus != oldStatus) {
            issue.setStatus(newStatus);
            boolean wasTerminal = oldStatus != null && oldStatus.resolvedAtTerminal();
            boolean isTerminal = newStatus.resolvedAtTerminal();
            if (!wasTerminal && isTerminal) {
                issue.setResolvedAt(Instant.now());
            } else if (wasTerminal && !isTerminal) {
                issue.setResolvedAt(null);
            }
            String historyReason =
                    request.statusChangeReason() != null && !request.statusChangeReason().isBlank()
                            ? request.statusChangeReason()
                            : (newStatus.resolvedAtTerminal() ? issue.getResolutionNotes() : null);
            appendStatusHistory(issue.getId(), oldStatus, newStatus, historyReason);
        }

        validateConditionalFields(newStatus, issue.getAssignedToUserId(), issue.getResolutionNotes());
        DprIssue saved = issueRepository.save(issue);
        DprIssueRow after = DprIssueRow.from(saved);
        auditService.logUpdate("DprIssue", saved.getId(), "row", before, after);
        eventPublisher.publishEvent(new DprIssueChangedEvent(
                saved.getProjectId(), saved.getDprId(), saved.getId(),
                oldStatus != null ? oldStatus.name() : null,
                saved.getStatus().name(),
                DprMutationType.UPDATED));
        return after;
    }

    public void delete(UUID projectId, UUID id) {
        DprIssue issue = findIssue(projectId, id);
        IssueStatus oldStatus = issue.getStatus();
        UUID dprId = issue.getDprId();
        issueRepository.delete(issue);
        auditService.logDelete("DprIssue", id);
        eventPublisher.publishEvent(new DprIssueChangedEvent(
                projectId, dprId, id,
                oldStatus != null ? oldStatus.name() : null, null,
                DprMutationType.DELETED));
    }

    public DprIssueRow create(UUID projectId, CreateDprIssueRequest req) {
        IssueStatus status = req.status() != null ? req.status() : IssueStatus.OPEN;
        validateConditionalFields(status, req.assignedToUserId(), null /* no resolution notes on create */);
        DprIssue issue = DprIssue.builder()
                .dprId(null)
                .projectId(projectId)
                .activityId(req.activityId())
                .activityName(req.activityName())
                .supervisorResourceId(req.supervisorResourceId())
                .supervisorName(req.supervisorName())
                .assignedToResourceId(
                        req.assignedToResourceId() != null
                                ? req.assignedToResourceId()
                                : req.supervisorResourceId())
                .assignedToName(
                        req.assignedToName() != null ? req.assignedToName() : req.supervisorName())
                .supervisorUserId(req.supervisorUserId())
                .assignedToUserId(req.assignedToUserId())
                .reportDate(req.reportDate() != null ? req.reportDate() : LocalDate.now())
                .category(req.category())
                .severity(req.severity())
                .status(status)
                .title(req.title())
                .description(req.description())
                .openedAt(Instant.now())
                .resolvedAt(status.resolvedAtTerminal() ? Instant.now() : null)
                .hseIncidentType(req.hseIncidentType())
                .build();
        DprIssue saved = issueRepository.save(issue);
        appendStatusHistory(saved.getId(), null, saved.getStatus(),
                saved.getStatus().resolvedAtTerminal() ? saved.getResolutionNotes() : null);
        auditService.logCreate("DprIssue", saved.getId(), DprIssueRow.from(saved));
        eventPublisher.publishEvent(new DprIssueChangedEvent(
                projectId, null, saved.getId(), null, status.name(), DprMutationType.CREATED));
        return DprIssueRow.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DprIssueStatusHistoryRow> history(UUID projectId, UUID id) {
        findIssue(projectId, id); // scope check — throws if not in project
        return historyRepository.findByIssueIdOrderByCreatedAtAsc(id).stream()
                .map(DprIssueStatusHistoryRow::from)
                .toList();
    }

    private void appendStatusHistory(UUID issueId, IssueStatus from, IssueStatus to, String reason) {
        historyRepository.save(DprIssueStatusHistory.builder()
                .issueId(issueId)
                .fromStatus(from)
                .toStatus(to)
                .actorUserId(projectAccessGuard.currentUserId())
                .reason(reason)
                .build());
    }

    /**
     * Cross-field mandatory rules enforced authoritatively here (not via bean annotations)
     * because they depend on the resulting status. Owner required for working/terminal
     * statuses; resolution notes required for terminal statuses.
     */
    private void validateConditionalFields(IssueStatus status, UUID assignedToUserId, String resolutionNotes) {
        if (status.requiresAssignee() && assignedToUserId == null) {
            throw new BusinessRuleException("DPR_ISSUE_INVALID",
                "Assigned To is required when status is " + status.name() + ".");
        }
        if (status.resolvedAtTerminal() && (resolutionNotes == null || resolutionNotes.isBlank())) {
            throw new BusinessRuleException("DPR_ISSUE_INVALID",
                "Resolution notes are required to mark an issue " + status.name() + ".");
        }
    }

    private DprIssue findIssue(UUID projectId, UUID id) {
        return issueRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("DprIssue", id));
    }
}
