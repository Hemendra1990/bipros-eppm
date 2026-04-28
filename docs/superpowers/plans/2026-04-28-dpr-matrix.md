# DPR Matrix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproduce the Excel "DPR" sheet — a BOQ × calendar matrix with per-day achieved quantities and monthly Projection vs Achieved totals. Adds a new `MonthlyBoqProjection` entity for the planned monthly target column, a new matrix-shape report endpoint, and frontend pages for both the report and the projection-entry form.

**Architecture:** A new entity `project.monthly_boq_projections` stores per-(BOQ × month) planned quantity (with derived planned amount = qty × `BoqItem.budgetedRate`). A new service `DprMatrixReportService` runs a single native query left-joining `boq_items ⨝ monthly_boq_projections ⨝ daily_progress_reports` and assembles a wide row per BOQ item with per-day cells. New endpoint `GET /v1/reports/dpr-matrix`. Two new frontend pages: a matrix viewer and a bulk-paste projection form.

**Tech Stack:** Java 23 / Spring Boot 3.5, JPA + Liquibase, native SQL, JUnit 5, Next.js 16 / React 19 / TanStack Query.

**Spec:** `docs/superpowers/specs/2026-04-27-capacity-utilization-reports-design.md` (sections 2.3, 5).

**Out of scope (covered by separate plans):** Capacity Utilization extension (Plan 1); Daily-Deployment matrix (Plan 2); Oman seed scripts (Plan 4).

**Depends on:** none (independent of Plans 1 and 2 — different entities and different endpoints).

---

## File-touch summary

**Backend (Java) — created:**
- `backend/bipros-api/src/main/resources/db/changelog/048-monthly-boq-projection.yaml`
- `backend/bipros-project/src/main/java/com/bipros/project/domain/model/MonthlyBoqProjection.java`
- `backend/bipros-project/src/main/java/com/bipros/project/domain/repository/MonthlyBoqProjectionRepository.java`
- `backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateMonthlyBoqProjectionRequest.java`
- `backend/bipros-project/src/main/java/com/bipros/project/application/dto/MonthlyBoqProjectionResponse.java`
- `backend/bipros-project/src/main/java/com/bipros/project/application/service/MonthlyBoqProjectionService.java`
- `backend/bipros-project/src/main/java/com/bipros/project/api/MonthlyBoqProjectionController.java`
- `backend/bipros-project/src/test/java/com/bipros/project/application/service/MonthlyBoqProjectionServiceTest.java`
- `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/dto/DprMatrixReport.java`
- `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/service/DprMatrixReportService.java`
- `backend/bipros-api/src/test/java/com/bipros/api/integration/DprMatrixIntegrationTest.java`

**Backend (Java) — modified:**
- `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml` — register changeset 048
- `backend/bipros-reporting/src/main/java/com/bipros/reporting/presentation/controller/ReportController.java` — wire new endpoint

**Frontend (TypeScript) — created:**
- `frontend/src/lib/api/dprMatrixApi.ts`
- `frontend/src/lib/api/monthlyBoqProjectionApi.ts`
- `frontend/src/app/(app)/projects/[projectId]/dpr-matrix/page.tsx`
- `frontend/src/app/(app)/projects/[projectId]/boq-projections/page.tsx`
- `frontend/e2e/tests/23-dpr-matrix.spec.ts`

**Frontend — modified:**
- `frontend/src/app/(app)/projects/[projectId]/layout.tsx` — add 2 new tabs

---

## Task 1: Liquibase changeset 048 — `monthly_boq_projections` table

**Files:**
- Create: `backend/bipros-api/src/main/resources/db/changelog/048-monthly-boq-projection.yaml`
- Modify: `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Write the changeset**

```yaml
databaseChangeLog:
  - changeSet:
      id: 048-create-monthly-boq-projections
      author: bipros
      changes:
        - createTable:
            schemaName: project
            tableName: monthly_boq_projections
            columns:
              - column: { name: id,              type: UUID,           constraints: { primaryKey: true, nullable: false } }
              - column: { name: project_id,      type: UUID,           constraints: { nullable: false } }
              - column: { name: boq_item_no,     type: VARCHAR(20),    constraints: { nullable: false } }
              - column: { name: year_month,      type: VARCHAR(7),     constraints: { nullable: false } }
              - column: { name: planned_qty,     type: DECIMAL(18,3),  constraints: { nullable: false } }
              - column: { name: planned_amount,  type: DECIMAL(19,2),  constraints: { nullable: false } }
              - column: { name: remarks,         type: VARCHAR(500) }
              - column: { name: created_at,      type: TIMESTAMP,      constraints: { nullable: false } }
              - column: { name: updated_at,      type: TIMESTAMP,      constraints: { nullable: false } }
              - column: { name: created_by,      type: VARCHAR(255) }
              - column: { name: updated_by,      type: VARCHAR(255) }
              - column: { name: version,         type: BIGINT }
        - addUniqueConstraint:
            schemaName: project
            tableName: monthly_boq_projections
            constraintName: uk_mbp_unique
            columnNames: project_id, boq_item_no, year_month
        - createIndex:
            schemaName: project
            tableName: monthly_boq_projections
            indexName: idx_mbp_project_month
            columns:
              - column: { name: project_id }
              - column: { name: year_month }
