# Issues Tab Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix and polish the Project Issues tab — rename "Blocked"→"On Hold" (label-only), wire a real project-team assignee picker, add conditional-mandatory validation, add a status-change history timeline, restructure the edit/new forms, and add free-text search to the list.

**Architecture:** Backend `bipros-project` module owns the domain (`DprIssue`, `IssueStatus`, `DprIssueService`, `DprIssueController`) plus a new append-only `DprIssueStatusHistory` entity modeled on the existing `DprApprovalHistory`. Cross-field mandatory rules live in the service (authoritative), not bean annotations, because they depend on the *resulting* status. Frontend talks to it through `dprIssueApi` + `projectTeamApi`; one project-team fetch feeds both the assignee picker and history actor-name resolution.

**Tech Stack:** Java 23 / Spring Boot 3.5, JPA (`ddl-auto: update` in dev, Liquibase in prod), JUnit 5 + Mockito; Next.js 16 / React 19, TanStack Query, Vitest + Testing Library, Tailwind.

## Global Constraints

- "On Hold" is **label-only**: stored enum value stays `BLOCKED`. No enum-value rename, no data migration, no change to any `=== "BLOCKED"` logic, AI tool strings, or analytics. Copy verbatim: display label is `On Hold`.
- Assignee picker source: `projectTeamApi.list(projectId)` (project org chart). On select, set BOTH `assignedToUserId` (the member's `userId`) and `assignedToName` (display label).
- Mandatory rules (conditional, enforced FE + BE):
  - **Status**: always required (already defaulted; assert explicitly).
  - **Assigned To**: required when resulting status ∈ {`IN_PROGRESS`, `BLOCKED`, `RESOLVED`, `CLOSED`}. Not required for fresh `OPEN`/`CANCELLED`.
  - **Resolution Notes**: shown AND required only when status ∈ {`RESOLVED`, `CLOSED`}. Hidden otherwise. `CANCELLED` exempt.
- Status-change history: status transitions only, append-only, written synchronously inside the service; `reason` = the resolution notes on terminal transitions, else null; actor = `ProjectAccessGuard.currentUserId()`.
- Search: in-memory stream filter on the existing list path (no JPQL) — new `q` param, case-insensitive contains on title + description.
- BE error pattern for cross-field violations: `throw new BusinessRuleException("DPR_ISSUE_INVALID", "<message>");`
- FE money/currency: not applicable to this feature — no `useProjectCurrency` usage.
- Backend run gotcha (project memory): after editing a sibling module, `mvn -pl bipros-project install` BEFORE running `bipros-api`, or new routes 404 from stale `~/.m2` jars.
- FE test run command: `cd frontend && npx vitest run <path>` (there is no `test` npm script; `vitest.config.mts` exists).

---

### Task 1: `IssueStatus.requiresAssignee()` helper (BE)

**Files:**
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/domain/model/IssueStatus.java`
- Test: `backend/bipros-project/src/test/java/com/bipros/project/domain/model/IssueStatusTest.java` (create)

**Interfaces:**
- Produces: `boolean IssueStatus.requiresAssignee()` — true for `IN_PROGRESS`, `BLOCKED`, `RESOLVED`, `CLOSED`; false for `OPEN`, `CANCELLED`. (`resolvedAtTerminal()` already exists for `RESOLVED`/`CLOSED`.)

- [ ] **Step 1: Write the failing test**

Create `IssueStatusTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl bipros-project test -Dtest=IssueStatusTest -q`
Expected: FAIL — `cannot find symbol: method requiresAssignee()`.

- [ ] **Step 3: Add the helper**

In `IssueStatus.java`, after the existing `RESOLVED_TERMINAL` set and `resolvedAtTerminal()` method (around line 24), add:

```java
    private static final Set<IssueStatus> REQUIRES_ASSIGNEE =
        EnumSet.of(IN_PROGRESS, BLOCKED, RESOLVED, CLOSED);

    /** Statuses where an issue must have an owner (assigned-to user). */
    public boolean requiresAssignee() {
        return REQUIRES_ASSIGNEE.contains(this);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -pl bipros-project test -Dtest=IssueStatusTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/domain/model/IssueStatus.java backend/bipros-project/src/test/java/com/bipros/project/domain/model/IssueStatusTest.java
git commit -m "feat(issues): add IssueStatus.requiresAssignee() helper"
```

---

### Task 2: `DprIssueStatusHistory` entity + repository + Liquibase (BE)

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/domain/model/DprIssueStatusHistory.java`
- Create: `backend/bipros-project/src/main/java/com/bipros/project/domain/repository/DprIssueStatusHistoryRepository.java`
- Create: `backend/bipros-api/src/main/resources/db/changelog/112-dpr-issue-status-history.yaml`
- Modify: `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml` (append include)

**Interfaces:**
- Produces:
  - Entity `DprIssueStatusHistory` (builder) with: `UUID issueId`, `IssueStatus fromStatus` (nullable), `IssueStatus toStatus` (non-null), `UUID actorUserId` (nullable), `String reason` (nullable, ≤1000). `createdAt` from `BaseEntity` is the transition time.
  - `DprIssueStatusHistoryRepository.findByIssueIdOrderByCreatedAtAsc(UUID issueId) : List<DprIssueStatusHistory>`

- [ ] **Step 1: Create the entity**

Create `DprIssueStatusHistory.java` (mirrors `DprApprovalHistory.java`):

```java
package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Append-only audit log of each {@link DprIssue} status transition.
 * {@code createdAt} (from {@link BaseEntity}) records the transition time.
 */
@Entity
@Table(
    name = "dpr_issue_status_history",
    schema = "project",
    indexes = {
        @Index(name = "idx_dpr_issue_status_history_issue", columnList = "issue_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DprIssueStatusHistory extends BaseEntity {

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private IssueStatus fromStatus;   // null for the initial transition (create)

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private IssueStatus toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;          // null for system/seeder actions

    @Column(name = "reason", length = 1000)
    private String reason;
}
```

- [ ] **Step 2: Create the repository**

Create `DprIssueStatusHistoryRepository.java`:

```java
package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DprIssueStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DprIssueStatusHistoryRepository extends JpaRepository<DprIssueStatusHistory, UUID> {

    List<DprIssueStatusHistory> findByIssueIdOrderByCreatedAtAsc(UUID issueId);
}
```

- [ ] **Step 3: Create the Liquibase changeset**

Create `112-dpr-issue-status-history.yaml` (mirrors changeset `108-2`):

```yaml
databaseChangeLog:
  # Append-only audit log of each DprIssue status transition. Soft FK (issue_id) to
  # project.dpr_issues.id. createdAt records the transition time; the row is never mutated.
  - changeSet:
      id: 112-1-dpr-issue-status-history-table
      author: bipros
      comment: |
        Append-only status-change history for field issues. One row is written by
        DprIssueService on create (from_status null) and on every status transition.
        Dev ddl-auto:update creates this table automatically; this changeset is the
        prod source of truth.
      changes:
        - createTable:
            schemaName: project
            tableName: dpr_issue_status_history
            columns:
              - column:
                  name: id
                  type: UUID
                  constraints: { primaryKey: true, nullable: false }
              - column: { name: issue_id, type: UUID, constraints: { nullable: false } }
              - column: { name: from_status, type: VARCHAR(20) }
              - column:
                  name: to_status
                  type: VARCHAR(20)
                  constraints: { nullable: false }
              - column: { name: actor_user_id, type: UUID }
              - column: { name: reason, type: VARCHAR(1000) }
              - column: { name: created_at, type: TIMESTAMP, constraints: { nullable: false } }
              - column: { name: updated_at, type: TIMESTAMP, constraints: { nullable: false } }
              - column: { name: created_by, type: VARCHAR(255) }
              - column: { name: updated_by, type: VARCHAR(255) }
              - column: { name: version, type: BIGINT }
        - createIndex:
            schemaName: project
            tableName: dpr_issue_status_history
            indexName: idx_dpr_issue_status_history_issue
            columns:
              - column: { name: issue_id }
      rollback:
        - dropTable:
            schemaName: project
            tableName: dpr_issue_status_history
```

- [ ] **Step 4: Register the changeset in the master changelog**

In `db.changelog-master.yaml`, append after the final existing `- include:` block (the `111-compression-finish-dates.yaml` include):

```yaml
  - include:
      file: db/changelog/112-dpr-issue-status-history.yaml
```

- [ ] **Step 5: Compile to verify entity + repo wire up**

Run: `cd backend && mvn -pl bipros-project test-compile -q`
Expected: BUILD SUCCESS (no test yet; compilation proves the JPA mappings and repo signature are valid).

- [ ] **Step 6: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/domain/model/DprIssueStatusHistory.java backend/bipros-project/src/main/java/com/bipros/project/domain/repository/DprIssueStatusHistoryRepository.java backend/bipros-api/src/main/resources/db/changelog/112-dpr-issue-status-history.yaml backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat(issues): add DprIssueStatusHistory entity, repo, and migration"
```

---

### Task 3: History DTO + endpoint, and history writes in service (BE)

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/application/dto/DprIssueStatusHistoryRow.java`
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/application/service/DprIssueService.java`
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/api/DprIssueController.java`
- Test: `backend/bipros-project/src/test/java/com/bipros/project/application/service/DprIssueServiceTest.java` (create)

**Interfaces:**
- Consumes: `DprIssueStatusHistoryRepository` (Task 2), `IssueStatus.requiresAssignee()` (Task 1), `ProjectAccessGuard.currentUserId()` (existing, `com.bipros.common.security.ProjectAccessGuard`).
- Produces:
  - `record DprIssueStatusHistoryRow(UUID id, IssueStatus fromStatus, IssueStatus toStatus, UUID actorUserId, String reason, Instant createdAt)` with `static from(DprIssueStatusHistory)`.
  - `List<DprIssueStatusHistoryRow> DprIssueService.history(UUID projectId, UUID id)`.
  - `GET /v1/projects/{projectId}/dpr-issues/{id}/history`.
  - Private `DprIssueService.appendStatusHistory(UUID issueId, IssueStatus from, IssueStatus to, String reason)`.

- [ ] **Step 1: Write the failing test**

Create `DprIssueServiceTest.java`. This test class is reused and extended by Tasks 4 and 5.

```java
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
```

> Note: `UpdateDprIssueRequest`'s constructor arg order is, in order:
> `title, description, category, severity, status, supervisorResourceId, supervisorName, assignedToResourceId, assignedToName, resolutionNotes, supervisorUserId, assignedToUserId, activityId, activityName`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl bipros-project test -Dtest=DprIssueServiceTest -q`
Expected: FAIL — `DprIssueService` constructor does not accept 5 args / `historyRepository` not wired.

- [ ] **Step 3: Create the history DTO**

Create `DprIssueStatusHistoryRow.java`:

```java
package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DprIssueStatusHistory;
import com.bipros.project.domain.model.IssueStatus;

import java.time.Instant;
import java.util.UUID;

/** Read model for one status transition in an issue's history timeline. */
public record DprIssueStatusHistoryRow(
    UUID id,
    IssueStatus fromStatus,
    IssueStatus toStatus,
    UUID actorUserId,
    String reason,
    Instant createdAt
) {
    public static DprIssueStatusHistoryRow from(DprIssueStatusHistory e) {
        return new DprIssueStatusHistoryRow(
            e.getId(), e.getFromStatus(), e.getToStatus(),
            e.getActorUserId(), e.getReason(), e.getCreatedAt());
    }
}
```

- [ ] **Step 4: Wire the repo + guard into the service and write history**

In `DprIssueService.java`:

1. Add imports:

```java
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.project.application.dto.DprIssueStatusHistoryRow;
import com.bipros.project.domain.model.DprIssueStatusHistory;
import com.bipros.project.domain.repository.DprIssueStatusHistoryRepository;
```

2. Add two fields after `private final ApplicationEventPublisher eventPublisher;` (line 45):

```java
    private final DprIssueStatusHistoryRepository historyRepository;
    private final ProjectAccessGuard projectAccessGuard;
```

> `@RequiredArgsConstructor` regenerates the constructor; the test in Step 1 expects the arg order `(issueRepository, historyRepository, auditService, eventPublisher, projectAccessGuard)`. Place the two new fields so the final field order is exactly: `issueRepository, historyRepository, auditService, eventPublisher, projectAccessGuard`. Reorder the existing field declarations to match: move `historyRepository` to right after `issueRepository`, and `projectAccessGuard` to the end.

3. In `patch(...)`, inside the `if (request.status() != null && newStatus != oldStatus)` block (after `issue.setStatus(newStatus);` and the resolvedAt handling, before the closing brace at line 111), append:

```java
            appendStatusHistory(issue.getId(), oldStatus, newStatus,
                    newStatus.resolvedAtTerminal() ? issue.getResolutionNotes() : null);
```

4. In `create(...)`, after `DprIssue saved = issueRepository.save(issue);` (line 160), add:

```java
        appendStatusHistory(saved.getId(), null, saved.getStatus(),
                saved.getStatus().resolvedAtTerminal() ? saved.getResolutionNotes() : null);
```

5. Add the helper + the `history(...)` read method (place near the bottom, before `findIssue`):

```java
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
```

- [ ] **Step 5: Add the history endpoint**

In `DprIssueController.java`, add the import:

```java
import com.bipros.project.application.dto.DprIssueStatusHistoryRow;
```

and a new endpoint after `get(...)` (after line 77):

```java
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<DprIssueStatusHistoryRow>>> history(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.history(projectId, id)));
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && mvn -pl bipros-project test -Dtest=DprIssueServiceTest -q`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/application/dto/DprIssueStatusHistoryRow.java backend/bipros-project/src/main/java/com/bipros/project/application/service/DprIssueService.java backend/bipros-project/src/main/java/com/bipros/project/api/DprIssueController.java backend/bipros-project/src/test/java/com/bipros/project/application/service/DprIssueServiceTest.java
git commit -m "feat(issues): write status-change history and expose history endpoint"
```

---

### Task 4: `assignedToUserId` on create + conditional mandatory validation (BE)

**Files:**
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateDprIssueRequest.java`
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/application/service/DprIssueService.java`
- Test: `backend/bipros-project/src/test/java/com/bipros/project/application/service/DprIssueServiceTest.java` (extend)

**Interfaces:**
- Consumes: `IssueStatus.requiresAssignee()`, `IssueStatus.resolvedAtTerminal()`.
- Produces: `CreateDprIssueRequest` gains trailing fields `UUID supervisorUserId, UUID assignedToUserId`. Service throws `BusinessRuleException("DPR_ISSUE_INVALID", …)` when (a) resulting status `requiresAssignee()` and assignee is null, or (b) resulting status `resolvedAtTerminal()` and resolution notes blank.

- [ ] **Step 1: Write the failing tests**

Add to `DprIssueServiceTest.java`:

```java
    @Test
    void patch_toTerminalWithoutNotes_throws() {
        DprIssue issue = openIssue();
        issue.setAssignedToUserId(assigneeId);
        when(issueRepository.findByIdAndProjectId(issueId, projectId)).thenReturn(Optional.of(issue));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.patch(projectId, issueId, new UpdateDprIssueRequest(
                null, null, null, null, IssueStatus.RESOLVED,
                null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(com.bipros.common.exception.BusinessRuleException.class)
            .hasMessageContaining("Resolution notes");
    }

    @Test
    void patch_toInProgressWithoutAssignee_throws() {
        DprIssue issue = openIssue();   // no assignee
        when(issueRepository.findByIdAndProjectId(issueId, projectId)).thenReturn(Optional.of(issue));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.patch(projectId, issueId, new UpdateDprIssueRequest(
                null, null, null, null, IssueStatus.IN_PROGRESS,
                null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(com.bipros.common.exception.BusinessRuleException.class)
            .hasMessageContaining("Assigned");
    }

    @Test
    void create_openWithoutAssignee_ok() {
        var row = service.create(projectId, new CreateDprIssueRequest(
            "title", null, IssueCategory.OTHER, IssueSeverity.MEDIUM, IssueStatus.OPEN,
            null, null, null, null, null, null, null, null, null));
        assertThat(row.title()).isEqualTo("title");
    }

    @Test
    void create_inProgressWithoutAssignee_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.create(projectId, new CreateDprIssueRequest(
                "title", null, IssueCategory.OTHER, IssueSeverity.MEDIUM, IssueStatus.IN_PROGRESS,
                null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(com.bipros.common.exception.BusinessRuleException.class);
    }
```

> `CreateDprIssueRequest`'s new constructor arg order (after this task) is:
> `title, description, category, severity, status, supervisorResourceId, supervisorName, assignedToResourceId, assignedToName, activityId, activityName, reportDate, supervisorUserId, assignedToUserId`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn -pl bipros-project test -Dtest=DprIssueServiceTest -q`
Expected: FAIL — `CreateDprIssueRequest` has no `supervisorUserId`/`assignedToUserId` args; no validation throws.

- [ ] **Step 3: Add the two fields to `CreateDprIssueRequest`**

Append the two fields to the record (after `LocalDate reportDate` at line 30):

```java
    LocalDate reportDate,
    UUID supervisorUserId,
    UUID assignedToUserId
) {}
```

- [ ] **Step 4: Wire the new fields + validation into the service**

In `DprIssueService.create(...)`:

1. After the `IssueStatus status = ...` line (line 137), add validation:

```java
        validateConditionalFields(status, req.assignedToUserId(), req.description() /*placeholder, replaced below*/);
