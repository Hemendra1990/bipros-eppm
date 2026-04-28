# Daily-Deployment Matrix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproduce the Excel "Daily Deployment" sheet — three stacked 9×31 matrices of equipment hours per day per month: Worked, Idle, Planned. Adds a new `DailyResourcePlan` entity for the planned-hours block, a new matrix-shape report endpoint, and a frontend page rendering the three matrices.

**Architecture:** A new entity `project.daily_resource_plans` for forward-looking planned hours (symmetric with the existing `daily_resource_deployments` for actuals). A new service `DailyDeploymentMatrixReportService` runs three native queries (one per matrix), each grouping by (resource-key, day-of-month). A new endpoint `GET /v1/reports/daily-deployment-matrix` returns the three matrices in one response. Two new frontend pages: a matrix viewer and a bulk-paste form for resource plans.

**Tech Stack:** Java 23 / Spring Boot 3.5, JPA + Liquibase, native SQL through shared `EntityManager`, JUnit 5, Spring MVC test, Next.js 16 / React 19 / TanStack Query.

**Spec:** `docs/superpowers/specs/2026-04-27-capacity-utilization-reports-design.md` (sections 2.4, 4).

**Out of scope (covered by separate plans):** Capacity Utilization extension (Plan 1, already merged); DPR matrix (Plan 3); Oman seed scripts (Plan 4); enriching the existing `DailyResourceDeployment` (worked + idle hours already exist there).

