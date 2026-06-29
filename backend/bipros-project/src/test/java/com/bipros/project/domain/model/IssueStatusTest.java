package com.bipros.project.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IssueStatusTest {

    @Test
    void requiresAssignee_trueForWorkingAndTerminal() {
        assertThat(IssueStatus.IN_PROGRESS.requiresAssignee()).isTrue();
        assertThat(IssueStatus.BLOCKED.requiresAssignee()).isTrue();
        assertThat(IssueStatus.RESOLVED.requiresAssignee()).isTrue();
        assertThat(IssueStatus.CLOSED.requiresAssignee()).isTrue();
    }

    @Test
    void requiresAssignee_falseForOpenAndCancelled() {
        assertThat(IssueStatus.OPEN.requiresAssignee()).isFalse();
        assertThat(IssueStatus.CANCELLED.requiresAssignee()).isFalse();
    }

    @Test
    void resolvedAtTerminal_onlyResolvedAndClosed() {
        assertThat(IssueStatus.RESOLVED.resolvedAtTerminal()).isTrue();
        assertThat(IssueStatus.CLOSED.resolvedAtTerminal()).isTrue();
        assertThat(IssueStatus.BLOCKED.resolvedAtTerminal()).isFalse();
    }
}