```

> Replace that placeholder call — `create` validates against the request's own assignee and notes. Use the dedicated form below instead of the placeholder line; do not keep the placeholder:

```java
        validateConditionalFields(status, req.assignedToUserId(), null /* no resolution notes on create */);
```

2. On the builder, set the canonical user-id fields (add after `.assignedToName(...)` at line 150 / before `.reportDate(...)`):

```java
                .supervisorUserId(req.supervisorUserId())
                .assignedToUserId(req.assignedToUserId())
```

In `DprIssueService.patch(...)`, after computing `newStatus` and applying all field setters but BEFORE `issueRepository.save(issue)` (i.e. right after the status-transition block ends at line 111), add:

```java
        validateConditionalFields(newStatus, issue.getAssignedToUserId(), issue.getResolutionNotes());
```

3. Add the shared validator (place near the other private helpers, before `findIssue`):

```java
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && mvn -pl bipros-project test -Dtest=DprIssueServiceTest -q`
Expected: PASS (all tests, including Task 3's history test).

- [ ] **Step 6: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateDprIssueRequest.java backend/bipros-project/src/main/java/com/bipros/project/application/service/DprIssueService.java backend/bipros-project/src/test/java/com/bipros/project/application/service/DprIssueServiceTest.java
git commit -m "feat(issues): persist assignedToUserId on create + conditional mandatory validation"
```