**Depends on:** Plan 1 must be merged first (this plan reuses the worktree pattern but doesn't share files with Plan 1).

---

## File-touch summary

**Backend (Java) — created:**
- `backend/bipros-api/src/main/resources/db/changelog/047-daily-resource-plan.yaml`
- `backend/bipros-project/src/main/java/com/bipros/project/domain/model/DailyResourcePlan.java`
- `backend/bipros-project/src/main/java/com/bipros/project/domain/repository/DailyResourcePlanRepository.java`
- `backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateDailyResourcePlanRequest.java`
- `backend/bipros-project/src/main/java/com/bipros/project/application/dto/DailyResourcePlanResponse.java`
- `backend/bipros-project/src/main/java/com/bipros/project/application/service/DailyResourcePlanService.java`
- `backend/bipros-project/src/main/java/com/bipros/project/api/DailyResourcePlanController.java`
- `backend/bipros-project/src/test/java/com/bipros/project/application/service/DailyResourcePlanServiceTest.java`
- `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/dto/DailyDeploymentMatrixReport.java`
- `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/service/DailyDeploymentMatrixReportService.java`
- `backend/bipros-api/src/test/java/com/bipros/api/integration/DailyDeploymentMatrixIntegrationTest.java`

**Backend (Java) — modified:**
- `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml` — register changeset 047
- `backend/bipros-reporting/src/main/java/com/bipros/reporting/presentation/controller/ReportController.java` — wire new endpoint

**Frontend (TypeScript) — created:**
- `frontend/src/lib/api/dailyDeploymentMatrixApi.ts`
- `frontend/src/lib/api/dailyResourcePlanApi.ts`
- `frontend/src/app/(app)/projects/[projectId]/daily-deployment-matrix/page.tsx`
- `frontend/src/app/(app)/projects/[projectId]/resource-plans/page.tsx`
- `frontend/e2e/tests/22-daily-deployment-matrix.spec.ts`

**Frontend — modified:**
- `frontend/src/app/(app)/projects/[projectId]/layout.tsx` — add 2 new tabs

---

## Task 1: Liquibase changeset 047 — `daily_resource_plans` table

**Files:**
- Create: `backend/bipros-api/src/main/resources/db/changelog/047-daily-resource-plan.yaml`
- Modify: `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Write the changeset**

```yaml
databaseChangeLog:
  - changeSet:
      id: 047-create-daily-resource-plans
      author: bipros
      changes:
        - createTable:
            schemaName: project
            tableName: daily_resource_plans
            columns:
              - column: { name: id,                    type: UUID,           constraints: { primaryKey: true, nullable: false } }
              - column: { name: project_id,            type: UUID,           constraints: { nullable: false } }
              - column: { name: plan_date,             type: DATE,           constraints: { nullable: false } }
              - column: { name: resource_type_def_id,  type: UUID }
              - column: { name: resource_id,           type: UUID }
              - column: { name: planned_hours,         type: DOUBLE,         constraints: { nullable: false } }
              - column: { name: remarks,               type: VARCHAR(500) }
              - column: { name: created_at,            type: TIMESTAMP,      constraints: { nullable: false } }
              - column: { name: updated_at,            type: TIMESTAMP,      constraints: { nullable: false } }
              - column: { name: created_by,            type: VARCHAR(255) }
              - column: { name: updated_by,            type: VARCHAR(255) }
              - column: { name: version,               type: BIGINT }
        - sql:
            dbms: postgresql
            sql: |
              ALTER TABLE project.daily_resource_plans
                ADD CONSTRAINT chk_drp_one_resource_dim
                CHECK ( (resource_type_def_id IS NOT NULL)::int + (resource_id IS NOT NULL)::int = 1 );
        - addUniqueConstraint:
            schemaName: project
            tableName: daily_resource_plans
            constraintName: uk_drp_unique
            columnNames: project_id, plan_date, resource_type_def_id, resource_id
        - createIndex:
            schemaName: project
            tableName: daily_resource_plans
            indexName: idx_drp_project_date
            columns:
              - column: { name: project_id }
              - column: { name: plan_date }
```

- [ ] **Step 2: Register in master**

Append to `db.changelog-master.yaml`:
```yaml
  - include:
      file: db/changelog/047-daily-resource-plan.yaml
```

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-api/src/main/resources/db/changelog/047-daily-resource-plan.yaml backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat(project): add daily_resource_plans table for forward-looking planned hours"
```

---

## Task 2: `DailyResourcePlan` entity

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/domain/model/DailyResourcePlan.java`

- [ ] **Step 1: Write the entity**

```java
package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Forward-looking planned hours per (project × date × resource-or-type). The "Planned" block
 * of the Excel "Daily Deployment" sheet (sub-table 3) is fed by this entity, while the "Worked"
 * and "Idle" blocks come from {@link DailyResourceDeployment}.
 *
 * <p>Exactly one of {@code resourceTypeDefId} (type-level plan, applies to any resource of the
 * type) or {@code resourceId} (specific resource) must be set; the DB CHECK constraint rejects
 * the other cases. Soft FKs (plain UUIDs) per the no-cross-module-deps rule.
 */
@Entity
@Table(
    name = "daily_resource_plans",
    schema = "project",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_drp_unique",
            columnNames = {"project_id", "plan_date", "resource_type_def_id", "resource_id"})
    },
    indexes = {
        @Index(name = "idx_drp_project_date", columnList = "project_id, plan_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyResourcePlan extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "plan_date", nullable = false)
  private LocalDate planDate;

  /** Soft FK to {@code resource.resource_type_defs.id}. Nullable. Mutually exclusive with {@link #resourceId}. */
  @Column(name = "resource_type_def_id")
  private UUID resourceTypeDefId;

  /** Soft FK to {@code resource.resources.id}. Nullable. Mutually exclusive with {@link #resourceTypeDefId}. */
  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "planned_hours", nullable = false)
  private Double plannedHours;

  @Column(name = "remarks", length = 500)
  private String remarks;
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-project -am compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/domain/model/DailyResourcePlan.java
git commit -m "feat(project): add DailyResourcePlan entity (planned hours per resource per day)"
```

---

## Task 3: `DailyResourcePlanRepository`

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/domain/repository/DailyResourcePlanRepository.java`

- [ ] **Step 1: Write the repository**

```java
package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DailyResourcePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyResourcePlanRepository extends JpaRepository<DailyResourcePlan, UUID> {

  List<DailyResourcePlan> findByProjectIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
      UUID projectId, LocalDate from, LocalDate to);

  List<DailyResourcePlan> findByProjectIdOrderByPlanDateAscIdAsc(UUID projectId);

  Optional<DailyResourcePlan> findFirstByProjectIdAndPlanDateAndResourceTypeDefIdAndResourceId(
      UUID projectId, LocalDate planDate, UUID resourceTypeDefId, UUID resourceId);
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-project -am compile -q
```

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/domain/repository/DailyResourcePlanRepository.java
git commit -m "feat(project): add DailyResourcePlanRepository"
```

---

## Task 4: Request and response DTOs

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateDailyResourcePlanRequest.java`
- Create: `backend/bipros-project/src/main/java/com/bipros/project/application/dto/DailyResourcePlanResponse.java`

- [ ] **Step 1: Write the request DTO**

```java
package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateDailyResourcePlanRequest(
    @NotNull(message = "planDate is required") LocalDate planDate,
    /** Type-level plan: applies to any resource of this type. Mutually exclusive with resourceId. */
    UUID resourceTypeDefId,
    /** Specific-resource plan. Mutually exclusive with resourceTypeDefId. */
    UUID resourceId,
    @NotNull(message = "plannedHours is required")
    @PositiveOrZero(message = "plannedHours must be >= 0") Double plannedHours,
    @Size(max = 500) String remarks
) {}
```

- [ ] **Step 2: Write the response DTO**

```java
package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DailyResourcePlan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyResourcePlanResponse(
    UUID id, UUID projectId, LocalDate planDate,
    UUID resourceTypeDefId, UUID resourceId,
    Double plannedHours, String remarks,
    Instant createdAt, Instant updatedAt, String createdBy, String updatedBy
) {
  public static DailyResourcePlanResponse from(DailyResourcePlan p) {
    return new DailyResourcePlanResponse(
        p.getId(), p.getProjectId(), p.getPlanDate(),
        p.getResourceTypeDefId(), p.getResourceId(),
        p.getPlannedHours(), p.getRemarks(),
        p.getCreatedAt(), p.getUpdatedAt(), p.getCreatedBy(), p.getUpdatedBy());
  }
}
```

- [ ] **Step 3: Compile + commit**

```bash
cd backend && mvn -pl bipros-project -am compile -q
git add backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateDailyResourcePlanRequest.java backend/bipros-project/src/main/java/com/bipros/project/application/dto/DailyResourcePlanResponse.java
git commit -m "feat(project): add DailyResourcePlan request + response DTOs"
```

---

## Task 5: `DailyResourcePlanService`

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/application/service/DailyResourcePlanService.java`

- [ ] **Step 1: Write the service**

```java
package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDailyResourcePlanRequest;
import com.bipros.project.application.dto.DailyResourcePlanResponse;
import com.bipros.project.domain.model.DailyResourcePlan;
import com.bipros.project.domain.repository.DailyResourcePlanRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DailyResourcePlanService {

  private final DailyResourcePlanRepository repository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

  public DailyResourcePlanResponse create(UUID projectId, CreateDailyResourcePlanRequest request) {
    ensureProjectExists(projectId);
    enforceMutuallyExclusive(request);
    rejectDuplicate(projectId, request.planDate(), request.resourceTypeDefId(), request.resourceId(), null);

    DailyResourcePlan row = DailyResourcePlan.builder()
        .projectId(projectId)
        .planDate(request.planDate())
        .resourceTypeDefId(request.resourceTypeDefId())
        .resourceId(request.resourceId())
        .plannedHours(request.plannedHours())
        .remarks(request.remarks())
        .build();

    DailyResourcePlan saved = repository.save(row);
    auditService.logCreate("DailyResourcePlan", saved.getId(), DailyResourcePlanResponse.from(saved));
    return DailyResourcePlanResponse.from(saved);
  }

  public List<DailyResourcePlanResponse> createBulk(UUID projectId, List<CreateDailyResourcePlanRequest> requests) {
    return requests.stream().map(r -> create(projectId, r)).toList();
  }

  @Transactional(readOnly = true)
  public List<DailyResourcePlanResponse> list(UUID projectId, LocalDate from, LocalDate to) {
    ensureProjectExists(projectId);
    List<DailyResourcePlan> rows = (from != null && to != null)
        ? repository.findByProjectIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(projectId, from, to)
        : repository.findByProjectIdOrderByPlanDateAscIdAsc(projectId);
    return rows.stream().map(DailyResourcePlanResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public DailyResourcePlanResponse get(UUID projectId, UUID id) {
    return DailyResourcePlanResponse.from(find(projectId, id));
  }

  public void delete(UUID projectId, UUID id) {
    DailyResourcePlan row = find(projectId, id);
    repository.delete(row);
    auditService.logDelete("DailyResourcePlan", id);
  }

  private DailyResourcePlan find(UUID projectId, UUID id) {
    DailyResourcePlan row = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("DailyResourcePlan", id));
    if (!row.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("DailyResourcePlan", id);
    }
    return row;
  }

  private void enforceMutuallyExclusive(CreateDailyResourcePlanRequest request) {
    boolean hasType = request.resourceTypeDefId() != null;
    boolean hasResource = request.resourceId() != null;
    if (hasType == hasResource) {
      throw new BusinessRuleException("PLAN_SCOPE_AMBIGUOUS",
          "Provide exactly one of resourceTypeDefId or resourceId");
    }
  }

  private void rejectDuplicate(UUID projectId, LocalDate planDate, UUID typeId, UUID resourceId, UUID excludeId) {
    repository
        .findFirstByProjectIdAndPlanDateAndResourceTypeDefIdAndResourceId(projectId, planDate, typeId, resourceId)
        .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
        .ifPresent(existing -> {
          throw new BusinessRuleException("DUPLICATE_DAILY_PLAN",
              "A plan already exists for this project + date + resource scope");
        });
  }

  private void ensureProjectExists(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }
}
```

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-project -am compile -q
git add backend/bipros-project/src/main/java/com/bipros/project/application/service/DailyResourcePlanService.java
git commit -m "feat(project): add DailyResourcePlanService with mutual-exclusion + dedup"
```

---

## Task 6: Service unit tests

**Files:**
- Create: `backend/bipros-project/src/test/java/com/bipros/project/application/service/DailyResourcePlanServiceTest.java`

- [ ] **Step 1: Write the tests**

```java
package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDailyResourcePlanRequest;
import com.bipros.project.application.dto.DailyResourcePlanResponse;
import com.bipros.project.domain.model.DailyResourcePlan;
import com.bipros.project.domain.repository.DailyResourcePlanRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyResourcePlanServiceTest {

  @Mock DailyResourcePlanRepository repository;
  @Mock ProjectRepository projectRepository;
  @Mock AuditService auditService;
  @InjectMocks DailyResourcePlanService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID typeId = UUID.randomUUID();
  private final UUID resourceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    lenient().when(projectRepository.existsById(projectId)).thenReturn(true);
    lenient().when(repository.save(any())).thenAnswer(inv -> {
      DailyResourcePlan p = inv.getArgument(0);
      p.setId(UUID.randomUUID());
      return p;
    });
  }

  @Test
  void createsTypeLevelPlan() {
    when(repository.findFirstByProjectIdAndPlanDateAndResourceTypeDefIdAndResourceId(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    var req = new CreateDailyResourcePlanRequest(LocalDate.of(2026, 5, 1), typeId, null, 8.0, "ok");
    DailyResourcePlanResponse resp = service.create(projectId, req);
    assertThat(resp.resourceTypeDefId()).isEqualTo(typeId);
    assertThat(resp.plannedHours()).isEqualTo(8.0);
  }

  @Test
  void createsSpecificResourcePlan() {
    when(repository.findFirstByProjectIdAndPlanDateAndResourceTypeDefIdAndResourceId(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    var req = new CreateDailyResourcePlanRequest(LocalDate.of(2026, 5, 1), null, resourceId, 10.0, null);
    DailyResourcePlanResponse resp = service.create(projectId, req);
    assertThat(resp.resourceId()).isEqualTo(resourceId);
  }

  @Test
  void rejectsBothScopes() {
    var req = new CreateDailyResourcePlanRequest(LocalDate.now(), typeId, resourceId, 8.0, null);
    assertThatThrownBy(() -> service.create(projectId, req))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("exactly one");
  }

  @Test
  void rejectsNeitherScope() {
    var req = new CreateDailyResourcePlanRequest(LocalDate.now(), null, null, 8.0, null);
    assertThatThrownBy(() -> service.create(projectId, req))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("exactly one");
  }

  @Test
  void rejectsDuplicate() {
    DailyResourcePlan existing = DailyResourcePlan.builder()
        .projectId(projectId).planDate(LocalDate.of(2026, 5, 1))
        .resourceTypeDefId(typeId).plannedHours(8.0).build();
    existing.setId(UUID.randomUUID());
    when(repository.findFirstByProjectIdAndPlanDateAndResourceTypeDefIdAndResourceId(
        projectId, LocalDate.of(2026, 5, 1), typeId, null))
        .thenReturn(Optional.of(existing));
    var req = new CreateDailyResourcePlanRequest(LocalDate.of(2026, 5, 1), typeId, null, 10.0, null);
    assertThatThrownBy(() -> service.create(projectId, req))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("already exists");
  }
}
```

- [ ] **Step 2: Run tests + commit**

```bash
cd backend && mvn -pl bipros-project -am test -Dtest=DailyResourcePlanServiceTest -q
git add backend/bipros-project/src/test/java/com/bipros/project/application/service/DailyResourcePlanServiceTest.java
git commit -m "test(project): cover DailyResourcePlanService scope + dedup rules"
```
Expected: 5 tests pass.

---

## Task 7: `DailyResourcePlanController`

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/api/DailyResourcePlanController.java`

- [ ] **Step 1: Write the controller**

```java
package com.bipros.project.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.application.dto.CreateDailyResourcePlanRequest;
import com.bipros.project.application.dto.DailyResourcePlanResponse;
import com.bipros.project.application.service.DailyResourcePlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/resource-plans")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DailyResourcePlanController {

  private final DailyResourcePlanService service;

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','SITE_SUPERVISOR')")
  public ResponseEntity<ApiResponse<DailyResourcePlanResponse>> create(
      @PathVariable UUID projectId, @Valid @RequestBody CreateDailyResourcePlanRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(service.create(projectId, request)));
  }

  @PostMapping("/bulk")
  @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','SITE_SUPERVISOR')")
  public ResponseEntity<ApiResponse<List<DailyResourcePlanResponse>>> createBulk(
      @PathVariable UUID projectId, @Valid @RequestBody List<CreateDailyResourcePlanRequest> requests) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(service.createBulk(projectId, requests)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<DailyResourcePlanResponse>>> list(
      @PathVariable UUID projectId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(ApiResponse.ok(service.list(projectId, from, to)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<DailyResourcePlanResponse>> get(
      @PathVariable UUID projectId, @PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(service.get(projectId, id)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID projectId, @PathVariable UUID id) {
    service.delete(projectId, id);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }
}
```

- [ ] **Step 2: Compile + smoke check**

```bash
cd backend && mvn -pl bipros-project -am compile -q
```

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/api/DailyResourcePlanController.java
git commit -m "feat(project): expose DailyResourcePlan CRUD endpoints"
```

---

## Task 8: `DailyDeploymentMatrixReport` DTO

**Files:**
- Create: `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/dto/DailyDeploymentMatrixReport.java`

- [ ] **Step 1: Write the DTO**

```java
package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Daily Deployment matrix report — three stacked matrices (Worked / Idle / Planned hours)
 * mirroring the Excel "Daily Deployment" sheet's three sub-tables.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyDeploymentMatrixReport(
    UUID projectId,
    String yearMonth,        // YYYY-MM
    int daysInMonth,
    String groupBy,          // RESOURCE_TYPE | RESOURCE
    Matrix workedHours,
    Matrix idleHours,
    Matrix plannedHours
) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Matrix(
      List<MatrixRow> rows,
      List<BigDecimal> columnTotals,
      BigDecimal grandTotal
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MatrixRow(
      GroupKey groupKey,
      List<BigDecimal> daily,   // length == daysInMonth, zero-filled
      BigDecimal rowTotal
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record GroupKey(
      UUID resourceTypeDefId,
      UUID resourceId,
      String displayLabel
  ) {}
}
```

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-reporting -am compile -q
git add backend/bipros-reporting/src/main/java/com/bipros/reporting/application/dto/DailyDeploymentMatrixReport.java
git commit -m "feat(reporting): add DailyDeploymentMatrixReport DTO"
```

---

## Task 9: `DailyDeploymentMatrixReportService`

**Files:**
- Create: `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/service/DailyDeploymentMatrixReportService.java`

- [ ] **Step 1: Write the service**

```java
package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.DailyDeploymentMatrixReport;
import com.bipros.reporting.application.dto.DailyDeploymentMatrixReport.GroupKey;
import com.bipros.reporting.application.dto.DailyDeploymentMatrixReport.Matrix;
import com.bipros.reporting.application.dto.DailyDeploymentMatrixReport.MatrixRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily Deployment matrix report — runs three native queries (worked, idle, planned hours)
 * and assembles each into a (resource × day-of-month) matrix mirroring the Excel sheet.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyDeploymentMatrixReportService {

  @PersistenceContext private EntityManager em;

  @Transactional(readOnly = true)
  public DailyDeploymentMatrixReport build(UUID projectId, String yearMonth, String groupBy) {
    YearMonth ym = YearMonth.parse(yearMonth);
    LocalDate from = ym.atDay(1);
    LocalDate to = ym.atEndOfMonth();
    int daysInMonth = ym.lengthOfMonth();
    String resolvedGroupBy = groupBy == null ? "RESOURCE_TYPE" : groupBy.toUpperCase();
    boolean groupByResource = "RESOURCE".equals(resolvedGroupBy);

    Matrix worked = buildMatrix(
        deploymentSql("hours_worked", groupByResource), projectId, from, to, daysInMonth);
    Matrix idle = buildMatrix(
        deploymentSql("idle_hours", groupByResource), projectId, from, to, daysInMonth);
    Matrix planned = buildMatrix(
        plannedSql(groupByResource), projectId, from, to, daysInMonth);

    return new DailyDeploymentMatrixReport(
        projectId, yearMonth, daysInMonth, resolvedGroupBy, worked, idle, planned);
  }

  /** SQL for {@code daily_resource_deployments.hours_worked} or {@code .idle_hours}. */
  private String deploymentSql(String column, boolean groupByResource) {
    String groupSelect = groupByResource ? "d.resource_id" : "r.resource_type_def_id";
    String groupJoin = "LEFT JOIN resource.resources r ON r.id = d.resource_id "
        + "LEFT JOIN resource.resource_type_defs t ON t.id = r.resource_type_def_id";
    String labelExpr = groupByResource
        ? "COALESCE(r.code || ' — ' || r.name, d.resource_description, '(unknown)')"
        : "COALESCE(t.name, '(no type)')";
    return "SELECT " + groupSelect + " AS gk, " + labelExpr + " AS label, "
        + "EXTRACT(DAY FROM d.log_date)::int AS dom, "
        + "COALESCE(SUM(d." + column + "), 0)::numeric AS hrs "
        + "FROM project.daily_resource_deployments d "
        + groupJoin + " "
        + "WHERE d.project_id = :projectId "
        + "  AND d.log_date BETWEEN :fromDate AND :toDate "
        + "GROUP BY gk, label, dom "
        + "ORDER BY label, dom";
  }

  private String plannedSql(boolean groupByResource) {
    String groupSelect = groupByResource
        ? "p.resource_id"
        : "COALESCE(p.resource_type_def_id, r.resource_type_def_id)";
    String labelExpr = groupByResource
        ? "COALESCE(r.code || ' — ' || r.name, '(unknown)')"
        : "COALESCE(t.name, t2.name, '(no type)')";
    return "SELECT " + groupSelect + " AS gk, " + labelExpr + " AS label, "
        + "EXTRACT(DAY FROM p.plan_date)::int AS dom, "
        + "COALESCE(SUM(p.planned_hours), 0)::numeric AS hrs "
        + "FROM project.daily_resource_plans p "
        + "LEFT JOIN resource.resources r ON r.id = p.resource_id "
        + "LEFT JOIN resource.resource_type_defs t ON t.id = r.resource_type_def_id "
        + "LEFT JOIN resource.resource_type_defs t2 ON t2.id = p.resource_type_def_id "
        + "WHERE p.project_id = :projectId "
        + "  AND p.plan_date BETWEEN :fromDate AND :toDate "
        + "GROUP BY gk, label, dom "
        + "ORDER BY label, dom";
  }

  @SuppressWarnings("unchecked")
  private Matrix buildMatrix(String sql, UUID projectId, LocalDate from, LocalDate to, int daysInMonth) {
    List<Object[]> raw = em.createNativeQuery(sql)
        .setParameter("projectId", projectId)
        .setParameter("fromDate", from)
        .setParameter("toDate", to)
        .getResultList();

    Map<String, MatrixRowBuilder> byKey = new LinkedHashMap<>();
    BigDecimal[] columnTotals = new BigDecimal[daysInMonth];
    for (int i = 0; i < daysInMonth; i++) columnTotals[i] = BigDecimal.ZERO;
    BigDecimal grandTotal = BigDecimal.ZERO;

    for (Object[] r : raw) {
      Object gkObj = r[0];
      String label = (String) r[1];
      int dom = ((Number) r[2]).intValue();
      BigDecimal hrs = (BigDecimal) r[3];
      String key = String.valueOf(gkObj) + "|" + label;
      MatrixRowBuilder b = byKey.computeIfAbsent(key, k -> new MatrixRowBuilder(
          new GroupKey(
              gkObj instanceof UUID && !"resource_id".equals(k) ? (UUID) gkObj : null,
              null, label),
          daysInMonth));
      // Position cell into 0-indexed daily array.
      if (dom >= 1 && dom <= daysInMonth) {
        b.daily[dom - 1] = b.daily[dom - 1].add(hrs);
        b.rowTotal = b.rowTotal.add(hrs);
        columnTotals[dom - 1] = columnTotals[dom - 1].add(hrs);
        grandTotal = grandTotal.add(hrs);
      }
    }

    List<MatrixRow> rows = new ArrayList<>(byKey.size());
    for (MatrixRowBuilder b : byKey.values()) {
      rows.add(new MatrixRow(b.groupKey, List.of(b.daily), b.rowTotal));
    }
    return new Matrix(rows, List.of(columnTotals), grandTotal);
  }

  private static final class MatrixRowBuilder {
    final GroupKey groupKey;
    final BigDecimal[] daily;
    BigDecimal rowTotal = BigDecimal.ZERO;

    MatrixRowBuilder(GroupKey groupKey, int daysInMonth) {
      this.groupKey = groupKey;
      this.daily = new BigDecimal[daysInMonth];
      for (int i = 0; i < daysInMonth; i++) daily[i] = BigDecimal.ZERO;
    }
  }
}
```

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-reporting -am compile -q
git add backend/bipros-reporting/src/main/java/com/bipros/reporting/application/service/DailyDeploymentMatrixReportService.java
git commit -m "feat(reporting): add DailyDeploymentMatrixReportService (3 matrices via native SQL)"
```

---

## Task 10: Wire endpoint in `ReportController`

**Files:**
- Modify: `backend/bipros-reporting/src/main/java/com/bipros/reporting/presentation/controller/ReportController.java`

- [ ] **Step 1: Inject the new service**

Add to the controller's `@RequiredArgsConstructor` field list (alongside `capacityUtilizationReportService`):
```java
  private final DailyDeploymentMatrixReportService dailyDeploymentMatrixReportService;
```
Add the import.

- [ ] **Step 2: Add the endpoint**

After the existing `getCapacityUtilization` method, add:
```java
  @GetMapping("/daily-deployment-matrix")
  public ApiResponse<DailyDeploymentMatrixReport> getDailyDeploymentMatrix(
      @RequestParam UUID projectId,
      @RequestParam String yearMonth,
      @RequestParam(required = false, defaultValue = "RESOURCE_TYPE") String groupBy) {
    return ApiResponse.ok(dailyDeploymentMatrixReportService.build(projectId, yearMonth, groupBy));
  }
```
Add the import for `DailyDeploymentMatrixReport`.

- [ ] **Step 3: Compile + commit**

```bash
cd backend && mvn -pl bipros-reporting -am compile -q
git add backend/bipros-reporting/src/main/java/com/bipros/reporting/presentation/controller/ReportController.java
git commit -m "feat(reporting): expose GET /v1/reports/daily-deployment-matrix"
```

---

## Task 11: Backend integration test

**Files:**
- Create: `backend/bipros-api/src/test/java/com/bipros/api/integration/DailyDeploymentMatrixIntegrationTest.java`

- [ ] **Step 1: Write the test**

Use the same `localit` profile pattern established by `CapacityUtilizationReportIntegrationTest` (Plan 1's Task 14). Boot Spring with a real Postgres, autowire `DailyDeploymentMatrixReportService`, seed:

- 1 project, 1 resource type def (Bull Dozer), 1 resource (BD-001), 3 daily deployments (1st, 2nd, 3rd of a known month) with `hours_worked` and `idle_hours`, 2 plans (1st, 2nd of same month) with `planned_hours`.
- Then call `service.build(projectId, "YYYY-MM", "RESOURCE_TYPE")` and assert:
  - `daysInMonth` matches the chosen month (e.g. `daysInMonth=30` for April).
  - `workedHours.rows` has 1 row, `daily[0]`, `daily[1]`, `daily[2]` non-zero, `daily[3..]` zero.
  - `idleHours.rows[0].rowTotal` matches sum of seeded idle hours.
  - `plannedHours.rows[0].rowTotal` matches sum of seeded plans.
  - `workedHours.columnTotals[0]` equals row[0].daily[0].
- Group-by-RESOURCE variant: same seed, call with `groupBy="RESOURCE"`, expect labels including the resource code.

- [ ] **Step 2: Run + commit**

```bash
cd backend && mvn -pl bipros-api -am test -Dtest=DailyDeploymentMatrixIntegrationTest -q
git add backend/bipros-api/src/test/java/com/bipros/api/integration/DailyDeploymentMatrixIntegrationTest.java
git commit -m "test(api): integration test for daily-deployment-matrix endpoint"
```
Expected: 2 tests pass.

---

## Task 12: Frontend API clients

**Files:**
- Create: `frontend/src/lib/api/dailyDeploymentMatrixApi.ts`
- Create: `frontend/src/lib/api/dailyResourcePlanApi.ts`

- [ ] **Step 1: Write `dailyDeploymentMatrixApi.ts`**

```typescript
import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type MatrixGroupBy = "RESOURCE_TYPE" | "RESOURCE";

export interface MatrixRow {
  groupKey: { resourceTypeDefId: string | null; resourceId: string | null; displayLabel: string };
  daily: number[];
  rowTotal: number;
}
export interface Matrix {
  rows: MatrixRow[];
  columnTotals: number[];
  grandTotal: number;
}
export interface DailyDeploymentMatrixReport {
  projectId: string;
  yearMonth: string;
  daysInMonth: number;
  groupBy: MatrixGroupBy;
  workedHours: Matrix;
  idleHours: Matrix;
  plannedHours: Matrix;
}

export interface GetDailyDeploymentMatrixParams {
  projectId: string;
  yearMonth: string;
  groupBy?: MatrixGroupBy;
}

export const dailyDeploymentMatrixApi = {
  get: (params: GetDailyDeploymentMatrixParams) => {
    const qs = [`projectId=${params.projectId}`, `yearMonth=${params.yearMonth}`];
    if (params.groupBy) qs.push(`groupBy=${params.groupBy}`);
    return apiClient
      .get<ApiResponse<DailyDeploymentMatrixReport>>(`/v1/reports/daily-deployment-matrix?${qs.join("&")}`)
      .then((r) => r.data);
  },
};
```

- [ ] **Step 2: Write `dailyResourcePlanApi.ts`**

```typescript
import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface DailyResourcePlanResponse {
  id: string;
  projectId: string;
  planDate: string;
  resourceTypeDefId: string | null;
  resourceId: string | null;
  plannedHours: number;
  remarks: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDailyResourcePlanRequest {
  planDate: string;
  resourceTypeDefId?: string | null;  // exactly one of these two must be set
  resourceId?: string | null;
  plannedHours: number;
  remarks?: string | null;
}

export const dailyResourcePlanApi = {
  list: (projectId: string, from?: string, to?: string) => {
    const qs: string[] = [];
    if (from) qs.push(`from=${from}`);
    if (to) qs.push(`to=${to}`);
    const suffix = qs.length ? `?${qs.join("&")}` : "";
    return apiClient
      .get<ApiResponse<DailyResourcePlanResponse[]>>(`/v1/projects/${projectId}/resource-plans${suffix}`)
      .then((r) => r.data);
  },
  create: (projectId: string, request: CreateDailyResourcePlanRequest) =>
    apiClient
      .post<ApiResponse<DailyResourcePlanResponse>>(`/v1/projects/${projectId}/resource-plans`, request)
      .then((r) => r.data),
  createBulk: (projectId: string, requests: CreateDailyResourcePlanRequest[]) =>
    apiClient
      .post<ApiResponse<DailyResourcePlanResponse[]>>(`/v1/projects/${projectId}/resource-plans/bulk`, requests)
      .then((r) => r.data),
  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/resource-plans/${id}`),
};
```

- [ ] **Step 3: Type-check + commit**

```bash
cd frontend && pnpm exec tsc --noEmit
git add frontend/src/lib/api/dailyDeploymentMatrixApi.ts frontend/src/lib/api/dailyResourcePlanApi.ts
git commit -m "feat(frontend): API clients for daily-deployment-matrix + resource-plans"
```

---

## Task 13: Resource-plans bulk-paste form page

**Files:**
- Create: `frontend/src/app/(app)/projects/[projectId]/resource-plans/page.tsx`

- [ ] **Step 1: Write the page**

A list view with an "Add Plan" form (date picker, resource-type dropdown OR resource dropdown, planned hours, remarks). Follow the same pattern as `daily-outputs/page.tsx` (TanStack Query, axios via `apiClient`, Tailwind classes that match the project's `bg-surface`, `text-text-primary`, `border-border` design tokens).

Two key behaviors:
1. When user picks a Resource Type, blank the Resource dropdown (and vice versa) — exactly one must be set.
2. Validate `plannedHours >= 0` before submit.

Reuse `SearchableSelect` (`@/components/common/SearchableSelect`) for the dropdowns. Resource list comes from `resourceApi.listResources()`. Resource type list comes from existing `resourceTypeApi` if present (search `frontend/src/lib/api/` for `resource-types`); fall back to `resourceTypeDefApi`.

Skeleton:
```tsx
"use client";
import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  dailyResourcePlanApi,
  type CreateDailyResourcePlanRequest,
} from "@/lib/api/dailyResourcePlanApi";
import { resourceApi } from "@/lib/api/resourceApi";
import { TabTip } from "@/components/common/TabTip";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";
// ... (full list/form skeleton — see daily-outputs/page.tsx as a 1:1 reference)
```

- [ ] **Step 2: Type-check + commit**

```bash
cd frontend && pnpm exec tsc --noEmit
git add frontend/src/app/\(app\)/projects/\[projectId\]/resource-plans/page.tsx
git commit -m "feat(frontend): add resource-plans CRUD page (drives daily-deployment matrix planned block)"
```

---

## Task 14: Daily-deployment matrix viewer page

**Files:**
- Create: `frontend/src/app/(app)/projects/[projectId]/daily-deployment-matrix/page.tsx`

- [ ] **Step 1: Write the page**

The page renders three stacked tables, each with rows = resources (or types) and columns = day 1..daysInMonth, plus a "Total" column at the right. Above the tables, a month picker (default current month) and a group-by toggle.

Shape (skeleton):
```tsx
"use client";
import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Download } from "lucide-react";
import {
  dailyDeploymentMatrixApi,
  type Matrix,
  type MatrixGroupBy,
} from "@/lib/api/dailyDeploymentMatrixApi";
import { TabTip } from "@/components/common/TabTip";

const currentMonth = () => new Date().toISOString().slice(0, 7);

function MatrixTable({ title, m, daysInMonth }: { title: string; m: Matrix; daysInMonth: number }) {
  const dayHeaders = Array.from({ length: daysInMonth }, (_, i) => i + 1);
  return (
    <div className="mb-8">
      <h3 className="text-lg font-semibold text-text-primary mb-2">{title}</h3>
      <div className="overflow-x-auto">
        <table className="border-collapse border border-border text-sm">
          <thead>
            <tr className="bg-surface/80 text-text-secondary">
              <th className="border border-border px-2 py-1 sticky left-0 bg-surface/80 z-10 text-left">Resource</th>
              {dayHeaders.map((d) => (
                <th key={d} className="border border-border px-2 py-1 text-right">{d}</th>
              ))}
              <th className="border border-border px-2 py-1 text-right font-bold">Total</th>
            </tr>
          </thead>
          <tbody>
            {m.rows.map((r) => (
              <tr key={r.groupKey.displayLabel}>
                <td className="border border-border px-2 py-1 sticky left-0 bg-surface text-text-primary">{r.groupKey.displayLabel}</td>
                {r.daily.map((v, i) => (
                  <td key={i} className="border border-border px-2 py-1 text-right text-text-primary">{v ? v : ""}</td>
                ))}
                <td className="border border-border px-2 py-1 text-right font-semibold text-text-primary">{r.rowTotal}</td>
              </tr>
            ))}
            <tr className="font-semibold bg-surface/50">
              <td className="border border-border px-2 py-1 sticky left-0 bg-surface/50 text-text-primary">Total</td>
              {m.columnTotals.map((v, i) => (
                <td key={i} className="border border-border px-2 py-1 text-right text-text-primary">{v ? v : ""}</td>
              ))}
              <td className="border border-border px-2 py-1 text-right font-bold text-text-primary">{m.grandTotal}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function DailyDeploymentMatrixPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [yearMonth, setYearMonth] = useState(currentMonth());
  const [groupBy, setGroupBy] = useState<MatrixGroupBy>("RESOURCE_TYPE");

  const { data, isLoading } = useQuery({
    queryKey: ["daily-deployment-matrix", projectId, yearMonth, groupBy],
    queryFn: () => dailyDeploymentMatrixApi.get({ projectId, yearMonth, groupBy }),
  });

  const report = data?.data;
  if (isLoading) return <div className="p-6 text-text-muted">Loading…</div>;
  if (!report) return <div className="p-6 text-danger">Failed to load report.</div>;

  return (
    <div className="p-6">
      <TabTip title="Daily Deployment Matrix" description="Hours per resource per day for the month — Worked, Idle, and Planned blocks." />
      <div className="mb-4 flex gap-3 items-end">
        <div>
          <label className="block text-sm text-text-secondary mb-1">Month</label>
          <input type="month" value={yearMonth} onChange={(e) => setYearMonth(e.target.value)}
            className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg" />
        </div>
        <div>
          <label className="block text-sm text-text-secondary mb-1">Group by</label>
          <select value={groupBy} onChange={(e) => setGroupBy(e.target.value as MatrixGroupBy)}
            className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg">
            <option value="RESOURCE_TYPE">Resource Type</option>
            <option value="RESOURCE">Specific Resource</option>
          </select>
        </div>
      </div>
      <MatrixTable title="Worked Hours" m={report.workedHours} daysInMonth={report.daysInMonth} />
      <MatrixTable title="Idle Hours" m={report.idleHours} daysInMonth={report.daysInMonth} />
      <MatrixTable title="Planned Hours" m={report.plannedHours} daysInMonth={report.daysInMonth} />
    </div>
  );
}
```

(Optional CSV download follows the same pattern as the capacity-utilization page; add only if you finish the rest with time to spare.)

- [ ] **Step 2: Type-check + commit**

```bash
cd frontend && pnpm exec tsc --noEmit
git add frontend/src/app/\(app\)/projects/\[projectId\]/daily-deployment-matrix/page.tsx
git commit -m "feat(frontend): add daily-deployment-matrix viewer page (3 stacked matrices)"
```

---

## Task 15: Add tabs to project layout

**Files:**
- Modify: `frontend/src/app/(app)/projects/[projectId]/layout.tsx`

- [ ] **Step 1: Add the two tabs**

Find the existing `tabs` array (look for the line `{ id: "capacity", label: "Capacity Util.", href: ... }`). After it, add:
```tsx
{ id: "deployment-matrix", label: "Deployment Matrix", href: `/projects/${projectId}/daily-deployment-matrix` },
{ id: "resource-plans",    label: "Resource Plans",    href: `/projects/${projectId}/resource-plans` },
```

- [ ] **Step 2: Type-check + commit**

```bash
cd frontend && pnpm exec tsc --noEmit
git add frontend/src/app/\(app\)/projects/\[projectId\]/layout.tsx
git commit -m "feat(frontend): add Deployment Matrix + Resource Plans tabs to project nav"
```

---

## Task 16: Playwright e2e

**Files:**
- Create: `frontend/e2e/tests/22-daily-deployment-matrix.spec.ts`

- [ ] **Step 1: Write the e2e**

```ts
import { test, expect } from "../fixtures/auth.fixture";

test.describe("Daily Deployment Matrix", () => {
  test("page renders with three matrices and month picker", async ({ authenticatedPage: page }) => {
    await page.goto("/projects");
    await expect(page.getByRole("heading", { name: /projects/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    const projectLink = page.locator('a[href^="/projects/"]:not([href$="/new"])').first();
    await projectLink.click();
    await expect(page).toHaveURL(/\/projects\/[0-9a-f-]+/);
    await page.goto(page.url().replace(/\/projects\/([0-9a-f-]+).*/, "/projects/$1/daily-deployment-matrix"));
    await expect(page.getByRole("heading", { name: /Daily Deployment Matrix/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Worked Hours/i)).toBeVisible();
    await expect(page.getByText(/Idle Hours/i)).toBeVisible();
    await expect(page.getByText(/Planned Hours/i)).toBeVisible();
    await expect(page.locator('input[type="month"]')).toBeVisible();
  });
});
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/tests/22-daily-deployment-matrix.spec.ts
git commit -m "test(frontend): e2e for daily-deployment-matrix page"
```

---

## Task 17: Final verification

- [ ] **Step 1: Backend full test pass**

```bash
cd backend && mvn -q -pl bipros-project,bipros-reporting,bipros-api -am test
```
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 2: Frontend type-check**

```bash
cd frontend && pnpm exec tsc --noEmit
```
Expected: 0 errors.

- [ ] **Step 3: API smoke test**

Backend running:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')
PROJECT_ID=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/projects | jq -r '.data[0].id')
MONTH=$(date +%Y-%m)
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/v1/reports/daily-deployment-matrix?projectId=$PROJECT_ID&yearMonth=$MONTH" \
  | jq '.data | { yearMonth, daysInMonth, workedRows: (.workedHours.rows|length), idleRows: (.idleHours.rows|length), plannedRows: (.plannedHours.rows|length) }'
```
Expected: a JSON object with the four counts (rows may be 0 if no data).

---

## Self-Review Notes

**Spec coverage** (against `docs/superpowers/specs/2026-04-27-capacity-utilization-reports-design.md` §2.4 + §4):
- §2.4 (`DailyResourcePlan` entity + CRUD) → Tasks 1–7
- §4.1 (matrix endpoint) → Tasks 8–10
- §4.2 (DTO shape) → Task 8
- §4.3 (service with 3 native queries) → Task 9
- §4.4 (frontend page) → Task 14, plus the resource-plans data-entry page (Task 13) and the new tabs (Task 15)
- Testing → Tasks 6, 11, 16, 17

**Type consistency:**
- DTO field names: `workedHours`, `idleHours`, `plannedHours` (camelCase) match between Java DTO (Task 8), service (Task 9), API client (Task 12), and frontend page (Task 14).
- `MatrixGroupBy` values: `"RESOURCE_TYPE" | "RESOURCE"` match between backend service param and frontend toggle.
- Liquibase changeset id `047-...` follows the established `<seq>-<descriptive-name>` pattern.
- `BaseEntity` field set (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `version`) covered in the changeset.