```

- [ ] **Step 2: Register in master**

Append to `db.changelog-master.yaml`:
```yaml
  - include:
      file: db/changelog/048-monthly-boq-projection.yaml
```

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-api/src/main/resources/db/changelog/048-monthly-boq-projection.yaml backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat(project): add monthly_boq_projections table for per-month BOQ projection targets"
```

---

## Task 2: `MonthlyBoqProjection` entity

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/domain/model/MonthlyBoqProjection.java`

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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-month planned target for a BOQ item — feeds the "Projection" columns of the Excel "DPR"
 * sheet. {@code planned_amount} is derived from {@code planned_qty × BoqItem.budgetedRate} at
 * service-layer save time (mirrors how {@code BoqCalculator} handles BoqItem amounts).
 */
@Entity
@Table(
    name = "monthly_boq_projections",
    schema = "project",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_mbp_unique",
            columnNames = {"project_id", "boq_item_no", "year_month"})
    },
    indexes = {
        @Index(name = "idx_mbp_project_month", columnList = "project_id, year_month")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBoqProjection extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  /** Soft FK to {@code project.boq_items.item_no} within the same project. */
  @Column(name = "boq_item_no", nullable = false, length = 20)
  private String boqItemNo;

  /** Format: {@code YYYY-MM}. */
  @Column(name = "year_month", nullable = false, length = 7)
  private String yearMonth;

  @Column(name = "planned_qty", nullable = false, precision = 18, scale = 3)
  private BigDecimal plannedQty;

  /** Derived: {@link #plannedQty} × {@code BoqItem.budgetedRate}. Recomputed on every save. */
  @Column(name = "planned_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal plannedAmount;

  @Column(name = "remarks", length = 500)
  private String remarks;
}
```

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-project -am compile -q
git add backend/bipros-project/src/main/java/com/bipros/project/domain/model/MonthlyBoqProjection.java
git commit -m "feat(project): add MonthlyBoqProjection entity"
```

---

## Task 3: `MonthlyBoqProjectionRepository`

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/domain/repository/MonthlyBoqProjectionRepository.java`

- [ ] **Step 1: Write the repository**

```java
package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.MonthlyBoqProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonthlyBoqProjectionRepository extends JpaRepository<MonthlyBoqProjection, UUID> {

  List<MonthlyBoqProjection> findByProjectIdAndYearMonthOrderByBoqItemNoAsc(
      UUID projectId, String yearMonth);

  List<MonthlyBoqProjection> findByProjectIdOrderByYearMonthAscBoqItemNoAsc(UUID projectId);

  Optional<MonthlyBoqProjection> findFirstByProjectIdAndBoqItemNoAndYearMonth(
      UUID projectId, String boqItemNo, String yearMonth);
}
```

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-project -am compile -q
git add backend/bipros-project/src/main/java/com/bipros/project/domain/repository/MonthlyBoqProjectionRepository.java
git commit -m "feat(project): add MonthlyBoqProjectionRepository"
```

---

## Task 4: Request and response DTOs

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateMonthlyBoqProjectionRequest.java`
- Create: `backend/bipros-project/src/main/java/com/bipros/project/application/dto/MonthlyBoqProjectionResponse.java`

- [ ] **Step 1: Write the DTOs**

`CreateMonthlyBoqProjectionRequest.java`:
```java
package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateMonthlyBoqProjectionRequest(
    @NotBlank(message = "boqItemNo is required") @Size(max = 20) String boqItemNo,
    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}", message = "yearMonth must be YYYY-MM") String yearMonth,
    @NotNull @PositiveOrZero BigDecimal plannedQty,
    @Size(max = 500) String remarks
) {}
```

`MonthlyBoqProjectionResponse.java`:
```java
package com.bipros.project.application.dto;

import com.bipros.project.domain.model.MonthlyBoqProjection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MonthlyBoqProjectionResponse(
    UUID id, UUID projectId, String boqItemNo, String yearMonth,
    BigDecimal plannedQty, BigDecimal plannedAmount, String remarks,
    Instant createdAt, Instant updatedAt
) {
  public static MonthlyBoqProjectionResponse from(MonthlyBoqProjection p) {
    return new MonthlyBoqProjectionResponse(
        p.getId(), p.getProjectId(), p.getBoqItemNo(), p.getYearMonth(),
        p.getPlannedQty(), p.getPlannedAmount(), p.getRemarks(),
        p.getCreatedAt(), p.getUpdatedAt());
  }
}
```

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-project -am compile -q
git add backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateMonthlyBoqProjectionRequest.java backend/bipros-project/src/main/java/com/bipros/project/application/dto/MonthlyBoqProjectionResponse.java
git commit -m "feat(project): add MonthlyBoqProjection request + response DTOs"
```

---

## Task 5: `MonthlyBoqProjectionService`

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/application/service/MonthlyBoqProjectionService.java`

- [ ] **Step 1: Write the service**

