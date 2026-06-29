package com.bipros.project.application.service;

import com.bipros.common.event.DprIssueChangedEvent;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDprIssueRequest;
import com.bipros.project.application.dto.UpdateDprIssueRequest;
import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.model.DprIssueStatusHistory;
import com.bipros.project.domain.model.IssueCategory;
import com.bipros.project.domain.model.IssueSeverity;
import com.bipros.project.domain.model.IssueStatus;
import com.bipros.project.domain.repository.DprIssueRepository;
import com.bipros.project.domain.repository.DprIssueStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DprIssueServiceTest {

    @Mock private DprIssueRepository issueRepository;
    @Mock private DprIssueStatusHistoryRepository historyRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ProjectAccessGuard projectAccessGuard;

    private DprIssueService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID issueId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID assigneeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DprIssueService(issueRepository, historyRepository, auditService,
                eventPublisher, projectAccessGuard);
        lenient().when(projectAccessGuard.currentUserId()).thenReturn(actorId);
        lenient().when(issueRepository.save(any(DprIssue.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private DprIssue openIssue() {
        return DprIssue.builder()
                .projectId(projectId)
                .category(IssueCategory.OTHER)
                .severity(IssueSeverity.MEDIUM)
                .status(IssueStatus.OPEN)
                .title("t")
                .openedAt(Instant.now())
                .build();
    }

    @Test
    void patch_statusChange_writesHistoryRowWithActor() {
        DprIssue issue = openIssue();
        issue.setAssignedToUserId(assigneeId);   // owner present so owner-rule passes
        when(issueRepository.findByIdAndProjectId(issueId, projectId)).thenReturn(Optional.of(issue));

        service.patch(projectId, issueId, new UpdateDprIssueRequest(
                null, null, null, null, IssueStatus.IN_PROGRESS,
                null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<DprIssueStatusHistory> cap = ArgumentCaptor.forClass(DprIssueStatusHistory.class);
        verify(historyRepository).save(cap.capture());
        assertThat(cap.getValue().getFromStatus()).isEqualTo(IssueStatus.OPEN);
        assertThat(cap.getValue().getToStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
        assertThat(cap.getValue().getActorUserId()).isEqualTo(actorId);
    }
}