---

### Task 5: Free-text `q` search on the list endpoint (BE)

**Files:**
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/application/service/DprIssueService.java`
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/api/DprIssueController.java`
- Test: `backend/bipros-project/src/test/java/com/bipros/project/application/service/DprIssueServiceTest.java` (extend)

**Interfaces:**
- Produces: `DprIssueService.list(...)` gains a trailing `String q` param; case-insensitive contains on title + description. Controller adds `@RequestParam(required=false) String q`.

- [ ] **Step 1: Write the failing test**

Add to `DprIssueServiceTest.java`:

```java
    @Test
    void list_qFiltersTitleAndDescriptionCaseInsensitive() {
        DprIssue a = openIssue(); a.setTitle("Steel rebar delay"); a.setDescription("customs");
        DprIssue b = openIssue(); b.setTitle("Crane breakdown"); b.setDescription("hydraulic");
        b.setReportDate(java.time.LocalDate.now());
        a.setReportDate(java.time.LocalDate.now());
        when(issueRepository.findByProjectIdOrderByOpenedAtDesc(projectId))
                .thenReturn(java.util.List.of(a, b));

        var byTitle = service.list(projectId, null, null, null, null, null, null, null, "REBAR");
        assertThat(byTitle).hasSize(1);
        assertThat(byTitle.get(0).title()).isEqualTo("Steel rebar delay");

        var byDesc = service.list(projectId, null, null, null, null, null, null, null, "hydraulic");
        assertThat(byDesc).hasSize(1);
        assertThat(byDesc.get(0).title()).isEqualTo("Crane breakdown");

        var noQ = service.list(projectId, null, null, null, null, null, null, null, null);
        assertThat(noQ).hasSize(2);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl bipros-project test -Dtest=DprIssueServiceTest#list_qFiltersTitleAndDescriptionCaseInsensitive -q`
Expected: FAIL — `list(...)` does not accept a 9th `String q` argument.

- [ ] **Step 3: Add the `q` param to the service**

In `DprIssueService.list(...)` (lines 47-68): add `String q` as the final parameter and a filter stage. Replace the method signature line and add the filter:

Signature (line 47-56) — append `, String q)`:

```java
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
```

Add this filter into the stream chain, right after the `dateTo` filter (line 65), before `.map(DprIssueRow::from)`:

```java
                .filter(i -> q == null || q.isBlank() || matchesQ(i, q))
```

Add the helper near `findIssue`:

```java
    private static boolean matchesQ(DprIssue i, String q) {
        String needle = q.toLowerCase();
        boolean inTitle = i.getTitle() != null && i.getTitle().toLowerCase().contains(needle);
        boolean inDesc = i.getDescription() != null && i.getDescription().toLowerCase().contains(needle);
        return inTitle || inDesc;
    }
```

- [ ] **Step 4: Add the `q` param to the controller**

In `DprIssueController.list(...)` (lines 58-70): add the param and pass it through:

Add to the method params (after `dateTo`, line 67):

```java
            @RequestParam(required = false) String q) {
```