```java
package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateMonthlyBoqProjectionRequest;
import com.bipros.project.application.dto.MonthlyBoqProjectionResponse;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.MonthlyBoqProjection;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.MonthlyBoqProjectionRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MonthlyBoqProjectionService {

  private final MonthlyBoqProjectionRepository repository;
  private final BoqItemRepository boqItemRepository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

  public MonthlyBoqProjectionResponse create(UUID projectId, CreateMonthlyBoqProjectionRequest request) {
    ensureProjectExists(projectId);
    rejectDuplicate(projectId, request.boqItemNo(), request.yearMonth(), null);

    BigDecimal plannedAmount = derivePlannedAmount(projectId, request.boqItemNo(), request.plannedQty());

    MonthlyBoqProjection row = MonthlyBoqProjection.builder()
        .projectId(projectId)
        .boqItemNo(request.boqItemNo())
        .yearMonth(request.yearMonth())
        .plannedQty(request.plannedQty())
        .plannedAmount(plannedAmount)
        .remarks(request.remarks())
        .build();

    MonthlyBoqProjection saved = repository.save(row);
    auditService.logCreate("MonthlyBoqProjection", saved.getId(), MonthlyBoqProjectionResponse.from(saved));
    return MonthlyBoqProjectionResponse.from(saved);
  }

  public List<MonthlyBoqProjectionResponse> createBulk(UUID projectId, List<CreateMonthlyBoqProjectionRequest> requests) {
    return requests.stream().map(r -> create(projectId, r)).toList();
  }

  public MonthlyBoqProjectionResponse update(UUID projectId, UUID id, CreateMonthlyBoqProjectionRequest request) {
    MonthlyBoqProjection row = find(projectId, id);
    rejectDuplicate(projectId, request.boqItemNo(), request.yearMonth(), id);
    BigDecimal plannedAmount = derivePlannedAmount(projectId, request.boqItemNo(), request.plannedQty());
    row.setBoqItemNo(request.boqItemNo());
    row.setYearMonth(request.yearMonth());
    row.setPlannedQty(request.plannedQty());
    row.setPlannedAmount(plannedAmount);
    row.setRemarks(request.remarks());
    MonthlyBoqProjection saved = repository.save(row);
    auditService.logUpdate("MonthlyBoqProjection", id, "projection", null, MonthlyBoqProjectionResponse.from(saved));
    return MonthlyBoqProjectionResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<MonthlyBoqProjectionResponse> list(UUID projectId, String yearMonth) {
    ensureProjectExists(projectId);
    List<MonthlyBoqProjection> rows = (yearMonth != null && !yearMonth.isBlank())
        ? repository.findByProjectIdAndYearMonthOrderByBoqItemNoAsc(projectId, yearMonth)
        : repository.findByProjectIdOrderByYearMonthAscBoqItemNoAsc(projectId);
    return rows.stream().map(MonthlyBoqProjectionResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public MonthlyBoqProjectionResponse get(UUID projectId, UUID id) {
    return MonthlyBoqProjectionResponse.from(find(projectId, id));
  }

  public void delete(UUID projectId, UUID id) {
    MonthlyBoqProjection row = find(projectId, id);
    repository.delete(row);
    auditService.logDelete("MonthlyBoqProjection", id);
  }

  private MonthlyBoqProjection find(UUID projectId, UUID id) {
    MonthlyBoqProjection row = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("MonthlyBoqProjection", id));
    if (!row.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("MonthlyBoqProjection", id);
    }
    return row;
  }

  /** plannedAmount = plannedQty × BoqItem.budgetedRate (or 0 if budgetedRate is null). */
  private BigDecimal derivePlannedAmount(UUID projectId, String boqItemNo, BigDecimal plannedQty) {
    BoqItem boqItem = boqItemRepository
        .findByProjectIdAndItemNo(projectId, boqItemNo)
        .orElseThrow(() -> new BusinessRuleException("BOQ_NOT_FOUND",
            "No BOQ item " + boqItemNo + " in this project"));
    BigDecimal rate = boqItem.getBudgetedRate() == null ? BigDecimal.ZERO : boqItem.getBudgetedRate();
    return plannedQty.multiply(rate).setScale(2, RoundingMode.HALF_UP);
  }

  private void rejectDuplicate(UUID projectId, String boqItemNo, String yearMonth, UUID excludeId) {
    repository.findFirstByProjectIdAndBoqItemNoAndYearMonth(projectId, boqItemNo, yearMonth)
        .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
        .ifPresent(existing -> {
          throw new BusinessRuleException("DUPLICATE_BOQ_PROJECTION",
              "A projection already exists for " + boqItemNo + " in " + yearMonth);
        });
  }

  private void ensureProjectExists(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }
}
```

