package com.bipros.api.listener;

import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.api.notification.AgentMailLog;
import com.bipros.api.notification.AgentMailLogService;
import com.bipros.api.notification.DprAlertConfig;
import com.bipros.common.event.IssueAssignedEvent;
import com.bipros.common.notification.NotificationService;
import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.model.IssueCategory;
import com.bipros.project.domain.model.IssueSeverity;
import com.bipros.project.domain.model.IssueStatus;
import com.bipros.project.domain.repository.DprIssueRepository;
import com.bipros.security.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueAssignmentNotificationListener")
class IssueAssignmentNotificationListenerTest {

    @Mock private DprIssueRepository issueRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;
    @Mock private DprAlertConfig alertConfig;
    @Mock private AgentMailLogService mailLogService;

    private IssueAssignmentNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new IssueAssignmentNotificationListener(issueRepository, userRepository,
            emailService, notificationService, alertConfig, mailLogService);
    }

    private DprIssue issue(UUID projectId, UUID issueId, UUID assignee) {
        DprIssue i = DprIssue.builder()
            .projectId(projectId)
            .title("GSB blend below CBR spec")
            .category(IssueCategory.QUALITY)
            .severity(IssueSeverity.HIGH)
            .status(IssueStatus.IN_PROGRESS)
            .assignedToUserId(assignee)
            .assignedToName("RAVI")
            .reportDate(LocalDate.of(2026, 8, 10))
            .dueDate(LocalDate.of(2026, 8, 15))
            .openedAt(Instant.parse("2026-08-10T08:00:00Z"))
            .interventionRequired(true)
            .build();
        i.setId(issueId);
        return i;
    }

    @Test
    @DisplayName("1. assigned to an emailable user: email with title + due date, EMAIL and IN_APP rows logged")
    void assigned_emailableUser_sendsAndLogs() {
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();

        when(issueRepository.findByIdAndProjectId(issueId, projectId))
            .thenReturn(Optional.of(issue(projectId, issueId, assignee)));
        com.bipros.security.domain.model.User user = mock(com.bipros.security.domain.model.User.class);
        when(user.getEmail()).thenReturn("ravi@example.com");
        when(userRepository.findById(assignee)).thenReturn(Optional.of(user));
        when(alertConfig.channel()).thenReturn("EMAIL");
        when(alertConfig.appBaseUrl()).thenReturn("http://localhost:3000");
        when(emailService.send(any())).thenReturn(EmailService.SendResult.SENT);

        listener.onAssigned(new IssueAssignedEvent(projectId, issueId, assignee));

        ArgumentCaptor<EmailMessage> emailCap = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService, times(1)).send(emailCap.capture());
        assertThat(emailCap.getValue().to()).containsExactly("ravi@example.com");
        assertThat(emailCap.getValue().html()).contains("GSB blend below CBR spec").contains("2026-08-15");

        ArgumentCaptor<AgentMailLog> logCap = ArgumentCaptor.forClass(AgentMailLog.class);
        verify(mailLogService, times(2)).log(logCap.capture());
        assertThat(logCap.getAllValues()).anySatisfy(r -> {
            assertThat(r.getCategory()).isEqualTo(AgentMailLog.CAT_ISSUE_ASSIGNMENT);
            assertThat(r.getChannel()).isEqualTo(AgentMailLog.CH_EMAIL);
            assertThat(r.getStatus()).isEqualTo("SENT");
        });
        assertThat(logCap.getAllValues()).anySatisfy(r ->
            assertThat(r.getChannel()).isEqualTo(AgentMailLog.CH_IN_APP));
    }

    @Test
    @DisplayName("2. assignee without email: no send, SKIPPED EMAIL row + IN_APP row logged")
    void assigned_noEmail_logsSkipped() {
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();

        when(issueRepository.findByIdAndProjectId(issueId, projectId))
            .thenReturn(Optional.of(issue(projectId, issueId, assignee)));
        com.bipros.security.domain.model.User user = mock(com.bipros.security.domain.model.User.class);
        when(user.getEmail()).thenReturn(null);
        when(userRepository.findById(assignee)).thenReturn(Optional.of(user));

        listener.onAssigned(new IssueAssignedEvent(projectId, issueId, assignee));

        verify(emailService, never()).send(any());
        ArgumentCaptor<AgentMailLog> logCap = ArgumentCaptor.forClass(AgentMailLog.class);
        verify(mailLogService, times(2)).log(logCap.capture());
        assertThat(logCap.getAllValues()).anySatisfy(r -> {
            assertThat(r.getChannel()).isEqualTo(AgentMailLog.CH_EMAIL);
            assertThat(r.getStatus()).isEqualTo(AgentMailLog.STATUS_SKIPPED);
        });
    }
}