(Change the previous line's `LocalDate dateTo)` to `LocalDate dateTo,`.)

Update the service call (line 68-69):

```java
        return ResponseEntity.ok(ApiResponse.ok(service.list(
                projectId, status, severity, category, supervisorUserId, activityId, dateFrom, dateTo, q)));
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && mvn -pl bipros-project test -Dtest=DprIssueServiceTest -q`
Expected: PASS.

- [ ] **Step 6: Build the module so bipros-api picks up new routes**

Run: `cd backend && mvn -pl bipros-project install -q -DskipTests`
Expected: BUILD SUCCESS. (Per project memory: required so a running `bipros-api` doesn't serve stale jars and 404 the new `/history` route + `q` param.)

- [ ] **Step 7: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/application/service/DprIssueService.java backend/bipros-project/src/main/java/com/bipros/project/api/DprIssueController.java backend/bipros-project/src/test/java/com/bipros/project/application/service/DprIssueServiceTest.java
git commit -m "feat(issues): add free-text q search to the list endpoint"
```

---

### Task 6: Frontend API client + types (FE)

**Files:**
- Modify: `frontend/src/lib/types/dpr.ts`
- Modify: `frontend/src/lib/api/dprIssueApi.ts`

**Interfaces:**
- Produces:
  - `CreateDprIssueRequest` (TS) gains `assignedToUserId?: string | null`.
  - `DprIssueFilters` gains `q?: string`; `toQuery` serializes it.
  - `DprIssueStatusHistoryRow` type + `dprIssueApi.history(projectId, id)`.

- [ ] **Step 1: Add `assignedToUserId` to the create type + a history row type**

In `frontend/src/lib/types/dpr.ts`, in the `CreateDprIssueRequest` interface (lines 155-168), add after `assignedToName`:

```typescript
  assignedToUserId?: string | null;
```

After the `CreateDprIssueRequest` interface closes (after line 168), add:

```typescript
/** One status transition in an issue's append-only history timeline. */
export interface DprIssueStatusHistoryRow {
  id: string;
  fromStatus: IssueStatus | null;
  toStatus: IssueStatus;
  actorUserId?: string | null;
  reason?: string | null;
  createdAt: string;
}
```

- [ ] **Step 2: Wire the API client**

In `frontend/src/lib/api/dprIssueApi.ts`:

1. Add `DprIssueStatusHistoryRow` to the type import block (lines 8-14) and the re-export block (lines 16-22):

```typescript
import type {
  CreateDprIssueRequest,
  DprIssueRow,
  DprIssueStatusHistoryRow,
  IssueCategory,
  IssueSeverity,
  IssueStatus,
} from "../types/dpr";

export type {
  CreateDprIssueRequest,
  DprIssueRow,
  DprIssueStatusHistoryRow,
  IssueCategory,
  IssueSeverity,
  IssueStatus,
} from "../types/dpr";
```

2. Add `q` to `DprIssueFilters` (after `dateTo?: string;`, line 31):

```typescript
  q?: string;
```

3. In `toQuery`, add (after the `dateTo` line, line 58):

```typescript
  if (filters.q) params.set("q", filters.q);
```

4. Add the `history` method to the `dprIssueApi` object (after `get`, before `patch`):

```typescript
  history: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<DprIssueStatusHistoryRow[]>>(`/v1/projects/${projectId}/dpr-issues/${id}/history`)
      .then((r) => r.data),
```

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no new errors referencing `dpr.ts` / `dprIssueApi.ts`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/types/dpr.ts frontend/src/lib/api/dprIssueApi.ts
git commit -m "feat(issues): FE types + api for assignedToUserId, q search, status history"
```

---

### Task 7: "On Hold" label + `statusLabel` helper + list/dashboard text (FE)

**Files:**
- Modify: `frontend/src/components/dpr/IssueBadges.tsx`
- Modify: `frontend/src/app/(app)/projects/[projectId]/issues/page.tsx`
- Modify: `frontend/src/app/(app)/projects/[projectId]/issues/dashboard/page.tsx`
- Test: `frontend/src/components/dpr/__tests__/IssueBadges.test.ts` (create)

**Interfaces:**
- Produces: `statusLabel(status: IssueStatus | null | undefined): string` exported from `IssueBadges.tsx`; `STATUS_OPTIONS` entry for `BLOCKED` now labelled `"On Hold"`.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/components/dpr/__tests__/IssueBadges.test.ts`:

```typescript
import { describe, it, expect } from "vitest";
import { statusLabel, STATUS_OPTIONS } from "../IssueBadges";

describe("statusLabel", () => {
  it("renders BLOCKED as 'On Hold'", () => {
    expect(statusLabel("BLOCKED")).toBe("On Hold");
  });
  it("renders IN_PROGRESS as 'In progress'", () => {
    expect(statusLabel("IN_PROGRESS")).toBe("In progress");
  });
  it("falls back to '—' for null", () => {
    expect(statusLabel(null)).toBe("—");
  });
  it("STATUS_OPTIONS has no 'Blocked' label", () => {
    expect(STATUS_OPTIONS.find((o) => o.label === "Blocked")).toBeUndefined();
    expect(STATUS_OPTIONS.find((o) => o.value === "BLOCKED")?.label).toBe("On Hold");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/dpr/__tests__/IssueBadges.test.ts`
Expected: FAIL — `statusLabel` is not exported; label is "Blocked".

- [ ] **Step 3: Update the label + add `statusLabel`**

In `IssueBadges.tsx`, change line 30:

```typescript
  { value: "BLOCKED", label: "On Hold" },
```

After `categoryLabel` (end of file), add:

```typescript
/** Friendly status label used in list rows and read-only summaries. */
export function statusLabel(s: IssueStatus | null | undefined): string {
  if (!s) return "—";
  return STATUS_OPTIONS.find((o) => o.value === s)?.label ?? s;
}
```

- [ ] **Step 4: Use `statusLabel` on the list + fix dashboard tile**

In `issues/page.tsx`:
- Add `statusLabel` to the import from `@/components/dpr/IssueBadges` (lines 14-21).
- Replace line 192 `{row.status.replace("_", " ")}` with:

```typescript
                        {statusLabel(row.status)}
```

In `issues/dashboard/page.tsx`, change line 181 tile label:

```typescript
            <KpiTile label="Open / On Hold" value={totalOpen} tone="warning" />
```

- [ ] **Step 5: Run tests to verify pass + no regressions**

Run: `cd frontend && npx vitest run src/components/dpr/__tests__/IssueBadges.test.ts src/components/dashboards/project/__tests__/dashboardDerivations.test.ts`
Expected: PASS — `dashboardDerivations.test.ts` still passes because the stored value `BLOCKED` is unchanged.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/dpr/IssueBadges.tsx frontend/src/components/dpr/__tests__/IssueBadges.test.ts "frontend/src/app/(app)/projects/[projectId]/issues/page.tsx" "frontend/src/app/(app)/projects/[projectId]/issues/dashboard/page.tsx"
git commit -m "feat(issues): label BLOCKED as 'On Hold' and render friendly status labels"
```

---

### Task 8: Shared assignee-picker hook + label helper (FE)

**Files:**
- Create: `frontend/src/components/dpr/useIssueAssignees.ts`
- Test: `frontend/src/components/dpr/__tests__/useIssueAssignees.test.ts` (create — pure helper only)

**Interfaces:**
- Produces:
  - `memberDisplayName(m: ProjectTeamMember): string` — `"First Last"` || `username` || `userId`.
  - `assigneeOption(m: ProjectTeamMember): { value: string; label: string }` — value = `userId`, label = `"<name> · <RoleLabel>"`.
  - `useIssueAssignees(projectId): { options: SelectOption[]; nameByUserId: Map<string,string>; isLoading: boolean }` — one `projectTeamApi.list` query feeding both the picker and history actor names.

This task isolates the team-fetch + labelling so Tasks 9, 10, 11 reuse it (DRY).

- [ ] **Step 1: Write the failing test (pure helpers)**

Create `frontend/src/components/dpr/__tests__/useIssueAssignees.test.ts`:

```typescript
import { describe, it, expect } from "vitest";
import { memberDisplayName, assigneeOption } from "../useIssueAssignees";
import type { ProjectTeamMember } from "@/lib/api/projectTeamApi";

function m(over: Partial<ProjectTeamMember>): ProjectTeamMember {
  return {
    id: "mem1", projectId: "p1", userId: "u1", role: "ENGINEER",
    reportsToUserId: null, activeFrom: null, activeTo: null,
    createdAt: null, updatedAt: null, ...over,
  };
}

describe("memberDisplayName", () => {
  it("prefers first + last name", () => {
    expect(memberDisplayName(m({ firstName: "Sara", lastName: "Khan" }))).toBe("Sara Khan");
  });
  it("falls back to username", () => {
    expect(memberDisplayName(m({ firstName: null, lastName: null, username: "skhan" }))).toBe("skhan");
  });
  it("falls back to userId", () => {
    expect(memberDisplayName(m({ firstName: null, lastName: null, username: null, userId: "u9" }))).toBe("u9");
  });
});

describe("assigneeOption", () => {
  it("value is userId, label has role", () => {
    const o = assigneeOption(m({ firstName: "Sara", lastName: "Khan", role: "SUPERVISOR" }));
    expect(o.value).toBe("u1");
    expect(o.label).toBe("Sara Khan · Supervisor");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/dpr/__tests__/useIssueAssignees.test.ts`
Expected: FAIL — module does not exist.

- [ ] **Step 3: Implement the hook + helpers**

Create `frontend/src/components/dpr/useIssueAssignees.ts`:

```typescript
import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  projectTeamApi,
  PROJECT_TEAM_ROLE_LABELS,
  type ProjectTeamMember,
} from "@/lib/api/projectTeamApi";
import type { SelectOption } from "@/components/common/SearchableSelect";

/** "First Last" || username || userId. */
export function memberDisplayName(m: ProjectTeamMember): string {
  const full = [m.firstName, m.lastName].filter(Boolean).join(" ").trim();
  return full || m.username || m.userId;
}

/** SearchableSelect option: value = userId, label = "<name> · <RoleLabel>". */
export function assigneeOption(m: ProjectTeamMember): SelectOption {
  return {
    value: m.userId,
    label: `${memberDisplayName(m)} · ${PROJECT_TEAM_ROLE_LABELS[m.role]}`,
  };
}

/**
 * One project-team fetch feeding both the Assigned-To picker (options) and the
 * status-history timeline (nameByUserId). De-dupes members that hold multiple roles
 * by userId, keeping the first occurrence.
 */
export function useIssueAssignees(projectId: string) {
  const { data, isLoading } = useQuery({
    queryKey: ["project-team", projectId],
    queryFn: () => projectTeamApi.list(projectId),
    enabled: !!projectId,
  });

  const members = useMemo(() => data?.data ?? [], [data]);

  const options = useMemo(() => {
    const seen = new Set<string>();
    const out: SelectOption[] = [];
    for (const m of members) {
      if (seen.has(m.userId)) continue;
      seen.add(m.userId);
      out.push(assigneeOption(m));
    }
    return out;
  }, [members]);

  const nameByUserId = useMemo(() => {
    const map = new Map<string, string>();
    for (const m of members) map.set(m.userId, memberDisplayName(m));
    return map;
  }, [members]);

  return { options, nameByUserId, isLoading };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/dpr/__tests__/useIssueAssignees.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/dpr/useIssueAssignees.ts frontend/src/components/dpr/__tests__/useIssueAssignees.test.ts
git commit -m "feat(issues): shared project-team assignee picker hook + label helpers"
```

---

### Task 9: New Issue form — assignee picker + conditional validation + inline errors (FE)

**Files:**
- Modify: `frontend/src/app/(app)/projects/[projectId]/issues/new/page.tsx`

**Interfaces:**
- Consumes: `useIssueAssignees` (Task 8), `assignedToUserId` on `CreateDprIssueRequest` (Task 6), `statusLabel` not needed here.

This is a full-file replacement (the form is restructured: assignee picker, conditional Resolution awareness, per-field inline errors). Replace the entire contents of `new/page.tsx` with:

```tsx
"use client";

import { useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { dprIssueApi } from "@/lib/api/dprIssueApi";
import { activityApi } from "@/lib/api/activityApi";
import type { CreateDprIssueRequest } from "@/lib/types/dpr";
import { PageHeader } from "@/components/common/PageHeader";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { CATEGORY_OPTIONS, SEVERITY_OPTIONS, STATUS_OPTIONS } from "@/components/dpr/IssueBadges";
import { useIssueAssignees } from "@/components/dpr/useIssueAssignees";
import type { IssueCategory, IssueSeverity, IssueStatus } from "@/lib/types/dpr";
import { getErrorMessage } from "@/lib/utils/error";

const inputCls =
  "mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none text-sm";
const errCls = "mt-1 text-xs text-danger";

const today = new Date().toISOString().slice(0, 10);

const ASSIGNEE_REQUIRED: IssueStatus[] = ["IN_PROGRESS", "BLOCKED", "RESOLVED", "CLOSED"];

export default function NewProjectIssuePage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const queryClient = useQueryClient();

  const [state, setState] = useState<CreateDprIssueRequest>({
    title: "",
    category: "OTHER",
    severity: "MEDIUM",
    status: "OPEN",
    reportDate: today,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const { options: assigneeOptions, isLoading: assigneesLoading } = useIssueAssignees(projectId);

  const { data: activitiesData, isLoading: activitiesLoading } = useQuery({
    queryKey: ["activities", projectId, "all"],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: !!projectId,
  });

  const activityOptions = useMemo(
    () =>
      (activitiesData?.data?.content ?? []).map((a) => ({
        value: a.id,
        label: `${a.code} — ${a.name}`,
      })),
    [activitiesData]
  );

  const set = <K extends keyof CreateDprIssueRequest>(k: K, v: CreateDprIssueRequest[K]) =>
    setState((s) => ({ ...s, [k]: v }));

  const handleActivityChange = (activityId: string) => {
    if (!activityId) {
      setState((s) => ({ ...s, activityId: null, activityName: null }));
      return;
    }
    const activity = (activitiesData?.data?.content ?? []).find((a) => a.id === activityId);
    setState((s) => ({ ...s, activityId, activityName: activity?.name ?? null }));
  };

  const handleAssigneeChange = (userId: string) => {
    if (!userId) {
      setState((s) => ({ ...s, assignedToUserId: null, assignedToName: null }));
      return;
    }
    const label = assigneeOptions.find((o) => o.value === userId)?.label ?? null;
    setState((s) => ({ ...s, assignedToUserId: userId, assignedToName: label }));
  };

  const mutation = useMutation({
    mutationFn: (body: CreateDprIssueRequest) => dprIssueApi.create(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["dpr-issues", projectId] });
      router.push(`/projects/${projectId}/issues`);
    },
    onError: (err) => setFormError(getErrorMessage(err)),
  });

  const validate = (): boolean => {
    const next: Record<string, string> = {};
    if (!state.title.trim()) next.title = "Title is required.";
    const status = state.status ?? "OPEN";
    if (ASSIGNEE_REQUIRED.includes(status) && !state.assignedToUserId) {
      next.assignedTo = "Assigned To is required for this status.";
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    if (!validate()) return;
    mutation.mutate(state);
  };

  const status = state.status ?? "OPEN";
  const assigneeRequired = ASSIGNEE_REQUIRED.includes(status);

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader title="New Issue" description="Log a field issue directly against this project." />

      <form onSubmit={handleSubmit} className="space-y-5 rounded-lg border border-border bg-surface p-6">
        {formError && (
          <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {formError}
          </div>
        )}

        <div>
          <label className="block text-sm font-medium text-text-secondary">Title *</label>
          <input
            type="text"
            maxLength={150}
            value={state.title}
            onChange={(e) => set("title", e.target.value)}
            placeholder="Brief summary of the issue"
            className={inputCls}
          />
          {errors.title && <p className={errCls}>{errors.title}</p>}
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Category *</label>
            <SearchableSelect
              options={CATEGORY_OPTIONS}
              value={state.category ?? ""}
              onChange={(v) => set("category", v as IssueCategory)}
              placeholder="Select category"
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Severity *</label>
            <SearchableSelect
              options={SEVERITY_OPTIONS}
              value={state.severity ?? ""}
              onChange={(v) => set("severity", v as IssueSeverity)}
              placeholder="Select severity"
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Status *</label>
            <SearchableSelect
              options={STATUS_OPTIONS}
              value={state.status ?? "OPEN"}
              onChange={(v) => set("status", v as IssueStatus)}
              placeholder="Select status"
              className="mt-1"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">Description</label>
          <textarea
            maxLength={2000}
            value={state.description ?? ""}
            onChange={(e) => set("description", e.target.value || null)}
            rows={3}
            placeholder="Detailed description of the issue…"
            className={inputCls}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Activity (optional)</label>
            <SearchableSelect
              options={activityOptions}
              value={state.activityId ?? ""}
              onChange={handleActivityChange}
              placeholder="Search activities…"
              loading={activitiesLoading}
              selectedLabel={
                state.activityId
                  ? activityOptions.find((o) => o.value === state.activityId)?.label
                  : undefined
              }
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Report Date</label>
            <input
              type="date"
              value={state.reportDate ?? today}
              onChange={(e) => set("reportDate", e.target.value)}
              className={inputCls}
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">
            Assigned To{assigneeRequired ? " *" : ""}
          </label>
          <SearchableSelect
            options={assigneeOptions}
            value={state.assignedToUserId ?? ""}
            onChange={handleAssigneeChange}
            placeholder="Select a project team member…"
            loading={assigneesLoading}
            selectedLabel={state.assignedToName ?? undefined}
            className="mt-1"
          />
          {errors.assignedTo && <p className={errCls}>{errors.assignedTo}</p>}
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => router.push(`/projects/${projectId}/issues`)}
            className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
          >
            {mutation.isPending ? "Saving…" : "Log Issue"}
          </button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 1: Typecheck + lint the new form**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors in `new/page.tsx`.

- [ ] **Step 2: Manual verification checklist (record results)**

Start the app (`docker compose up -d`; backend `mvn -pl bipros-api spring-boot:run` after the Task 5 install; `cd frontend && node node_modules/next/dist/bin/next dev` per the corepack memory). Then:
- Assigned To shows project team members; selecting one persists after save (check list column + reopen edit).
- Set Status = In progress without an assignee → inline "Assigned To is required for this status." blocks submit.
- Leave Title empty → inline "Title is required.".

- [ ] **Step 3: Commit**

```bash
git add "frontend/src/app/(app)/projects/[projectId]/issues/new/page.tsx"
git commit -m "feat(issues): assignee picker + conditional validation on the new-issue form"
```

---

### Task 10: Edit Issue form — sections, header, assignee picker, conditional resolution, history timeline (FE)

**Files:**
- Modify: `frontend/src/app/(app)/projects/[projectId]/issues/[issueId]/edit/page.tsx`

**Interfaces:**
- Consumes: `useIssueAssignees` (Task 8), `dprIssueApi.history` (Task 6), `statusLabel` (Task 7), `assignedToUserId` on `UpdateDprIssueRequest` (already exists).

Full-file replacement. Replace the entire contents of `edit/page.tsx` with:

```tsx
"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { dprIssueApi, type UpdateDprIssueRequest } from "@/lib/api/dprIssueApi";
import { activityApi } from "@/lib/api/activityApi";
import { PageHeader } from "@/components/common/PageHeader";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import {
  CATEGORY_OPTIONS,
  SEVERITY_OPTIONS,
  STATUS_OPTIONS,
  statusLabel,
} from "@/components/dpr/IssueBadges";
import { useIssueAssignees } from "@/components/dpr/useIssueAssignees";
import { getErrorMessage } from "@/lib/utils/error";
import type { IssueCategory, IssueSeverity, IssueStatus } from "@/lib/types/dpr";

const inputCls =
  "mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none text-sm";
const errCls = "mt-1 text-xs text-danger";

const ASSIGNEE_REQUIRED: IssueStatus[] = ["IN_PROGRESS", "BLOCKED", "RESOLVED", "CLOSED"];
const TERMINAL: IssueStatus[] = ["RESOLVED", "CLOSED"];

interface FormState {
  title: string;
  description: string;
  category: IssueCategory;
  severity: IssueSeverity;
  status: IssueStatus;
  activityId: string;
  activityName: string;
  assignedToUserId: string;
  assignedToName: string;
  resolutionNotes: string;
}

function fmtDate(iso?: string | null): string {
  return iso ? new Date(iso).toLocaleDateString() : "—";
}

export default function EditIssuePage() {
  const params = useParams<{ projectId: string; issueId: string }>();
  const { projectId, issueId } = params;
  const router = useRouter();
  const queryClient = useQueryClient();

  const [form, setForm] = useState<FormState | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const { data: issueData, isLoading: issueLoading } = useQuery({
    queryKey: ["dpr-issue", projectId, issueId],
    queryFn: () => dprIssueApi.get(projectId, issueId),
    enabled: !!projectId && !!issueId,
  });

  const { data: historyData } = useQuery({
    queryKey: ["dpr-issue-history", projectId, issueId],
    queryFn: () => dprIssueApi.history(projectId, issueId),
    enabled: !!projectId && !!issueId,
  });

  const { options: assigneeOptions, nameByUserId, isLoading: assigneesLoading } =
    useIssueAssignees(projectId);

  const { data: activitiesData, isLoading: activitiesLoading } = useQuery({
    queryKey: ["activities", projectId, "all"],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: !!projectId,
  });

  const activityOptions = useMemo(
    () =>
      (activitiesData?.data?.content ?? []).map((a) => ({
        value: a.id,
        label: `${a.code} — ${a.name}`,
      })),
    [activitiesData]
  );

  useEffect(() => {
    const issue = issueData?.data;
    if (!issue || form) return;
    setForm({
      title: issue.title,
      description: issue.description ?? "",
      category: issue.category,
      severity: issue.severity,
      status: issue.status,
      activityId: issue.activityId ?? "",
      activityName: issue.activityName ?? "",
      assignedToUserId: issue.assignedToUserId ?? "",
      assignedToName: issue.assignedToName ?? "",
      resolutionNotes: issue.resolutionNotes ?? "",
    });
  }, [issueData, form]);

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) =>
    setForm((s) => (s ? { ...s, [k]: v } : s));

  const handleActivityChange = (activityId: string) => {
    if (!activityId) {
      setForm((s) => (s ? { ...s, activityId: "", activityName: "" } : s));
      return;
    }
    const activity = (activitiesData?.data?.content ?? []).find((a) => a.id === activityId);
    setForm((s) => (s ? { ...s, activityId, activityName: activity?.name ?? "" } : s));
  };

  const handleAssigneeChange = (userId: string) => {
    if (!userId) {
      setForm((s) => (s ? { ...s, assignedToUserId: "", assignedToName: "" } : s));
      return;
    }
    const label = assigneeOptions.find((o) => o.value === userId)?.label ?? "";
    setForm((s) => (s ? { ...s, assignedToUserId: userId, assignedToName: label } : s));
  };

  const mutation = useMutation({
    mutationFn: (body: UpdateDprIssueRequest) => dprIssueApi.patch(projectId, issueId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["dpr-issues", projectId] });
      queryClient.invalidateQueries({ queryKey: ["dpr-issue", projectId, issueId] });
      router.push(`/projects/${projectId}/issues`);
    },
    onError: (err) => setFormError(getErrorMessage(err)),
  });

  const validate = (f: FormState): boolean => {
    const next: Record<string, string> = {};
    if (!f.title.trim()) next.title = "Title is required.";
    if (ASSIGNEE_REQUIRED.includes(f.status) && !f.assignedToUserId) {
      next.assignedTo = "Assigned To is required for this status.";
    }
    if (TERMINAL.includes(f.status) && !f.resolutionNotes.trim()) {
      next.resolutionNotes = "Resolution notes are required to resolve or close.";
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form) return;
    setFormError(null);
    if (!validate(form)) return;
    const body: UpdateDprIssueRequest = {
      title: form.title,
      description: form.description || null,
      category: form.category,
      severity: form.severity,
      status: form.status,
      assignedToUserId: form.assignedToUserId || null,
      assignedToName: form.assignedToName || null,
      resolutionNotes: form.resolutionNotes || null,
      activityId: form.activityId || null,
      activityName: form.activityName || null,
    };
    mutation.mutate(body);
  };

  if (issueLoading || !form) {
    return <div className="p-6 text-sm text-text-muted">Loading…</div>;
  }

  const issue = issueData?.data;
  const showResolution = TERMINAL.includes(form.status);
  const assigneeRequired = ASSIGNEE_REQUIRED.includes(form.status);
  const history = historyData?.data ?? [];

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader title="Edit Issue" description="Update the details of this issue." />

      {/* Read-only context strip */}
      <div className="grid grid-cols-2 gap-3 rounded-lg border border-border bg-surface-hover px-4 py-3 text-sm sm:grid-cols-4">
        <div>
          <div className="text-xs uppercase tracking-wide text-text-muted">Logged by</div>
          <div className="text-text-primary">{issue?.supervisorName ?? "—"}</div>
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-text-muted">Report date</div>
          <div className="text-text-primary">{fmtDate(issue?.reportDate)}</div>
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-text-muted">Opened</div>
          <div className="text-text-primary">{fmtDate(issue?.openedAt)}</div>
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-text-muted">Resolved</div>
          <div className="text-text-primary">{fmtDate(issue?.resolvedAt)}</div>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-5 rounded-lg border border-border bg-surface p-6">
        {formError && (
          <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {formError}
          </div>
        )}

        <div>
          <label className="block text-sm font-medium text-text-secondary">Title *</label>
          <input
            type="text"
            maxLength={150}
            value={form.title}
            onChange={(e) => set("title", e.target.value)}
            className={inputCls}
          />
          {errors.title && <p className={errCls}>{errors.title}</p>}
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Category *</label>
            <SearchableSelect
              options={CATEGORY_OPTIONS}
              value={form.category}
              onChange={(v) => set("category", v as IssueCategory)}
              placeholder="Select category"
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Severity *</label>
            <SearchableSelect
              options={SEVERITY_OPTIONS}
              value={form.severity}
              onChange={(v) => set("severity", v as IssueSeverity)}
              placeholder="Select severity"
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Status *</label>
            <SearchableSelect
              options={STATUS_OPTIONS}
              value={form.status}
              onChange={(v) => set("status", v as IssueStatus)}
              placeholder="Select status"
              className="mt-1"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">Description</label>
          <textarea
            maxLength={2000}
            value={form.description}
            onChange={(e) => set("description", e.target.value)}
            rows={3}
            className={inputCls}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Activity</label>
            <SearchableSelect
              options={activityOptions}
              value={form.activityId}
              onChange={handleActivityChange}
              placeholder="Search activities…"
              loading={activitiesLoading}
              selectedLabel={
                form.activityId
                  ? activityOptions.find((o) => o.value === form.activityId)?.label ?? form.activityName
                  : undefined
              }
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">
              Assigned To{assigneeRequired ? " *" : ""}
            </label>
            <SearchableSelect
              options={assigneeOptions}
              value={form.assignedToUserId}
              onChange={handleAssigneeChange}
              placeholder="Select a project team member…"
              loading={assigneesLoading}
              selectedLabel={form.assignedToName || undefined}
              className="mt-1"
            />
            {errors.assignedTo && <p className={errCls}>{errors.assignedTo}</p>}
          </div>
        </div>

        {showResolution && (
          <div>
            <label className="block text-sm font-medium text-text-secondary">Resolution Notes *</label>
            <textarea
              maxLength={1000}
              value={form.resolutionNotes}
              onChange={(e) => set("resolutionNotes", e.target.value)}
              rows={2}
              placeholder="How was this issue resolved?"
              className={inputCls}
            />
            {errors.resolutionNotes && <p className={errCls}>{errors.resolutionNotes}</p>}
          </div>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => router.push(`/projects/${projectId}/issues`)}
            className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
          >
            {mutation.isPending ? "Saving…" : "Save Changes"}
          </button>
        </div>
      </form>

      {/* Status history timeline */}
      <div className="rounded-lg border border-border bg-surface p-6">
        <h2 className="text-sm font-semibold text-text-primary">Status history</h2>
        {history.length === 0 ? (
          <p className="mt-2 text-sm text-text-muted">No status changes recorded yet.</p>
        ) : (
          <ol className="mt-3 space-y-3">
            {history.map((h) => (
              <li key={h.id} className="flex gap-3 text-sm">
                <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-accent" />
                <div>
                  <div className="text-text-primary">
                    {h.fromStatus ? `${statusLabel(h.fromStatus)} → ` : "Created as "}
                    <span className="font-medium">{statusLabel(h.toStatus)}</span>
                  </div>
                  <div className="text-xs text-text-muted">
                    {(h.actorUserId && nameByUserId.get(h.actorUserId)) || "System"} ·{" "}
                    {new Date(h.createdAt).toLocaleString()}
                  </div>
                  {h.reason && <div className="mt-0.5 text-text-secondary">{h.reason}</div>}
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 1: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors in `edit/page.tsx`.

- [ ] **Step 2: Manual verification checklist (record results)**

- Open an OPEN issue → Resolution Notes field is hidden.
- Change Status to Resolved → Resolution Notes appears with a `*`; submitting empty shows inline error; BE also rejects if bypassed.
- Header strip shows Logged by / Report date / Opened / Resolved.
- After a status change + save, reopen → "Status history" lists the transition with actor name + timestamp; terminal transitions show the resolution note as the reason.
- "On Hold" appears in the status dropdown and history (never "Blocked").

- [ ] **Step 3: Commit**

```bash
git add "frontend/src/app/(app)/projects/[projectId]/issues/[issueId]/edit/page.tsx"
git commit -m "feat(issues): restructure edit form with header, picker, conditional resolution, history timeline"
```

---

### Task 11: Issues list — text search, q wiring, date column = report date, terminal-status routing (FE)

**Files:**
- Modify: `frontend/src/app/(app)/projects/[projectId]/issues/page.tsx`

**Interfaces:**
- Consumes: `DprIssueFilters.q` + `toQuery` (Task 6), `statusLabel` (Task 7, already imported in Task 7).

- [ ] **Step 1: Add a debounced search box wired to `filters.q`**

In `issues/page.tsx`:

1. Ensure `useEffect` is imported (line 3): change `import { useState } from "react";` to:

```tsx
import { useState, useEffect } from "react";
```

2. Add local search state below the existing `useState` hooks (after line 35 `const [statusMenu, setStatusMenu] = useState<string | null>(null);`):

```tsx
  const [searchInput, setSearchInput] = useState("");

  useEffect(() => {
    const t = setTimeout(() => {
      setFilters((f) => ({ ...f, q: searchInput.trim() || undefined }));
    }, 300);
    return () => clearTimeout(t);
  }, [searchInput]);
```

3. In the filter bar (after the opening `<div className="flex flex-wrap items-center gap-2">` at line 95, as the first control), add the search input:

```tsx
        <input
          type="text"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Search title or description…"
          className={`${inputCls} w-64`}
        />
```

4. Update `clearFilters` (line 67) to also clear the search box:

```tsx
  const clearFilters = () => {
    setFilters({});
    setSearchInput("");
  };
```

- [ ] **Step 2: Show report date in the Date column (align with the date filter)**

Replace the Date cell (lines 217-221) — currently `row.openedAt` — with `row.reportDate`:

```tsx
                  <td className="px-4 py-3 text-text-muted whitespace-nowrap">
                    {row.reportDate
                      ? new Date(row.reportDate).toLocaleDateString()
                      : "—"}
                  </td>
```

- [ ] **Step 3: Route terminal-status picks from the inline dropdown to the edit page**

The inline status dropdown (lines 197-210) must not patch directly to RESOLVED/CLOSED — those need resolution notes (BE rejects). Replace the inner `STATUS_OPTIONS.map(...)` button handler so terminal picks navigate to edit instead:

```tsx
                        {STATUS_OPTIONS.map((opt) => (
                          <button
                            key={opt.value}
                            onClick={() => {
                              setStatusMenu(null);
                              if (opt.value === "RESOLVED" || opt.value === "CLOSED") {
                                router.push(`/projects/${projectId}/issues/${row.id}/edit`);
                                return;
                              }
                              patchMutation.mutate({ id: row.id!, body: { status: opt.value } });
                            }}
                            className="block w-full px-3 py-2 text-left text-sm hover:bg-surface-hover text-text-primary"
                          >
                            {opt.label}
                          </button>
                        ))}
```

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors in `issues/page.tsx`.

- [ ] **Step 5: Manual verification checklist (record results)**

- Typing in the search box filters rows (debounced ~300ms); clearing shows all; "Clear" resets it.
- Status column shows "On Hold" / "In progress" (friendly labels, from Task 7).
- Date column matches the date-range filter (both report date now).
- Inline dropdown: picking In progress / On Hold patches in place; picking Resolved / Closed opens the edit page (so notes can be entered).

- [ ] **Step 6: Commit**

```bash
git add "frontend/src/app/(app)/projects/[projectId]/issues/page.tsx"
git commit -m "feat(issues): list search box, report-date column, terminal-status routes to edit"
```

---

### Task 12: Full-suite verification (BE + FE)

**Files:** none (verification only).

- [ ] **Step 1: Backend — module test suite**

Run: `cd backend && mvn -pl bipros-project test -q`
Expected: BUILD SUCCESS — `IssueStatusTest`, `DprIssueServiceTest`, and the pre-existing `DailyProgressReportServiceIssuesTest` all pass.

- [ ] **Step 2: Backend — install so a running API serves the new routes**

Run: `cd backend && mvn -pl bipros-project install -q -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Frontend — vitest suite**

Run: `cd frontend && npx vitest run`
Expected: all tests pass, including `IssueBadges.test.ts`, `useIssueAssignees.test.ts`, and the unchanged `dashboardDerivations.test.ts`.

- [ ] **Step 4: Frontend — typecheck + lint**

Run: `cd frontend && npx tsc --noEmit && node node_modules/next/dist/bin/next lint` (or `npx eslint src/app/\(app\)/projects/\[projectId\]/issues src/components/dpr`)
Expected: no errors.

- [ ] **Step 5: End-to-end smoke (manual, record results)**

With the stack running, walk the 8 requirements against the Khasab demo project:
1. Status dropdown + badges show "On Hold" (never "Blocked"); stored value still `BLOCKED`.
2. Assigned To picker lists project team members; selection persists and shows in the list column.
3. Resolution Notes hidden for non-terminal statuses; shown + required for Resolved/Closed.
4. Edit page shows a Status history timeline after status changes (actor + timestamp + reason).
5. Mandatory rules enforced (Status always; Assigned To for working/terminal; Resolution Notes for terminal) on both FE inline errors and BE rejection.
6. Edit form reorganised (header strip + sections + history).
7. List / create / edit / delete / inline status / filters all functional.
8. Search box filters by title/description; dropdown + date filters return correct rows; Date column aligns with the date filter.

- [ ] **Step 6: Commit any verification fixes** (only if Steps 1-5 surfaced issues).

---

## Self-Review

**Spec coverage:** all 8 spec items map to tasks — #1 On-Hold label → Task 7; #2 assignee picker → Tasks 4 (BE create), 6 (types/api), 8 (hook), 9/10 (forms); #3 resolution-notes confusion → Tasks 4 (BE), 10 (FE conditional render); #4 history → Tasks 2, 3 (BE), 6 (api), 10 (timeline); #5 mandatory → Tasks 1, 4 (BE), 9, 10 (FE); #6 UI/UX → Tasks 9, 10; #7 functionality verification → Task 12; #8 search/filter → Tasks 5 (BE q), 6 (api), 11 (FE box + date-column fix). Decisions taken without asking (history scope = status-only, date-column→reportDate, no supervisor/activity UI) are reflected and bounded.

**Placeholder scan:** one intentional placeholder call is shown in Task 4 Step 4 with an explicit "replace this" instruction immediately following — flagged, not left dangling. No other TBD/TODO.

**Type consistency:** BE `DprIssueService` constructor field order `(issueRepository, historyRepository, auditService, eventPublisher, projectAccessGuard)` matches the test's `new DprIssueService(...)` in Task 3. `CreateDprIssueRequest` arg order documented in Task 4 matches the calls in Task 4's tests. `UpdateDprIssueRequest` 14-arg order documented in Task 3 matches all test constructions. FE `assigneeOption`/`memberDisplayName`/`useIssueAssignees`/`statusLabel`/`dprIssueApi.history`/`DprIssueStatusHistoryRow` names are consistent across Tasks 6–11.