Note: `BoqItemRepository` likely already has `findByProjectIdAndItemNo`. Verify with a quick grep; if not present, add the finder method.

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-project -am compile -q
git add backend/bipros-project/src/main/java/com/bipros/project/application/service/MonthlyBoqProjectionService.java backend/bipros-project/src/main/java/com/bipros/project/domain/repository/BoqItemRepository.java
git commit -m "feat(project): add MonthlyBoqProjectionService with derived plannedAmount + dedup"
```

---

## Task 6: Service unit tests

**Files:**
- Create: `backend/bipros-project/src/test/java/com/bipros/project/application/service/MonthlyBoqProjectionServiceTest.java`

- [ ] **Step 1: Write the tests**

5 tests via `@ExtendWith(MockitoExtension.class)` + `@InjectMocks`:

1. `createsProjectionWithDerivedAmount` — supply `plannedQty=100`, mock BoqItem with `budgetedRate=50`, expect `plannedAmount=5000`.
2. `createsWithZeroAmountWhenBudgetedRateNull` — BoqItem.budgetedRate=null → `plannedAmount=0`.
3. `rejectsUnknownBoqItem` — BoqItemRepository returns empty → BusinessRuleException with code `BOQ_NOT_FOUND`.
4. `rejectsDuplicateProjection` — existing row in repo for same (project, item, month) → BusinessRuleException with code `DUPLICATE_BOQ_PROJECTION`.
5. `updatesPlannedAmountOnQtyChange` — call update with new qty, assert plannedAmount recomputes.

Use the same `@InjectMocks` + `lenient().when(...)` pattern from `DailyResourcePlanServiceTest` (Plan 2's Task 6).

- [ ] **Step 2: Run + commit**

```bash
cd backend && mvn -pl bipros-project -am test -Dtest=MonthlyBoqProjectionServiceTest -q
git add backend/bipros-project/src/test/java/com/bipros/project/application/service/MonthlyBoqProjectionServiceTest.java
git commit -m "test(project): cover MonthlyBoqProjectionService derivation + dedup"
```
Expected: 5 tests pass.

---

## Task 7: `MonthlyBoqProjectionController`

**Files:**
- Create: `backend/bipros-project/src/main/java/com/bipros/project/api/MonthlyBoqProjectionController.java`

- [ ] **Step 1: Write the controller**

```java
package com.bipros.project.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.application.dto.CreateMonthlyBoqProjectionRequest;
import com.bipros.project.application.dto.MonthlyBoqProjectionResponse;
import com.bipros.project.application.service.MonthlyBoqProjectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects/{projectId}/boq-projections")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MonthlyBoqProjectionController {

  private final MonthlyBoqProjectionService service;

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','COST_ENGINEER')")
  public ResponseEntity<ApiResponse<MonthlyBoqProjectionResponse>> create(
      @PathVariable UUID projectId, @Valid @RequestBody CreateMonthlyBoqProjectionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(service.create(projectId, request)));
  }

  @PostMapping("/bulk")
  @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','COST_ENGINEER')")
  public ResponseEntity<ApiResponse<List<MonthlyBoqProjectionResponse>>> createBulk(
      @PathVariable UUID projectId, @Valid @RequestBody List<CreateMonthlyBoqProjectionRequest> requests) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(service.createBulk(projectId, requests)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','COST_ENGINEER')")
  public ResponseEntity<ApiResponse<MonthlyBoqProjectionResponse>> update(
      @PathVariable UUID projectId, @PathVariable UUID id,
      @Valid @RequestBody CreateMonthlyBoqProjectionRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(service.update(projectId, id, request)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<MonthlyBoqProjectionResponse>>> list(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String yearMonth) {
    return ResponseEntity.ok(ApiResponse.ok(service.list(projectId, yearMonth)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<MonthlyBoqProjectionResponse>> get(
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

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-project -am compile -q
git add backend/bipros-project/src/main/java/com/bipros/project/api/MonthlyBoqProjectionController.java
git commit -m "feat(project): expose MonthlyBoqProjection CRUD endpoints"
```

---

## Task 8: `DprMatrixReport` DTO

**Files:**
- Create: `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/dto/DprMatrixReport.java`

- [ ] **Step 1: Write the DTO**

```java
package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DPR (Daily Progress Report) matrix — one wide row per BOQ item with per-day achieved
 * quantities + monthly Projection vs Achieved totals. Mirrors the Excel "DPR" sheet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DprMatrixReport(
    UUID projectId,
    String yearMonth,        // YYYY-MM
    int daysInMonth,
    String chapter,          // null = all chapters
    List<Row> rows,
    Totals totals
) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Row(
      String boqItemNo,
      String description,
      String unit,
      BigDecimal revisedRate,    // boq_items.budgeted_rate (or actual_rate if you prefer)
      Money projection,          // null when no MonthlyBoqProjection row exists
      Money achieved,
      List<BigDecimal> daily     // length == daysInMonth, zero-filled
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Money(
      BigDecimal qty,
      BigDecimal amount
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Totals(
      Money projection,
      Money achieved,
      List<BigDecimal> daily
  ) {}
}
```

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-reporting -am compile -q
git add backend/bipros-reporting/src/main/java/com/bipros/reporting/application/dto/DprMatrixReport.java
git commit -m "feat(reporting): add DprMatrixReport DTO"
```

---

## Task 9: `DprMatrixReportService`

**Files:**
- Create: `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/service/DprMatrixReportService.java`

- [ ] **Step 1: Write the service**

```java
package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.DprMatrixReport;
import com.bipros.reporting.application.dto.DprMatrixReport.Money;
import com.bipros.reporting.application.dto.DprMatrixReport.Row;
import com.bipros.reporting.application.dto.DprMatrixReport.Totals;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DprMatrixReportService {

  @PersistenceContext private EntityManager em;

  @Transactional(readOnly = true)
  public DprMatrixReport build(UUID projectId, String yearMonth, String chapter) {
    YearMonth ym = YearMonth.parse(yearMonth);
    LocalDate from = ym.atDay(1);
    LocalDate to = ym.atEndOfMonth();
    int daysInMonth = ym.lengthOfMonth();

    // 1) BOQ items + projection (one row per item).
    String boqSql = "SELECT b.item_no, b.description, b.unit, b.budgeted_rate, b.actual_rate, "
        + "  COALESCE(mp.planned_qty, 0)::numeric AS proj_qty, "
        + "  COALESCE(mp.planned_amount, 0)::numeric AS proj_amt "
        + "FROM project.boq_items b "
        + "LEFT JOIN project.monthly_boq_projections mp "
        + "  ON mp.project_id = b.project_id AND mp.boq_item_no = b.item_no AND mp.year_month = :yearMonth "
        + "WHERE b.project_id = :projectId "
        + (chapter != null && !chapter.isBlank() ? "  AND b.chapter = :chapter " : "")
        + "ORDER BY b.item_no";
    var boqQuery = em.createNativeQuery(boqSql)
        .setParameter("projectId", projectId)
        .setParameter("yearMonth", yearMonth);
    if (chapter != null && !chapter.isBlank()) {
      boqQuery.setParameter("chapter", chapter);
    }
    @SuppressWarnings("unchecked")
    List<Object[]> boqRaw = boqQuery.getResultList();

    // 2) Daily progress aggregated by (item_no, day-of-month).
    String dprSql = "SELECT dpr.boq_item_no, EXTRACT(DAY FROM dpr.report_date)::int AS dom, "
        + "  COALESCE(SUM(dpr.qty_executed), 0)::numeric AS qty "
        + "FROM project.daily_progress_reports dpr "
        + "WHERE dpr.project_id = :projectId "
        + "  AND dpr.report_date BETWEEN :fromDate AND :toDate "
        + "  AND dpr.boq_item_no IS NOT NULL "
        + "GROUP BY dpr.boq_item_no, dom";
    @SuppressWarnings("unchecked")
    List<Object[]> dprRaw = em.createNativeQuery(dprSql)
        .setParameter("projectId", projectId)
        .setParameter("fromDate", from)
        .setParameter("toDate", to)
        .getResultList();

    // 3) Index daily by item_no
    Map<String, BigDecimal[]> dailyByItem = new LinkedHashMap<>();
    for (Object[] r : dprRaw) {
      String itemNo = (String) r[0];
      int dom = ((Number) r[1]).intValue();
      BigDecimal qty = (BigDecimal) r[2];
      BigDecimal[] arr = dailyByItem.computeIfAbsent(itemNo, k -> zeroArr(daysInMonth));
      if (dom >= 1 && dom <= daysInMonth) arr[dom - 1] = arr[dom - 1].add(qty);
    }

    // 4) Assemble rows + totals
    List<Row> rows = new ArrayList<>(boqRaw.size());
    BigDecimal[] columnTotals = zeroArr(daysInMonth);
    BigDecimal totalProjQty = BigDecimal.ZERO, totalProjAmt = BigDecimal.ZERO;
    BigDecimal totalAchQty = BigDecimal.ZERO, totalAchAmt = BigDecimal.ZERO;

    for (Object[] r : boqRaw) {
      String itemNo = (String) r[0];
      String description = (String) r[1];
      String unit = (String) r[2];
      BigDecimal budgetedRate = (BigDecimal) r[3];
      BigDecimal actualRate = (BigDecimal) r[4];
      BigDecimal projQty = (BigDecimal) r[5];
      BigDecimal projAmt = (BigDecimal) r[6];

      BigDecimal[] daily = dailyByItem.getOrDefault(itemNo, zeroArr(daysInMonth));
      BigDecimal achQty = sum(daily);
      BigDecimal rate = actualRate != null ? actualRate : (budgetedRate != null ? budgetedRate : BigDecimal.ZERO);
      BigDecimal achAmt = achQty.multiply(rate).setScale(2, RoundingMode.HALF_UP);

      Money projection = (projQty.signum() > 0 || projAmt.signum() > 0) ? new Money(projQty, projAmt) : null;
      rows.add(new Row(itemNo, description, unit, budgetedRate,
          projection, new Money(achQty, achAmt), List.of(daily)));

      for (int i = 0; i < daysInMonth; i++) {
        columnTotals[i] = columnTotals[i].add(daily[i]);
      }
      if (projection != null) { totalProjQty = totalProjQty.add(projQty); totalProjAmt = totalProjAmt.add(projAmt); }
      totalAchQty = totalAchQty.add(achQty); totalAchAmt = totalAchAmt.add(achAmt);
    }

    Totals totals = new Totals(
        new Money(totalProjQty, totalProjAmt),
        new Money(totalAchQty, totalAchAmt),
        List.of(columnTotals));

    return new DprMatrixReport(projectId, yearMonth, daysInMonth,
        chapter == null || chapter.isBlank() ? null : chapter, rows, totals);
  }

  private static BigDecimal[] zeroArr(int n) {
    BigDecimal[] a = new BigDecimal[n];
    for (int i = 0; i < n; i++) a[i] = BigDecimal.ZERO;
    return a;
  }

  private static BigDecimal sum(BigDecimal[] a) {
    BigDecimal s = BigDecimal.ZERO;
    for (BigDecimal v : a) s = s.add(v);
    return s;
  }
}
```

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-reporting -am compile -q
git add backend/bipros-reporting/src/main/java/com/bipros/reporting/application/service/DprMatrixReportService.java
git commit -m "feat(reporting): add DprMatrixReportService (BOQ × calendar + projection vs achieved)"
```

---

## Task 10: Wire endpoint in `ReportController`

**Files:**
- Modify: `backend/bipros-reporting/src/main/java/com/bipros/reporting/presentation/controller/ReportController.java`

- [ ] **Step 1: Inject + add endpoint**

Add to constructor field list:
```java
  private final DprMatrixReportService dprMatrixReportService;
```
Add the import.

Add the endpoint method:
```java
  @GetMapping("/dpr-matrix")
  public ApiResponse<DprMatrixReport> getDprMatrix(
      @RequestParam UUID projectId,
      @RequestParam String yearMonth,
      @RequestParam(required = false) String chapter) {
    return ApiResponse.ok(dprMatrixReportService.build(projectId, yearMonth, chapter));
  }
```
Add the import for `DprMatrixReport`.

- [ ] **Step 2: Compile + commit**

```bash
cd backend && mvn -pl bipros-reporting -am compile -q
git add backend/bipros-reporting/src/main/java/com/bipros/reporting/presentation/controller/ReportController.java
git commit -m "feat(reporting): expose GET /v1/reports/dpr-matrix"
```

---

## Task 11: Backend integration test

**Files:**
- Create: `backend/bipros-api/src/test/java/com/bipros/api/integration/DprMatrixIntegrationTest.java`

- [ ] **Step 1: Write the test**

Use the `localit` profile pattern (Plan 1 / Task 14). Seed:
- 1 project, 2 BOQ items (`1.1` description "Earthwork", unit "Cum", budgeted_rate=100; `1.2` description "Concrete", unit "Cum", budgeted_rate=500)
- 1 monthly projection row for item `1.1` (yearMonth=current, planned_qty=50, planned_amount=5000)
- 3 daily progress reports: 1st of month item `1.1` qty=10, 2nd item `1.1` qty=15, 3rd item `1.2` qty=2

Then assert:
- `report.rows.size() == 2`
- Row for `1.1`: `projection.qty=50`, `projection.amount=5000`, `achieved.qty=25`, `achieved.amount=2500`, `daily[0]=10`, `daily[1]=15`, `daily[2]=0`
- Row for `1.2`: `projection == null`, `achieved.qty=2`, `achieved.amount=1000`, `daily[2]=2`
- `totals.daily[0]=10`, `totals.daily[1]=15`, `totals.daily[2]=2`
- `totals.achieved.amount=3500` (2500 + 1000)

Plus a `chapter` filter test: tag both BOQ items with `chapter="1 - Earthwork"`, then call with `chapter="1 - Earthwork"` → expect both; with `chapter="9 - Other"` → expect 0 rows.

- [ ] **Step 2: Run + commit**

```bash
cd backend && mvn -pl bipros-api -am test -Dtest=DprMatrixIntegrationTest -q
git add backend/bipros-api/src/test/java/com/bipros/api/integration/DprMatrixIntegrationTest.java
git commit -m "test(api): integration test for dpr-matrix endpoint"
```
Expected: 2 tests pass.

---

## Task 12: Frontend API clients

**Files:**
- Create: `frontend/src/lib/api/dprMatrixApi.ts`
- Create: `frontend/src/lib/api/monthlyBoqProjectionApi.ts`

- [ ] **Step 1: Write `dprMatrixApi.ts`**

```typescript
import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface Money { qty: number | null; amount: number | null; }
export interface DprMatrixRow {
  boqItemNo: string;
  description: string;
  unit: string;
  revisedRate: number | null;
  projection: Money | null;
  achieved: Money;
  daily: number[];
}
export interface DprMatrixReport {
  projectId: string;
  yearMonth: string;
  daysInMonth: number;
  chapter: string | null;
  rows: DprMatrixRow[];
  totals: { projection: Money; achieved: Money; daily: number[] };
}
export interface GetDprMatrixParams {
  projectId: string;
  yearMonth: string;
  chapter?: string;
}
export const dprMatrixApi = {
  get: (params: GetDprMatrixParams) => {
    const qs = [`projectId=${params.projectId}`, `yearMonth=${params.yearMonth}`];
    if (params.chapter) qs.push(`chapter=${encodeURIComponent(params.chapter)}`);
    return apiClient
      .get<ApiResponse<DprMatrixReport>>(`/v1/reports/dpr-matrix?${qs.join("&")}`)
      .then((r) => r.data);
  },
};
```

- [ ] **Step 2: Write `monthlyBoqProjectionApi.ts`**

```typescript
import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface MonthlyBoqProjectionResponse {
  id: string;
  projectId: string;
  boqItemNo: string;
  yearMonth: string;
  plannedQty: number;
  plannedAmount: number;
  remarks: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface CreateMonthlyBoqProjectionRequest {
  boqItemNo: string;
  yearMonth: string;        // YYYY-MM
  plannedQty: number;
  remarks?: string | null;
}

export const monthlyBoqProjectionApi = {
  list: (projectId: string, yearMonth?: string) => {
    const qs = yearMonth ? `?yearMonth=${yearMonth}` : "";
    return apiClient
      .get<ApiResponse<MonthlyBoqProjectionResponse[]>>(`/v1/projects/${projectId}/boq-projections${qs}`)
      .then((r) => r.data);
  },
  create: (projectId: string, request: CreateMonthlyBoqProjectionRequest) =>
    apiClient
      .post<ApiResponse<MonthlyBoqProjectionResponse>>(`/v1/projects/${projectId}/boq-projections`, request)
      .then((r) => r.data),
  createBulk: (projectId: string, requests: CreateMonthlyBoqProjectionRequest[]) =>
    apiClient
      .post<ApiResponse<MonthlyBoqProjectionResponse[]>>(`/v1/projects/${projectId}/boq-projections/bulk`, requests)
      .then((r) => r.data),
  update: (projectId: string, id: string, request: CreateMonthlyBoqProjectionRequest) =>
    apiClient
      .put<ApiResponse<MonthlyBoqProjectionResponse>>(`/v1/projects/${projectId}/boq-projections/${id}`, request)
      .then((r) => r.data),
  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/boq-projections/${id}`),
};
```

- [ ] **Step 3: Type-check + commit**

```bash
cd frontend && pnpm exec tsc --noEmit
git add frontend/src/lib/api/dprMatrixApi.ts frontend/src/lib/api/monthlyBoqProjectionApi.ts
git commit -m "feat(frontend): API clients for dpr-matrix + monthly-boq-projection"
```

---

## Task 13: BOQ projections form page

**Files:**
- Create: `frontend/src/app/(app)/projects/[projectId]/boq-projections/page.tsx`

- [ ] **Step 1: Write the page**

A list view filtered by month + an "Add Projection" form (BOQ item picker via `boqApi.list(projectId)`, year-month picker, planned-qty input, remarks). Plus a "Bulk Paste" textarea for entering many rows at once (one row per line, format: `itemNo<TAB>plannedQty[<TAB>remarks]`). On submit, call `monthlyBoqProjectionApi.createBulk`.

Same Tailwind tokens as `daily-outputs/page.tsx`. Reuse `SearchableSelect` for the BOQ item picker.

Skeleton:
```tsx
"use client";
import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  monthlyBoqProjectionApi,
  type CreateMonthlyBoqProjectionRequest,
} from "@/lib/api/monthlyBoqProjectionApi";
import { boqApi } from "@/lib/api/boqApi";
import { TabTip } from "@/components/common/TabTip";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";
// ... full skeleton mirroring daily-outputs/page.tsx
```

- [ ] **Step 2: Type-check + commit**

```bash
cd frontend && pnpm exec tsc --noEmit
git add frontend/src/app/\(app\)/projects/\[projectId\]/boq-projections/page.tsx
git commit -m "feat(frontend): add BOQ projections CRUD page (drives DPR matrix Projection columns)"
```

---

## Task 14: DPR matrix viewer page

**Files:**
- Create: `frontend/src/app/(app)/projects/[projectId]/dpr-matrix/page.tsx`

- [ ] **Step 1: Write the page**

Wide table: sticky-left columns (Item No, Description, Unit, Rate, Projection Qty, Projection Amt, Achieved Qty, Achieved Amt), then 31 day columns, then a Total column. Filters above: month picker (default current), chapter filter (free text or dropdown if chapters can be enumerated from `boqApi`).

Skeleton:
```tsx
"use client";
import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { dprMatrixApi } from "@/lib/api/dprMatrixApi";
import { TabTip } from "@/components/common/TabTip";

const currentMonth = () => new Date().toISOString().slice(0, 7);

export default function DprMatrixPage() {
  const { projectId } = useParams() as { projectId: string };
  const [yearMonth, setYearMonth] = useState(currentMonth());
  const [chapter, setChapter] = useState("");

  const { data, isLoading } = useQuery({
    queryKey: ["dpr-matrix", projectId, yearMonth, chapter],
    queryFn: () => dprMatrixApi.get({ projectId, yearMonth, chapter: chapter || undefined }),
  });

  const report = data?.data;
  if (isLoading) return <div className="p-6 text-text-muted">Loading…</div>;
  if (!report) return <div className="p-6 text-danger">Failed to load DPR.</div>;

  const dayHeaders = Array.from({ length: report.daysInMonth }, (_, i) => i + 1);

  return (
    <div className="p-6">
      <TabTip title="Daily Progress Report (Matrix)" description="BOQ items vs per-day quantity executed; with monthly Projection vs Achieved totals." />
      <div className="mb-4 flex gap-3 items-end">
        <div>
          <label className="block text-sm text-text-secondary mb-1">Month</label>
          <input type="month" value={yearMonth} onChange={(e) => setYearMonth(e.target.value)}
            className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg" />
        </div>
        <div>
          <label className="block text-sm text-text-secondary mb-1">Chapter (optional)</label>
          <input type="text" value={chapter} onChange={(e) => setChapter(e.target.value)}
            placeholder="e.g. 1 - Earthwork"
            className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg" />
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="border-collapse border border-border text-sm">
          <thead>
            <tr className="bg-surface/80 text-text-secondary">
              <th className="border border-border px-2 py-1 sticky left-0 bg-surface/80 text-left">Item No</th>
              <th className="border border-border px-2 py-1 text-left">Description</th>
              <th className="border border-border px-2 py-1 text-left">Unit</th>
              <th className="border border-border px-2 py-1 text-right">Rate</th>
              <th className="border border-border px-2 py-1 text-right">Proj Qty</th>
              <th className="border border-border px-2 py-1 text-right">Proj Amt</th>
              <th className="border border-border px-2 py-1 text-right">Ach Qty</th>
              <th className="border border-border px-2 py-1 text-right">Ach Amt</th>
              {dayHeaders.map((d) => <th key={d} className="border border-border px-2 py-1 text-right">{d}</th>)}
            </tr>
          </thead>
          <tbody>
            {report.rows.map((r) => (
              <tr key={r.boqItemNo} className="hover:bg-surface-hover/30 text-text-primary">
                <td className="border border-border px-2 py-1 sticky left-0 bg-surface font-mono">{r.boqItemNo}</td>
                <td className="border border-border px-2 py-1">{r.description}</td>
                <td className="border border-border px-2 py-1">{r.unit}</td>
                <td className="border border-border px-2 py-1 text-right">{r.revisedRate ?? "—"}</td>
                <td className="border border-border px-2 py-1 text-right">{r.projection?.qty ?? "—"}</td>
                <td className="border border-border px-2 py-1 text-right">{r.projection?.amount ?? "—"}</td>
                <td className="border border-border px-2 py-1 text-right">{r.achieved.qty}</td>
                <td className="border border-border px-2 py-1 text-right">{r.achieved.amount}</td>
                {r.daily.map((v, i) => <td key={i} className="border border-border px-2 py-1 text-right">{v ? v : ""}</td>)}
              </tr>
            ))}
            <tr className="font-semibold bg-surface/50 text-text-primary">
              <td colSpan={4} className="border border-border px-2 py-1 sticky left-0 bg-surface/50">Totals</td>
              <td className="border border-border px-2 py-1 text-right">{report.totals.projection.qty}</td>
              <td className="border border-border px-2 py-1 text-right">{report.totals.projection.amount}</td>
              <td className="border border-border px-2 py-1 text-right">{report.totals.achieved.qty}</td>
              <td className="border border-border px-2 py-1 text-right">{report.totals.achieved.amount}</td>
              {report.totals.daily.map((v, i) => <td key={i} className="border border-border px-2 py-1 text-right">{v ? v : ""}</td>)}
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Type-check + commit**

```bash
cd frontend && pnpm exec tsc --noEmit
git add frontend/src/app/\(app\)/projects/\[projectId\]/dpr-matrix/page.tsx
git commit -m "feat(frontend): add DPR matrix viewer page (BOQ × calendar + projection vs achieved)"
```

---

## Task 15: Add tabs to project layout

**Files:**
- Modify: `frontend/src/app/(app)/projects/[projectId]/layout.tsx`

- [ ] **Step 1: Add the two tabs**

Find the existing `tabs` array and append:
```tsx
{ id: "dpr-matrix",      label: "DPR (Matrix)",     href: `/projects/${projectId}/dpr-matrix` },
{ id: "boq-projections", label: "BOQ Projections",  href: `/projects/${projectId}/boq-projections` },
```

- [ ] **Step 2: Type-check + commit**

```bash
cd frontend && pnpm exec tsc --noEmit
git add frontend/src/app/\(app\)/projects/\[projectId\]/layout.tsx
git commit -m "feat(frontend): add DPR Matrix + BOQ Projections tabs to project nav"
```

---

## Task 16: Playwright e2e

**Files:**
- Create: `frontend/e2e/tests/23-dpr-matrix.spec.ts`

- [ ] **Step 1: Write the e2e**

```ts
import { test, expect } from "../fixtures/auth.fixture";

test.describe("DPR Matrix", () => {
  test("page renders with month picker, chapter filter, and table", async ({ authenticatedPage: page }) => {
    await page.goto("/projects");
    await expect(page.getByRole("heading", { name: /projects/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    const projectLink = page.locator('a[href^="/projects/"]:not([href$="/new"])').first();
    await projectLink.click();
    await expect(page).toHaveURL(/\/projects\/[0-9a-f-]+/);
    await page.goto(page.url().replace(/\/projects\/([0-9a-f-]+).*/, "/projects/$1/dpr-matrix"));
    await expect(page.getByRole("heading", { name: /Daily Progress Report/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('input[type="month"]')).toBeVisible();
    await expect(page.getByPlaceholder(/Earthwork/i)).toBeVisible();
    await expect(page.getByText(/Item No/i).first()).toBeVisible();
  });
});
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/tests/23-dpr-matrix.spec.ts
git commit -m "test(frontend): e2e for dpr-matrix page"
```

---

## Task 17: Final verification

- [ ] **Step 1: Backend tests**

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
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/v1/reports/dpr-matrix?projectId=$PROJECT_ID&yearMonth=$MONTH" \
  | jq '.data | { yearMonth, daysInMonth, rowCount: (.rows|length), totalAchieved: .totals.achieved.amount }'
```
Expected: a JSON object with the row count and total achieved amount.

---

## Self-Review Notes

**Spec coverage** (against `docs/superpowers/specs/2026-04-27-capacity-utilization-reports-design.md` §2.3 + §5):
- §2.3 (`MonthlyBoqProjection` entity + CRUD) → Tasks 1–7
- §5.1 (matrix endpoint) → Tasks 8–10
- §5.2 (DTO shape) → Task 8
- §5.3 (service with native query) → Task 9
- §5.4 (frontend page) → Task 14, plus the projection-entry page (Task 13) and the new tabs (Task 15)
- Testing → Tasks 6, 11, 16, 17

**Type consistency:**
- `Money { qty, amount }` shape matches across DTO (Task 8), service (Task 9), API client (Task 12), and page (Task 14).
- `yearMonth` format `"YYYY-MM"` enforced in request DTO via `@Pattern` (Task 4) and via `<input type="month">` in the form (Task 13/14).
- Liquibase changeset id `048-...` follows the `<seq>-<descriptive-name>` convention.
- Repository finder method names follow Spring Data derivation; verified by reading them aloud.
