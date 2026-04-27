# Capacity-Utilization Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing `/v1/reports/capacity-utilization` endpoint and the `/projects/:id/capacity-utilization` page to reproduce all columns of the Excel "Plant utilization", "Manpower utilization", and "SUMMARY" sheets — adding project-scoped productivity-norm overrides ("site norm" column), a per-supervisor split, and a SUMMARY-level roll-up with AVERAGE row.

**Architecture:** Three orthogonal additions hung off the existing `CapacityUtilizationReportService`: (1) a nullable `project_id` on `productivity_norms` plus an extended 5-step lookup chain; (2) new `supervisor_id` + `supervisor_name` columns on `daily_activity_resource_outputs` so the report can group by supervisor; (3) a `level=DETAIL|SUMMARY` parameter and `bySupervisor=true` flag on the existing endpoint. All DTO additions are nullable / optional so existing callers stay green.

**Tech Stack:** Java 23 / Spring Boot 3.5, JPA + Liquibase, native SQL through the shared `EntityManager`, JUnit 5, Spring MVC test, Next.js 16 / React 19 / TanStack Query, axios shared client.

**Spec:** `docs/superpowers/specs/2026-04-27-capacity-utilization-reports-design.md` (sections 2.1, 2.2, 3.x).

**Out of scope (covered by separate plans):** Daily-Deployment matrix endpoint (Plan 2); DPR matrix endpoint (Plan 3); Oman seed scripts (Plan 4); the new entities `MonthlyBoqProjection` and `DailyResourcePlan` (Plans 2 & 3).

---

## File-touch summary

**Backend (Java) — created:**
- `backend/bipros-api/src/main/resources/db/changelog/045-productivity-norm-add-project-id.yaml`
- `backend/bipros-api/src/main/resources/db/changelog/046-dar-add-supervisor.yaml`
- `backend/bipros-resource/src/test/java/com/bipros/resource/domain/service/ProductivityNormLookupServiceTest.java`
- `backend/bipros-api/src/test/java/com/bipros/api/integration/CapacityUtilizationReportIntegrationTest.java`

**Backend (Java) — modified:**
- `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml` — register the 2 new changesets
- `backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/ProductivityNorm.java` — add `projectId`
- `backend/bipros-resource/src/main/java/com/bipros/resource/domain/repository/ProductivityNormRepository.java` — 2 new finder methods
- `backend/bipros-resource/src/main/java/com/bipros/resource/domain/service/ProductivityNormLookupService.java` — 5-step chain + `siteOutputPerDay` exposure
- `backend/bipros-resource/src/main/java/com/bipros/resource/domain/service/ResolvedNorm.java` — `siteOutputPerDay` field + 2 new `Source` enum values
- `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/CreateProductivityNormRequest.java` — accept `projectId`
- `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/ProductivityNormResponse.java` — expose `projectId`
- `backend/bipros-resource/src/main/java/com/bipros/resource/application/service/ProductivityNormService.java` — persist `projectId`
- `backend/bipros-project/src/main/java/com/bipros/project/domain/model/DailyActivityResourceOutput.java` — supervisor fields
- `backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateDailyActivityResourceOutputRequest.java` — supervisor fields
- `backend/bipros-project/src/main/java/com/bipros/project/application/dto/DailyActivityResourceOutputResponse.java` — supervisor fields
- `backend/bipros-project/src/main/java/com/bipros/project/application/service/DailyActivityResourceOutputService.java` — persist supervisor fields
- `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/dto/CapacityUtilizationReport.java` — `Row.kind`, `Budgeted.siteOutputPerDay`, `Period.bySupervisor`
- `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/service/CapacityUtilizationReportService.java` — supervisor split, site-norm, SUMMARY/AVERAGE, supervisorId filter
- `backend/bipros-reporting/src/main/java/com/bipros/reporting/presentation/controller/ReportController.java` — new query params

**Frontend (TypeScript) — modified:**
- `frontend/src/lib/api/dailyActivityResourceOutputApi.ts` — add supervisor fields to create payload + response
- `frontend/src/lib/api/capacityUtilizationApi.ts` — new params + DTO fields
- `frontend/src/app/(app)/projects/[projectId]/capacity-utilization/page.tsx` — Detail/Summary toggle, by-supervisor checkbox, site-norm column, AVERAGE row, supervisor sub-columns, expanded CSV

**Frontend — created:**
- `frontend/tests/e2e/capacity-utilization-extension.spec.ts`

---

## Task 1: Liquibase changeset 045 — add `project_id` to `productivity_norms`

**Files:**
- Create: `backend/bipros-api/src/main/resources/db/changelog/045-productivity-norm-add-project-id.yaml`
- Modify: `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Write the changeset**

Create `backend/bipros-api/src/main/resources/db/changelog/045-productivity-norm-add-project-id.yaml`:
```yaml
databaseChangeLog:
  - changeSet:
      id: 045-productivity-norm-add-project-id
      author: bipros
      changes:
        - addColumn:
            schemaName: resource
            tableName: productivity_norms
            columns:
              - column:
                  name: project_id
                  type: UUID
                  # Nullable: NULL = global default norm; non-NULL = project-specific override
        - createIndex:
            schemaName: resource
            tableName: productivity_norms
            indexName: idx_prod_norm_project
            columns:
              - column:
                  name: project_id
```

- [ ] **Step 2: Register in master**

Append to `db.changelog-master.yaml`:
```yaml
  - include:
      file: db/changelog/045-productivity-norm-add-project-id.yaml
```

- [ ] **Step 3: Verify the changeset applies**

Boot backend in `prod` profile (the only profile that runs Liquibase). With dev profile, `ddl-auto: create-drop` rebuilds the schema and the entity change in Task 2 is enough to add the column. Verify by booting the backend and checking the `resource.productivity_norms` table has the new column:
```bash
docker exec -i bipros-postgres psql -U bipros -d bipros -c "\d resource.productivity_norms" | grep project_id
```
Expected: a row showing `project_id | uuid`.

- [ ] **Step 4: Commit**

```bash
git add backend/bipros-api/src/main/resources/db/changelog/045-productivity-norm-add-project-id.yaml backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat(resource): add project_id column to productivity_norms for project-scoped norm overrides"
```

---

## Task 2: Add `projectId` to `ProductivityNorm` entity

**Files:**
- Modify: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/ProductivityNorm.java`

- [ ] **Step 1: Add the field**

After the `@Index(name = "idx_prod_norm_resource", ...)` line in the `@Table(indexes = {...})` block, add a new index entry:
```java
        @Index(name = "idx_prod_norm_project", columnList = "project_id")
```

After the `private Resource resource;` field (around line 63), add:
```java
  /**
   * Optional project scope. When set, this norm overrides any global (NULL) norm for the same
   * (workActivity × resource-or-type) within this project. Resolution chain in
   * {@code ProductivityNormLookupService}: project+resource → project+type → global+resource →
   * global+type → {@code Resource.standardOutputPerDay}.
   */
  @Column(name = "project_id")
  private UUID projectId;
```

Add the import `import java.util.UUID;` if not already present.

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-resource -am compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/ProductivityNorm.java
git commit -m "feat(resource): add ProductivityNorm.projectId for project-scoped overrides"
```

---

## Task 3: Add project-scoped finder methods to `ProductivityNormRepository`

**Files:**
- Modify: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/repository/ProductivityNormRepository.java`

- [ ] **Step 1: Add finder methods**

Inside the interface body, add:
```java
  /** Step 1 of project-scoped resolution: project + specific resource. */
  Optional<ProductivityNorm> findFirstByWorkActivityIdAndResourceIdAndProjectId(
      UUID workActivityId, UUID resourceId, UUID projectId);

  /** Step 2 of project-scoped resolution: project + resource type (no specific resource). */
  Optional<ProductivityNorm> findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectId(
      UUID workActivityId, UUID resourceTypeDefId, UUID projectId);

  /**
   * Step 3 of project-scoped resolution: global (project_id IS NULL) + specific resource.
   * Existing {@link #findFirstByWorkActivityIdAndResourceId} returned the first match regardless
   * of project_id; this variant explicitly requires NULL project_id so a project-specific override
   * doesn't accidentally satisfy a "global" lookup.
   */
  Optional<ProductivityNorm> findFirstByWorkActivityIdAndResourceIdAndProjectIdIsNull(
      UUID workActivityId, UUID resourceId);

  /** Step 4 of project-scoped resolution: global type-level (project_id IS NULL). */
  Optional<ProductivityNorm> findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectIdIsNull(
      UUID workActivityId, UUID resourceTypeDefId);
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-resource -am compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/repository/ProductivityNormRepository.java
git commit -m "feat(resource): add project-scoped finder methods to ProductivityNormRepository"
```

---

## Task 4: Extend `ResolvedNorm` with `siteOutputPerDay` + new Source values

**Files:**
- Modify: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/service/ResolvedNorm.java`

- [ ] **Step 1: Add field + enum values**

Replace the entire record with:
```java
package com.bipros.resource.domain.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of resolving the productivity norm that applies to a given (work activity, resource)
 * pair. Records the source of the value so callers can show provenance and audit logic.
 *
 * <p>{@code outputPerDay} is the value used as the planning norm (denominator for
 * "% utilization"). {@code siteOutputPerDay} is the project-scoped override when one exists —
 * surfaced separately so reports can render the spreadsheet's two-norm columns side-by-side.
 * When no project override exists, {@code siteOutputPerDay == null}.
 */
public record ResolvedNorm(
    BigDecimal outputPerDay,
    BigDecimal siteOutputPerDay,
    String unit,
    Source source,
    UUID productivityNormId,
    UUID workActivityId,
    UUID resourceId
) {
  public enum Source {
    /** Project-scoped override on the specific resource. */
    PROJECT_SPECIFIC_RESOURCE,
    /** Project-scoped override on the resource type. */
    PROJECT_SPECIFIC_TYPE,
    /** Global (project_id IS NULL) {@code ProductivityNorm} row scoped to the specific resource. */
    SPECIFIC_RESOURCE,
    /** Global (project_id IS NULL) {@code ProductivityNorm} row scoped to the resource type. */
    RESOURCE_TYPE,
    /** Fell back to {@code Resource.standardOutputPerDay}. */
    RESOURCE_LEGACY,
    /** No norm could be resolved for the inputs. */
    NONE
  }

  public static ResolvedNorm none(UUID workActivityId, UUID resourceId) {
    return new ResolvedNorm(null, null, null, Source.NONE, null, workActivityId, resourceId);
  }
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-resource -am compile -q
```
Expected: compile error in `ProductivityNormLookupService` because the constructor signature changed. That's intentional — Task 5 fixes it.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/service/ResolvedNorm.java
git commit -m "feat(resource): add ResolvedNorm.siteOutputPerDay + project-scoped Source enum values"
```

---

## Task 5: Extend `ProductivityNormLookupService` with the 5-step chain

**Files:**
- Modify: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/service/ProductivityNormLookupService.java`

- [ ] **Step 1: Update Javadoc and add new resolve overload**

Replace the class-level Javadoc:
```java
/**
 * Resolves the effective productivity norm for a {@code (workActivity, resource[, project])} pair.
 *
 * <p>Project-scoped fallback order (when {@code projectId} is non-null):
 * <ol>
 *   <li>project + specific resource</li>
 *   <li>project + resource type</li>
 *   <li>global resource ({@code project_id IS NULL})</li>
 *   <li>global resource type ({@code project_id IS NULL})</li>
 *   <li>{@code Resource.standardOutputPerDay} (legacy denormalised default)</li>
 *   <li>empty</li>
 * </ol>
 *
 * <p>When {@code projectId} is null, steps 1 and 2 are skipped — global-only lookup. The legacy
 * 2-arg {@link #resolve(UUID, UUID)} delegates here with {@code projectId == null} so existing
 * callers behave identically.
 */
```

Replace the `resolve(UUID workActivityId, UUID resourceId)` method body, and add the new 3-arg overload:
```java
  public ResolvedNorm resolve(UUID workActivityId, UUID resourceId) {
    return resolve(workActivityId, resourceId, null);
  }

  public ResolvedNorm resolve(UUID workActivityId, UUID resourceId, UUID projectId) {
    if (workActivityId == null) {
      return ResolvedNorm.none(null, resourceId);
    }
    Resource resource = resourceId == null ? null : resourceRepository.findById(resourceId).orElse(null);
    if (resource == null) {
      return ResolvedNorm.none(workActivityId, resourceId);
    }

    BigDecimal siteOverride = null;  // populated when a project-specific norm exists

    // 1) project + specific resource
    if (projectId != null) {
      Optional<ProductivityNorm> hit =
          normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectId(workActivityId, resource.getId(), projectId);
      if (hit.isPresent()) {
        siteOverride = hit.get().getOutputPerDay();
        return materialise(hit.get(), siteOverride, ResolvedNorm.Source.PROJECT_SPECIFIC_RESOURCE, resource.getId());
      }
    }

    // 2) project + resource type
    ResourceTypeDef def = resource.getResourceTypeDef();
    if (projectId != null && def != null) {
      Optional<ProductivityNorm> hit = normRepository
          .findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectId(workActivityId, def.getId(), projectId);
      if (hit.isPresent()) {
        siteOverride = hit.get().getOutputPerDay();
        return materialise(hit.get(), siteOverride, ResolvedNorm.Source.PROJECT_SPECIFIC_TYPE, resource.getId());
      }
    }

    // 3) global resource
    Optional<ProductivityNorm> globalSpecific =
        normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectIdIsNull(workActivityId, resource.getId());
    if (globalSpecific.isPresent()) {
      return materialise(globalSpecific.get(), null, ResolvedNorm.Source.SPECIFIC_RESOURCE, resource.getId());
    }

    // 4) global type
    if (def != null) {
      Optional<ProductivityNorm> globalType = normRepository
          .findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectIdIsNull(workActivityId, def.getId());
      if (globalType.isPresent()) {
        return materialise(globalType.get(), null, ResolvedNorm.Source.RESOURCE_TYPE, resource.getId());
      }
    }

    // 5) legacy
    if (resource.getStandardOutputPerDay() != null) {
      return new ResolvedNorm(
          BigDecimal.valueOf(resource.getStandardOutputPerDay()),
          null,
          resource.getStandardOutputUnit(),
          ResolvedNorm.Source.RESOURCE_LEGACY,
          null,
          workActivityId,
          resource.getId());
    }
    return ResolvedNorm.none(workActivityId, resourceId);
  }
```

Replace the private `materialise` method:
```java
  private ResolvedNorm materialise(ProductivityNorm norm, BigDecimal siteOverride, ResolvedNorm.Source source, UUID resourceId) {
    return new ResolvedNorm(
        norm.getOutputPerDay(),
        siteOverride,
        norm.getUnit(),
        source,
        norm.getId(),
        norm.getWorkActivity() == null ? null : norm.getWorkActivity().getId(),
        resourceId);
  }
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-resource -am compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/service/ProductivityNormLookupService.java
git commit -m "feat(resource): extend ProductivityNormLookupService with project-scoped 5-step fallback chain"
```

---

## Task 6: Tests for the 5-step chain

**Files:**
- Create: `backend/bipros-resource/src/test/java/com/bipros/resource/domain/service/ProductivityNormLookupServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bipros.resource.domain.service;

import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.ProductivityNormType;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.model.WorkActivity;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.WorkActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductivityNormLookupServiceTest {

  @Mock ProductivityNormRepository normRepository;
  @Mock WorkActivityRepository workActivityRepository;
  @Mock ResourceRepository resourceRepository;
  @InjectMocks ProductivityNormLookupService service;

  private final UUID waId = UUID.randomUUID();
  private final UUID resId = UUID.randomUUID();
  private final UUID typeId = UUID.randomUUID();
  private final UUID projectId = UUID.randomUUID();

  private Resource resource;

  @BeforeEach
  void setUp() {
    ResourceTypeDef type = new ResourceTypeDef();
    type.setId(typeId);
    resource = new Resource();
    resource.setId(resId);
    resource.setResourceTypeDef(type);
    when(resourceRepository.findById(resId)).thenReturn(Optional.of(resource));
  }

  @Test
  void step1_projectSpecificResource_winsOverEverythingElse() {
    ProductivityNorm projectResource = norm(BigDecimal.valueOf(1500), waId, resId, null, projectId);
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectId(waId, resId, projectId))
        .thenReturn(Optional.of(projectResource));

    ResolvedNorm out = service.resolve(waId, resId, projectId);

    assertThat(out.source()).isEqualTo(ResolvedNorm.Source.PROJECT_SPECIFIC_RESOURCE);
    assertThat(out.outputPerDay()).isEqualByComparingTo("1500");
    assertThat(out.siteOutputPerDay()).isEqualByComparingTo("1500");
  }

  @Test
  void step2_projectSpecificType_whenNoResourceOverride() {
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectId(waId, resId, projectId))
        .thenReturn(Optional.empty());
    ProductivityNorm projectType = norm(BigDecimal.valueOf(1200), waId, null, typeId, projectId);
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectId(waId, typeId, projectId))
        .thenReturn(Optional.of(projectType));

    ResolvedNorm out = service.resolve(waId, resId, projectId);

    assertThat(out.source()).isEqualTo(ResolvedNorm.Source.PROJECT_SPECIFIC_TYPE);
    assertThat(out.outputPerDay()).isEqualByComparingTo("1200");
    assertThat(out.siteOutputPerDay()).isEqualByComparingTo("1200");
  }

  @Test
  void step3_globalResource_whenNoProjectOverride() {
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectId(waId, resId, projectId))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectId(any(), any(), any()))
        .thenReturn(Optional.empty());
    ProductivityNorm global = norm(BigDecimal.valueOf(900), waId, resId, null, null);
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectIdIsNull(waId, resId))
        .thenReturn(Optional.of(global));

    ResolvedNorm out = service.resolve(waId, resId, projectId);

    assertThat(out.source()).isEqualTo(ResolvedNorm.Source.SPECIFIC_RESOURCE);
    assertThat(out.outputPerDay()).isEqualByComparingTo("900");
    assertThat(out.siteOutputPerDay()).isNull();
  }

  @Test
  void step4_globalType_whenNoSpecificResource() {
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectId(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectId(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectIdIsNull(waId, resId))
        .thenReturn(Optional.empty());
    ProductivityNorm globalType = norm(BigDecimal.valueOf(800), waId, null, typeId, null);
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectIdIsNull(waId, typeId))
        .thenReturn(Optional.of(globalType));

    ResolvedNorm out = service.resolve(waId, resId, projectId);

    assertThat(out.source()).isEqualTo(ResolvedNorm.Source.RESOURCE_TYPE);
    assertThat(out.outputPerDay()).isEqualByComparingTo("800");
    assertThat(out.siteOutputPerDay()).isNull();
  }

  @Test
  void step5_legacyStandardOutputPerDay_whenNoNormExists() {
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectId(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectId(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectIdIsNull(any(), any()))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectIdIsNull(any(), any()))
        .thenReturn(Optional.empty());
    resource.setStandardOutputPerDay(700.0);
    resource.setStandardOutputUnit("Cum/day");

    ResolvedNorm out = service.resolve(waId, resId, projectId);

    assertThat(out.source()).isEqualTo(ResolvedNorm.Source.RESOURCE_LEGACY);
    assertThat(out.outputPerDay()).isEqualByComparingTo("700");
    assertThat(out.siteOutputPerDay()).isNull();
  }

  @Test
  void none_whenNothingMatches() {
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectId(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectId(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectIdIsNull(any(), any()))
        .thenReturn(Optional.empty());
    when(normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefIdAndProjectIdIsNull(any(), any()))
        .thenReturn(Optional.empty());

    ResolvedNorm out = service.resolve(waId, resId, projectId);

    assertThat(out.source()).isEqualTo(ResolvedNorm.Source.NONE);
    assertThat(out.outputPerDay()).isNull();
  }

  @Test
  void twoArgOverload_skipsProjectSteps() {
    when(normRepository.findFirstByWorkActivityIdAndResourceIdAndProjectIdIsNull(waId, resId))
        .thenReturn(Optional.of(norm(BigDecimal.valueOf(900), waId, resId, null, null)));

    ResolvedNorm out = service.resolve(waId, resId);

    assertThat(out.source()).isEqualTo(ResolvedNorm.Source.SPECIFIC_RESOURCE);
  }

  private ProductivityNorm norm(BigDecimal output, UUID waId, UUID resId, UUID typeId, UUID projectId) {
    ProductivityNorm n = new ProductivityNorm();
    n.setId(UUID.randomUUID());
    n.setNormType(ProductivityNormType.EQUIPMENT);
    WorkActivity wa = new WorkActivity();
    wa.setId(waId);
    n.setWorkActivity(wa);
    if (resId != null) {
      Resource r = new Resource();
      r.setId(resId);
      n.setResource(r);
    }
    if (typeId != null) {
      ResourceTypeDef t = new ResourceTypeDef();
      t.setId(typeId);
      n.setResourceTypeDef(t);
    }
    n.setOutputPerDay(output);
    n.setUnit("Cum/day");
    n.setProjectId(projectId);
    return n;
  }
}
```

- [ ] **Step 2: Run the tests — verify they pass**

```bash
cd backend && mvn -pl bipros-resource -am test -Dtest=ProductivityNormLookupServiceTest -q
```
Expected: 7 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-resource/src/test/java/com/bipros/resource/domain/service/ProductivityNormLookupServiceTest.java
git commit -m "test(resource): cover 5-step productivity norm resolution chain"
```

---

## Task 7: Wire `projectId` into ProductivityNorm DTOs and service

**Files:**
- Modify: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/CreateProductivityNormRequest.java`
- Modify: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/ProductivityNormResponse.java`
- Modify: `backend/bipros-resource/src/main/java/com/bipros/resource/application/service/ProductivityNormService.java`

- [ ] **Step 1: Add `projectId` to the request DTO**

In `CreateProductivityNormRequest.java`, add a new field after `resourceId` (preserve record-component order — append before the existing trailing fields):
```java
    /** Optional: when set, makes this norm a project-specific override. */
    UUID projectId,
```
(Be careful: this is a Java record — every consumer that constructs the record positionally needs updating. Search and update with the next step.)

- [ ] **Step 2: Add to response DTO**

In `ProductivityNormResponse.java`, add a `UUID projectId` field. If the response is a record, add it positionally and update the `from(...)` factory to include `n.getProjectId()`.

- [ ] **Step 3: Persist in the service**

In `ProductivityNormService.java`, in the `create` method (find the `ProductivityNorm.builder()` chain), add:
```java
        .projectId(request.projectId())
```
Add to the update method analogously.

- [ ] **Step 4: Compile + fix call sites**

```bash
cd backend && mvn -pl bipros-resource -am compile -q
```
Expected: BUILD SUCCESS. If any caller constructs the record positionally, fix it.

- [ ] **Step 5: Run existing norm tests**

```bash
cd backend && mvn -pl bipros-resource -am test -q
```
Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add backend/bipros-resource/src/main/java/com/bipros/resource/
git commit -m "feat(resource): expose projectId on productivity-norm CRUD endpoints"
```

---

## Task 8: Liquibase changeset 046 — supervisor fields on `daily_activity_resource_outputs`

**Files:**
- Create: `backend/bipros-api/src/main/resources/db/changelog/046-dar-add-supervisor.yaml`
- Modify: `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Write the changeset**

Create `046-dar-add-supervisor.yaml`:
```yaml
databaseChangeLog:
  - changeSet:
      id: 046-dar-add-supervisor-columns
      author: bipros
      changes:
        - addColumn:
            schemaName: project
            tableName: daily_activity_resource_outputs
            columns:
              - column:
                  name: supervisor_id
                  type: UUID
                  # Nullable initially — backfilled by the next changeset, then ALTER NOT NULL.
              - column:
                  name: supervisor_name
                  type: VARCHAR(150)
        - createIndex:
            schemaName: project
            tableName: daily_activity_resource_outputs
            indexName: idx_dar_supervisor
            columns:
              - column:
                  name: project_id
              - column:
                  name: supervisor_id
              - column:
                  name: output_date

  - changeSet:
      id: 046-dar-backfill-supervisor
      author: bipros
      changes:
        - sql:
            dbms: postgresql
            sql: |
              UPDATE project.daily_activity_resource_outputs o
                 SET supervisor_id = (CASE
                   WHEN o.created_by ~ '^[0-9a-fA-F-]{36}$' THEN o.created_by::uuid
                   ELSE NULL END),
                     supervisor_name = COALESCE(
                       (SELECT TRIM(CONCAT(COALESCE(u.first_name,''),' ',COALESCE(u.last_name,'')))
                          FROM security.users u
                         WHERE o.created_by ~ '^[0-9a-fA-F-]{36}$'
                           AND u.id = o.created_by::uuid),
                       'Unknown')
               WHERE o.supervisor_id IS NULL;
              -- Any row whose created_by is not a UUID gets a synthetic placeholder:
              UPDATE project.daily_activity_resource_outputs
                 SET supervisor_id = '00000000-0000-0000-0000-000000000000'::uuid,
                     supervisor_name = 'Unknown'
               WHERE supervisor_id IS NULL;

  - changeSet:
      id: 046-dar-supervisor-not-null
      author: bipros
      changes:
        - addNotNullConstraint:
            schemaName: project
            tableName: daily_activity_resource_outputs
            columnName: supervisor_id
            columnDataType: UUID
        - addNotNullConstraint:
            schemaName: project
            tableName: daily_activity_resource_outputs
            columnName: supervisor_name
            columnDataType: VARCHAR(150)
```

- [ ] **Step 2: Register in master**

Append to `db.changelog-master.yaml`:
```yaml
  - include:
      file: db/changelog/046-dar-add-supervisor.yaml
```

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-api/src/main/resources/db/changelog/046-dar-add-supervisor.yaml backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat(project): add supervisor_id + supervisor_name to daily_activity_resource_outputs (with backfill)"
```

---

## Task 9: Add supervisor fields to `DailyActivityResourceOutput` entity + DTOs

**Files:**
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/domain/model/DailyActivityResourceOutput.java`
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateDailyActivityResourceOutputRequest.java`
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/application/dto/DailyActivityResourceOutputResponse.java`

- [ ] **Step 1: Update the entity**

In `DailyActivityResourceOutput.java`, after the `private UUID resourceId;` field, add:
```java
  /** Soft FK to {@code security.users.id}. The supervisor on whose authority this output was logged. */
  @Column(name = "supervisor_id", nullable = false)
  private UUID supervisorId;

  /** Denormalised supervisor display name — set at write time so reports can group/render without a JOIN. */
  @Column(name = "supervisor_name", nullable = false, length = 150)
  private String supervisorName;
```

In the `@Table(indexes = {...})` block, add:
```java
        @Index(name = "idx_dar_supervisor", columnList = "project_id, supervisor_id, output_date")
```

- [ ] **Step 2: Update the request DTO**

In `CreateDailyActivityResourceOutputRequest.java`, add two fields (record components — preserve order, append at end):
```java
    @NotNull(message = "supervisorId is required") UUID supervisorId,

    @NotBlank(message = "supervisorName is required")
    @Size(max = 150)
    String supervisorName,
```
Add the import `import jakarta.validation.constraints.NotBlank;` if missing.

- [ ] **Step 3: Update the response DTO**

In `DailyActivityResourceOutputResponse.java`, add to the record (positional, before `Instant createdAt`):
```java
    UUID supervisorId,
    String supervisorName,
```
Update the `from(...)` factory to include `o.getSupervisorId(), o.getSupervisorName()` in the matching positions.

- [ ] **Step 4: Compile**

```bash
cd backend && mvn -pl bipros-project -am compile -q
```
Expected: compile error in `DailyActivityResourceOutputService.create()` because the builder doesn't set the new fields. That's intentional — Task 10 fixes it.

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/domain/model/DailyActivityResourceOutput.java backend/bipros-project/src/main/java/com/bipros/project/application/dto/CreateDailyActivityResourceOutputRequest.java backend/bipros-project/src/main/java/com/bipros/project/application/dto/DailyActivityResourceOutputResponse.java
git commit -m "feat(project): add supervisor fields to DailyActivityResourceOutput entity + DTOs"
```

---

## Task 10: Persist supervisor in `DailyActivityResourceOutputService`

**Files:**
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/application/service/DailyActivityResourceOutputService.java`

- [ ] **Step 1: Set supervisor in the builder**

In `create(...)`, locate the `DailyActivityResourceOutput.builder()` chain and add two lines (suggested place: after `.resourceId(...)`):
```java
        .supervisorId(request.supervisorId())
        .supervisorName(request.supervisorName().trim())
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-project -am compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Run existing project tests**

```bash
cd backend && mvn -pl bipros-project -am test -q
```
Expected: existing `DailyActivityResourceOutputServiceTest` likely fails because its fixtures don't supply supervisor fields. Update each test fixture to include `UUID.randomUUID()` for `supervisorId` and `"Test Supervisor"` for `supervisorName` in the request constructor. After fixing, re-run.

- [ ] **Step 4: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/application/service/DailyActivityResourceOutputService.java backend/bipros-project/src/test/java/com/bipros/project/application/service/DailyActivityResourceOutputServiceTest.java
git commit -m "feat(project): require supervisor fields when creating daily activity resource outputs"
```

---

## Task 11: Extend `CapacityUtilizationReport` DTO

**Files:**
- Modify: `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/dto/CapacityUtilizationReport.java`

- [ ] **Step 1: Add nested types and extend records**

Replace the file contents with:
```java
package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Capacity Utilization report — mirrors the Excel "Plant utilization" / "Manpower utilization"
 * sheets at {@code level=DETAIL}, and the "SUMMARY" sheet at {@code level=SUMMARY}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapacityUtilizationReport(
    UUID projectId,
    LocalDate fromDate,
    LocalDate toDate,
    String groupBy,   // RESOURCE_TYPE | RESOURCE
    String normType,  // MANPOWER | EQUIPMENT | null
    String level,     // DETAIL | SUMMARY
    boolean bySupervisor,
    List<Row> rows
) {
  public enum RowKind { DETAIL, SUMMARY, AVERAGE }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Row(
      RowKind kind,
      GroupKey groupKey,
      WorkActivityRef workActivity,  // null on SUMMARY/AVERAGE rows
      Budgeted budgeted,             // null on AVERAGE rows
      Period forTheDay,
      Period forTheMonth,
      Period cumulative
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record GroupKey(
      UUID resourceTypeDefId,
      UUID resourceId,
      String displayLabel
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record WorkActivityRef(
      UUID id,
      String code,
      String name,
      String defaultUnit
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Budgeted(
      BigDecimal outputPerDay,        // the value used as denominator for utilization
      BigDecimal siteOutputPerDay,    // project-scoped override (null when none)
      String source                   // PROJECT_SPECIFIC_RESOURCE | PROJECT_SPECIFIC_TYPE | SPECIFIC_RESOURCE | RESOURCE_TYPE | RESOURCE_LEGACY | NONE
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Period(
      BigDecimal qty,
      BigDecimal budgetedDays,
      BigDecimal actualDays,
      BigDecimal actualOutputPerDay,
      BigDecimal utilizationPct,
      List<SupervisorPeriod> bySupervisor   // populated only when ?bySupervisor=true
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SupervisorPeriod(
      UUID supervisorId,
      String supervisorName,
      BigDecimal qty,
      BigDecimal actualDays,
      BigDecimal utilizationPct
  ) {}
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-reporting -am compile -q
```
Expected: compile error in `CapacityUtilizationReportService` — its constructor calls now miss the new arguments. Task 12 fixes that.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-reporting/src/main/java/com/bipros/reporting/application/dto/CapacityUtilizationReport.java
git commit -m "feat(reporting): extend CapacityUtilizationReport DTO with kind, siteOutputPerDay, bySupervisor split"
```

---

## Task 12: Extend `CapacityUtilizationReportService`

**Files:**
- Modify: `backend/bipros-reporting/src/main/java/com/bipros/reporting/application/service/CapacityUtilizationReportService.java`

This task changes the service substantially — supervisor split, site-norm column, SUMMARY/AVERAGE roll-up, and a `supervisorId` filter. Replace the file contents end-to-end:

- [ ] **Step 1: Replace the file**

Replace `CapacityUtilizationReportService.java` with:

```java
package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.CapacityUtilizationReport;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.Budgeted;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.GroupKey;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.Period;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.Row;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.RowKind;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.SupervisorPeriod;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.WorkActivityRef;
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

/**
 * Capacity Utilization report — joins {@code project.daily_activity_resource_outputs} (Phase 2
 * actuals) with {@code activity.activities.work_activity_id} (Phase 1 link) and
 * {@code resource.resources.resource_type_def_id}, then resolves the budgeted norm via the
 * {@code resource.productivity_norms} 5-step chain (project-specific → global → legacy).
 *
 * <p>Implemented as native SQL through the shared {@link EntityManager} so this module does
 * <em>not</em> need a Maven dependency on {@code bipros-activity} or {@code bipros-resource}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CapacityUtilizationReportService {

  private static final double DEFAULT_HOURS_PER_DAY = 8.0;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal UTILIZATION_CAP = BigDecimal.valueOf(999);

  @PersistenceContext private EntityManager em;

  @Transactional(readOnly = true)
  public CapacityUtilizationReport build(
      UUID projectId,
      LocalDate fromDate,
      LocalDate toDate,
      String groupBy,
      String normType,
      String level,
      UUID supervisorId,
      boolean bySupervisor) {

    LocalDate effectiveTo = toDate == null ? LocalDate.now() : toDate;
    LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfYear(1) : fromDate;
    String resolvedGroupBy = groupBy == null ? "RESOURCE_TYPE" : groupBy.toUpperCase();
    String resolvedLevel = level == null ? "DETAIL" : level.toUpperCase();
    boolean groupByResource = "RESOURCE".equals(resolvedGroupBy);
    boolean summary = "SUMMARY".equals(resolvedLevel);

    // 1. Pull all output rows in the cumulative window.
    StringBuilder sql = new StringBuilder()
        .append("SELECT ")
        .append("  o.output_date, ")
        .append("  a.work_activity_id, ")
        .append("  wa.code, ")
        .append("  wa.name, ")
        .append("  wa.default_unit, ")
        .append("  r.resource_type_def_id, ")
        .append("  rtd.name AS resource_type_def_name, ")
        .append("  r.id AS resource_id, ")
        .append("  r.code AS resource_code, ")
        .append("  r.name AS resource_name, ")
        .append("  o.qty_executed, ")
        .append("  o.hours_worked, ")
        .append("  o.days_worked, ")
        .append("  o.supervisor_id, ")
        .append("  o.supervisor_name ")
        .append("FROM project.daily_activity_resource_outputs o ")
        .append("JOIN activity.activities a ON a.id = o.activity_id ")
        .append("JOIN resource.work_activities wa ON wa.id = a.work_activity_id ")
        .append("JOIN resource.resources r ON r.id = o.resource_id ")
        .append("LEFT JOIN resource.resource_type_defs rtd ON rtd.id = r.resource_type_def_id ")
        .append("WHERE o.project_id = :projectId ")
        .append("  AND o.output_date BETWEEN :fromDate AND :toDate ")
        .append("  AND a.work_activity_id IS NOT NULL ");
    if (supervisorId != null) {
      sql.append("  AND o.supervisor_id = :supervisorId ");
    }

    var query = em.createNativeQuery(sql.toString())
        .setParameter("projectId", projectId)
        .setParameter("fromDate", effectiveFrom)
        .setParameter("toDate", effectiveTo);
    if (supervisorId != null) {
      query.setParameter("supervisorId", supervisorId);
    }

    @SuppressWarnings("unchecked")
    List<Object[]> raw = query.getResultList();

    // 2. Aggregate by (workActivity, group-key). Group key = resourceTypeDefId or resourceId.
    Map<String, Aggregate> byBucket = new LinkedHashMap<>();
    for (Object[] r : raw) {
      LocalDate outputDate = ((java.sql.Date) r[0]).toLocalDate();
      UUID workActivityId = (UUID) r[1];
      String workActivityCode = (String) r[2];
      String workActivityName = (String) r[3];
      String workActivityDefaultUnit = (String) r[4];
      UUID resourceTypeDefId = (UUID) r[5];
      String resourceTypeDefName = (String) r[6];
      UUID resourceId = (UUID) r[7];
      String resourceCode = (String) r[8];
      String resourceName = (String) r[9];
      BigDecimal qty = (BigDecimal) r[10];
      Number hoursWorked = (Number) r[11];
      Number daysWorked = (Number) r[12];
      UUID rowSupervisorId = (UUID) r[13];
      String rowSupervisorName = (String) r[14];

      double effectiveDays;
      if (daysWorked != null) {
        effectiveDays = daysWorked.doubleValue();
      } else if (hoursWorked != null) {
        effectiveDays = hoursWorked.doubleValue() / DEFAULT_HOURS_PER_DAY;
      } else {
        effectiveDays = 0.0;
      }

      UUID groupResourceTypeId = groupByResource ? null : resourceTypeDefId;
      UUID groupResourceId = groupByResource ? resourceId : null;
      String displayLabel = groupByResource
          ? (resourceCode != null ? resourceCode + " — " + resourceName : resourceName)
          : (resourceTypeDefName != null ? resourceTypeDefName : "(no type)");
      String bucketKey = workActivityId + "|" + (groupByResource ? resourceId : nullSafe(resourceTypeDefId));

      Aggregate agg = byBucket.computeIfAbsent(bucketKey, k -> new Aggregate(
          new GroupKey(groupResourceTypeId, groupResourceId, displayLabel),
          new WorkActivityRef(workActivityId, workActivityCode, workActivityName, workActivityDefaultUnit),
          resourceId));

      agg.addCumulative(qty, effectiveDays, rowSupervisorId, rowSupervisorName, outputDate, effectiveTo);
    }

    // 3. Resolve budgeted norm per bucket and assemble DETAIL rows.
    List<Row> detailRows = new ArrayList<>(byBucket.size());
    for (Aggregate agg : byBucket.values()) {
      Budgeted budgeted = resolveBudgeted(agg.workActivity.id(), agg.representativeResourceId, projectId);
      Period day = period(agg.dayQty, agg.dayDays, budgeted, agg.daySupervisors, bySupervisor);
      Period month = period(agg.monthQty, agg.monthDays, budgeted, agg.monthSupervisors, bySupervisor);
      Period cum = period(agg.cumQty, agg.cumDays, budgeted, agg.cumSupervisors, bySupervisor);
      detailRows.add(new Row(RowKind.DETAIL, agg.groupKey, agg.workActivity, budgeted, day, month, cum));
    }

    // Optional normType filter — discard rows whose norm record's normType doesn't match.
    if (normType != null && !normType.isBlank()) {
      String normTypeUpper = normType.toUpperCase();
      detailRows.removeIf(row -> !matchesNormType(row, normTypeUpper));
    }

    List<Row> finalRows = summary ? collapseToSummary(detailRows, bySupervisor) : detailRows;

    return new CapacityUtilizationReport(
        projectId, effectiveFrom, effectiveTo, resolvedGroupBy, normType,
        resolvedLevel, bySupervisor, finalRows);
  }

  // ─── SUMMARY collapse (one row per group-key + AVERAGE row) ────────────────────────────────
  private List<Row> collapseToSummary(List<Row> detail, boolean bySupervisor) {
    Map<UUID, SummaryAccumulator> byGroup = new LinkedHashMap<>();
    for (Row d : detail) {
      UUID gk = d.groupKey().resourceTypeDefId() != null
          ? d.groupKey().resourceTypeDefId()
          : d.groupKey().resourceId();
      SummaryAccumulator acc = byGroup.computeIfAbsent(gk, k -> new SummaryAccumulator(d.groupKey()));
      acc.absorb(d);
    }

    List<Row> rows = new ArrayList<>(byGroup.size() + 1);
    BigDecimal sumUtilDay = BigDecimal.ZERO; int countUtilDay = 0;
    BigDecimal sumUtilMonth = BigDecimal.ZERO; int countUtilMonth = 0;
    BigDecimal sumUtilCum = BigDecimal.ZERO; int countUtilCum = 0;

    for (SummaryAccumulator acc : byGroup.values()) {
      Period day = acc.toDayPeriod(bySupervisor);
      Period month = acc.toMonthPeriod(bySupervisor);
      Period cum = acc.toCumulativePeriod(bySupervisor);
      rows.add(new Row(RowKind.SUMMARY, acc.groupKey, null, null, day, month, cum));
      if (day.utilizationPct() != null) { sumUtilDay = sumUtilDay.add(day.utilizationPct()); countUtilDay++; }
      if (month.utilizationPct() != null) { sumUtilMonth = sumUtilMonth.add(month.utilizationPct()); countUtilMonth++; }
      if (cum.utilizationPct() != null) { sumUtilCum = sumUtilCum.add(cum.utilizationPct()); countUtilCum++; }
    }

    Period avgDay = avgPeriod(sumUtilDay, countUtilDay);
    Period avgMonth = avgPeriod(sumUtilMonth, countUtilMonth);
    Period avgCum = avgPeriod(sumUtilCum, countUtilCum);
    rows.add(new Row(RowKind.AVERAGE, new GroupKey(null, null, "AVERAGE"), null, null, avgDay, avgMonth, avgCum));
    return rows;
  }

  private Period avgPeriod(BigDecimal sum, int count) {
    if (count == 0) return new Period(null, null, null, null, null, null);
    BigDecimal avg = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    return new Period(null, null, null, null, avg, null);
  }

  // ─── Norm resolution (5-step chain) ─────────────────────────────────────────────────────────
  private Budgeted resolveBudgeted(UUID workActivityId, UUID resourceId, UUID projectId) {
    if (workActivityId == null || resourceId == null) {
      return new Budgeted(null, null, "NONE");
    }
    // 1) project + specific resource
    BigDecimal projSpecific = singleBigDecimal(
        "SELECT n.output_per_day FROM resource.productivity_norms n "
            + "WHERE n.work_activity_id = :wa AND n.resource_id = :res AND n.project_id = :proj",
        Map.of("wa", workActivityId, "res", resourceId, "proj", projectId));
    if (projSpecific != null) {
      return new Budgeted(projSpecific, projSpecific, "PROJECT_SPECIFIC_RESOURCE");
    }
    // 2) project + type
    BigDecimal projType = singleBigDecimal(
        "SELECT n.output_per_day FROM resource.productivity_norms n "
            + "JOIN resource.resources r ON r.resource_type_def_id = n.resource_type_def_id "
            + "WHERE n.work_activity_id = :wa AND n.resource_id IS NULL "
            + "  AND n.project_id = :proj AND r.id = :res",
        Map.of("wa", workActivityId, "res", resourceId, "proj", projectId));
    if (projType != null) {
      return new Budgeted(projType, projType, "PROJECT_SPECIFIC_TYPE");
    }
    // 3) global resource
    BigDecimal specific = singleBigDecimal(
        "SELECT n.output_per_day FROM resource.productivity_norms n "
            + "WHERE n.work_activity_id = :wa AND n.resource_id = :res AND n.project_id IS NULL",
        Map.of("wa", workActivityId, "res", resourceId));
    if (specific != null) {
      return new Budgeted(specific, null, "SPECIFIC_RESOURCE");
    }
    // 4) global type
    BigDecimal typeLevel = singleBigDecimal(
        "SELECT n.output_per_day FROM resource.productivity_norms n "
            + "JOIN resource.resources r ON r.resource_type_def_id = n.resource_type_def_id "
            + "WHERE n.work_activity_id = :wa AND n.resource_id IS NULL "
            + "  AND n.project_id IS NULL AND r.id = :res",
        Map.of("wa", workActivityId, "res", resourceId));
    if (typeLevel != null) {
      return new Budgeted(typeLevel, null, "RESOURCE_TYPE");
    }
    // 5) legacy
    BigDecimal legacy = singleBigDecimal(
        "SELECT r.standard_output_per_day FROM resource.resources r WHERE r.id = :res",
        Map.of("res", resourceId));
    if (legacy != null) {
      return new Budgeted(legacy, null, "RESOURCE_LEGACY");
    }
    return new Budgeted(null, null, "NONE");
  }

  private boolean matchesNormType(Row row, String normTypeUpper) {
    if (row.budgeted() == null || row.workActivity() == null) {
      return false;
    }
    Object stored = em.createNativeQuery(
            "SELECT norm_type FROM resource.productivity_norms WHERE work_activity_id = :wa")
        .setParameter("wa", row.workActivity().id())
        .setMaxResults(1)
        .getResultList().stream().findFirst().orElse(null);
    return stored != null && normTypeUpper.equals(stored.toString());
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────────────────────
  private Period period(BigDecimal qty, double days, Budgeted budgeted, Map<UUID, SupervisorBucket> sup, boolean bySupervisor) {
    BigDecimal actualDaysExact = days > 0 ? BigDecimal.valueOf(days) : BigDecimal.ZERO;
    BigDecimal budgetedDaysExact = (budgeted.outputPerDay() != null && budgeted.outputPerDay().signum() > 0)
        ? qty.divide(budgeted.outputPerDay(), 8, RoundingMode.HALF_UP)
        : null;
    BigDecimal actualOutputPerDayExact = (days > 0)
        ? qty.divide(actualDaysExact, 8, RoundingMode.HALF_UP)
        : null;
    BigDecimal utilization = (budgetedDaysExact != null && actualDaysExact.signum() > 0)
        ? budgetedDaysExact.divide(actualDaysExact, 8, RoundingMode.HALF_UP).multiply(HUNDRED)
        : null;
    if (utilization != null) {
      utilization = utilization.min(UTILIZATION_CAP).setScale(2, RoundingMode.HALF_UP);
    }

    List<SupervisorPeriod> bySup = null;
    if (bySupervisor && sup != null && !sup.isEmpty()) {
      bySup = new ArrayList<>(sup.size());
      for (SupervisorBucket b : sup.values()) {
        BigDecimal sActDays = b.days > 0 ? BigDecimal.valueOf(b.days) : BigDecimal.ZERO;
        BigDecimal sBudDays = (budgeted.outputPerDay() != null && budgeted.outputPerDay().signum() > 0)
            ? b.qty.divide(budgeted.outputPerDay(), 8, RoundingMode.HALF_UP) : null;
        BigDecimal sUtil = (sBudDays != null && sActDays.signum() > 0)
            ? sBudDays.divide(sActDays, 8, RoundingMode.HALF_UP).multiply(HUNDRED) : null;
        if (sUtil != null) sUtil = sUtil.min(UTILIZATION_CAP).setScale(2, RoundingMode.HALF_UP);
        bySup.add(new SupervisorPeriod(b.supervisorId, b.supervisorName, round(b.qty), round(sActDays), sUtil));
      }
    }

    return new Period(
        round(qty),
        budgetedDaysExact != null ? round(budgetedDaysExact) : null,
        round(actualDaysExact),
        actualOutputPerDayExact != null ? round(actualOutputPerDayExact) : null,
        utilization,
        bySup);
  }

  private static BigDecimal round(BigDecimal v) {
    return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
  }

  private static boolean isInMonth(LocalDate date, LocalDate anchor) {
    return YearMonth.from(date).equals(YearMonth.from(anchor));
  }

  private static String nullSafe(UUID id) {
    return id == null ? "—" : id.toString();
  }

  @SuppressWarnings("unchecked")
  private BigDecimal singleBigDecimal(String sql, Map<String, Object> params) {
    var query = em.createNativeQuery(sql);
    params.forEach(query::setParameter);
    List<Object> rows = query.setMaxResults(1).getResultList();
    if (rows.isEmpty() || rows.get(0) == null) return null;
    Object o = rows.get(0);
    if (o instanceof BigDecimal bd) return bd;
    if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
    return null;
  }

  /** Mutable accumulator scoped to a single (workActivity × group-key) bucket. */
  private static final class Aggregate {
    final GroupKey groupKey;
    final WorkActivityRef workActivity;
    final UUID representativeResourceId;
    BigDecimal dayQty = BigDecimal.ZERO;   double dayDays = 0;
    BigDecimal monthQty = BigDecimal.ZERO; double monthDays = 0;
    BigDecimal cumQty = BigDecimal.ZERO;   double cumDays = 0;
    final Map<UUID, SupervisorBucket> daySupervisors = new LinkedHashMap<>();
    final Map<UUID, SupervisorBucket> monthSupervisors = new LinkedHashMap<>();
    final Map<UUID, SupervisorBucket> cumSupervisors = new LinkedHashMap<>();

    Aggregate(GroupKey groupKey, WorkActivityRef workActivity, UUID representativeResourceId) {
      this.groupKey = groupKey;
      this.workActivity = workActivity;
      this.representativeResourceId = representativeResourceId;
    }

    void addCumulative(BigDecimal qty, double days, UUID supId, String supName, LocalDate outputDate, LocalDate anchor) {
      cumQty = cumQty.add(qty); cumDays += days;
      bumpSupervisor(cumSupervisors, supId, supName, qty, days);
      if (isInMonth(outputDate, anchor)) {
        monthQty = monthQty.add(qty); monthDays += days;
        bumpSupervisor(monthSupervisors, supId, supName, qty, days);
      }
      if (outputDate.equals(anchor)) {
        dayQty = dayQty.add(qty); dayDays += days;
        bumpSupervisor(daySupervisors, supId, supName, qty, days);
      }
    }

    private void bumpSupervisor(Map<UUID, SupervisorBucket> m, UUID id, String name, BigDecimal qty, double days) {
      if (id == null) return;
      SupervisorBucket b = m.computeIfAbsent(id, k -> new SupervisorBucket(id, name));
      b.qty = b.qty.add(qty);
      b.days += days;
    }
  }

  /** Per-supervisor sub-bucket inside an Aggregate. */
  private static final class SupervisorBucket {
    final UUID supervisorId;
    final String supervisorName;
    BigDecimal qty = BigDecimal.ZERO;
    double days = 0;
    SupervisorBucket(UUID id, String name) { this.supervisorId = id; this.supervisorName = name; }
  }

  /** Mutable accumulator for SUMMARY-level roll-up across all detail rows of one group-key. */
  private static final class SummaryAccumulator {
    final GroupKey groupKey;
    BigDecimal dayQty = BigDecimal.ZERO,   dayBud = BigDecimal.ZERO,   dayAct = BigDecimal.ZERO;
    BigDecimal monthQty = BigDecimal.ZERO, monthBud = BigDecimal.ZERO, monthAct = BigDecimal.ZERO;
    BigDecimal cumQty = BigDecimal.ZERO,   cumBud = BigDecimal.ZERO,   cumAct = BigDecimal.ZERO;

    SummaryAccumulator(GroupKey gk) { this.groupKey = gk; }

    void absorb(Row r) {
      dayQty = dayQty.add(zeroIfNull(r.forTheDay().qty()));
      dayBud = dayBud.add(zeroIfNull(r.forTheDay().budgetedDays()));
      dayAct = dayAct.add(zeroIfNull(r.forTheDay().actualDays()));
      monthQty = monthQty.add(zeroIfNull(r.forTheMonth().qty()));
      monthBud = monthBud.add(zeroIfNull(r.forTheMonth().budgetedDays()));
      monthAct = monthAct.add(zeroIfNull(r.forTheMonth().actualDays()));
      cumQty = cumQty.add(zeroIfNull(r.cumulative().qty()));
      cumBud = cumBud.add(zeroIfNull(r.cumulative().budgetedDays()));
      cumAct = cumAct.add(zeroIfNull(r.cumulative().actualDays()));
    }

    Period toDayPeriod(boolean bySup) { return summaryPeriod(dayQty, dayBud, dayAct); }
    Period toMonthPeriod(boolean bySup) { return summaryPeriod(monthQty, monthBud, monthAct); }
    Period toCumulativePeriod(boolean bySup) { return summaryPeriod(cumQty, cumBud, cumAct); }

    private static Period summaryPeriod(BigDecimal qty, BigDecimal bud, BigDecimal act) {
      BigDecimal util = (bud.signum() > 0 && act.signum() > 0)
          ? bud.divide(act, 8, RoundingMode.HALF_UP).multiply(HUNDRED).min(UTILIZATION_CAP).setScale(2, RoundingMode.HALF_UP)
          : null;
      return new Period(
          qty.setScale(2, RoundingMode.HALF_UP),
          bud.setScale(2, RoundingMode.HALF_UP),
          act.setScale(2, RoundingMode.HALF_UP),
          null,
          util,
          null);
    }

    private static BigDecimal zeroIfNull(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
  }
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-reporting -am compile -q
```
Expected: compile error in `ReportController` because the controller still calls the old 5-arg `build(...)`. Task 13 fixes that.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-reporting/src/main/java/com/bipros/reporting/application/service/CapacityUtilizationReportService.java
git commit -m "feat(reporting): supervisor split + SUMMARY/AVERAGE roll-up + site-norm column on capacity utilization report"
```

---

## Task 13: Update `ReportController` to accept new params

**Files:**
- Modify: `backend/bipros-reporting/src/main/java/com/bipros/reporting/presentation/controller/ReportController.java`

- [ ] **Step 1: Replace the `getCapacityUtilization` method**

Find the existing method and replace with:
```java
  @GetMapping("/capacity-utilization")
  public ApiResponse<CapacityUtilizationReport> getCapacityUtilization(
      @RequestParam UUID projectId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
      @RequestParam(required = false, defaultValue = "RESOURCE_TYPE") String groupBy,
      @RequestParam(required = false) String normType,
      @RequestParam(required = false, defaultValue = "DETAIL") String level,
      @RequestParam(required = false) UUID supervisorId,
      @RequestParam(required = false, defaultValue = "false") boolean bySupervisor) {
    return ApiResponse.ok(
        capacityUtilizationReportService.build(
            projectId, fromDate, toDate, groupBy, normType, level, supervisorId, bySupervisor));
  }
```

- [ ] **Step 2: Compile + run all backend tests**

```bash
cd backend && mvn -pl bipros-reporting -am compile -q && mvn -pl bipros-reporting -am test -q
```
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-reporting/src/main/java/com/bipros/reporting/presentation/controller/ReportController.java
git commit -m "feat(reporting): expose level + supervisorId + bySupervisor query params on /v1/reports/capacity-utilization"
```

---

## Task 14: Backend integration test for the extended endpoint

**Files:**
- Create: `backend/bipros-api/src/test/java/com/bipros/api/integration/CapacityUtilizationReportIntegrationTest.java`

- [ ] **Step 1: Write the integration test**

The test boots the full Spring context against a real Postgres (pattern follows `ResourceMaterialIntegrationTest` already in `bipros-api/integration`). It seeds: 1 project, 2 equipment ResourceTypeDefs, 2 resources, 2 work activities, 1 project-specific norm + 1 global norm, then 4 daily outputs across 2 supervisors and 2 days.

Create `backend/bipros-api/src/test/java/com/bipros/api/integration/CapacityUtilizationReportIntegrationTest.java`:

```java
package com.bipros.api.integration;

import com.bipros.api.BiprosApplication;
import com.bipros.reporting.application.dto.CapacityUtilizationReport;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.RowKind;
import com.bipros.reporting.application.service.CapacityUtilizationReportService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = BiprosApplication.class)
@ActiveProfiles("test")
@Transactional
class CapacityUtilizationReportIntegrationTest {

  @Autowired CapacityUtilizationReportService service;
  @PersistenceContext EntityManager em;

  private UUID projectId;
  private UUID workActivityId;
  private UUID resourceId;
  private UUID resourceTypeDefId;
  private UUID supervisorAlice = UUID.randomUUID();
  private UUID supervisorBob   = UUID.randomUUID();
  private final LocalDate today = LocalDate.now();
  private final LocalDate yesterday = today.minusDays(1);

  @BeforeEach
  void seed() {
    projectId = UUID.randomUUID();
    resourceTypeDefId = UUID.randomUUID();
    resourceId = UUID.randomUUID();
    workActivityId = UUID.randomUUID();

    // Insert minimal rows via native SQL — keeps the test independent of repository APIs.
    em.createNativeQuery("INSERT INTO project.projects (id, code, name, created_at, updated_at) " +
        "VALUES (?1, 'TEST-CAP', 'Test Capacity', now(), now())")
        .setParameter(1, projectId).executeUpdate();

    em.createNativeQuery("INSERT INTO resource.resource_type_defs " +
        "(id, code, name, base_category, sort_order, active, system_default, created_at, updated_at) " +
        "VALUES (?1, 'EQ_BD', 'Bull Dozer', 'NONLABOR', 1, true, false, now(), now())")
        .setParameter(1, resourceTypeDefId).executeUpdate();

    em.createNativeQuery("INSERT INTO resource.resources " +
        "(id, code, name, resource_type, resource_type_def_id, created_at, updated_at) " +
        "VALUES (?1, 'BD-001', 'Bull Dozer 001', 'NONLABOR', ?2, now(), now())")
        .setParameter(1, resourceId).setParameter(2, resourceTypeDefId).executeUpdate();

    em.createNativeQuery("INSERT INTO resource.work_activities " +
        "(id, code, name, default_unit, sort_order, active, created_at, updated_at) " +
        "VALUES (?1, 'WA001', 'Unclassified Excavation', 'Cum', 1, true, now(), now())")
        .setParameter(1, workActivityId).executeUpdate();

    // Global norm: 900 Cum/day for the type
    em.createNativeQuery("INSERT INTO resource.productivity_norms " +
        "(id, norm_type, work_activity_id, resource_type_def_id, unit, output_per_day, created_at, updated_at) " +
        "VALUES (?1, 'EQUIPMENT', ?2, ?3, 'Cum/day', 900, now(), now())")
        .setParameter(1, UUID.randomUUID()).setParameter(2, workActivityId).setParameter(3, resourceTypeDefId)
        .executeUpdate();

    // Project-specific override: 1200 Cum/day for the same type within this project
    em.createNativeQuery("INSERT INTO resource.productivity_norms " +
        "(id, norm_type, work_activity_id, resource_type_def_id, project_id, unit, output_per_day, created_at, updated_at) " +
        "VALUES (?1, 'EQUIPMENT', ?2, ?3, ?4, 'Cum/day', 1200, now(), now())")
        .setParameter(1, UUID.randomUUID()).setParameter(2, workActivityId).setParameter(3, resourceTypeDefId)
        .setParameter(4, projectId).executeUpdate();

    // Activity row in activity schema (linked to the work activity)
    UUID activityId = UUID.randomUUID();
    em.createNativeQuery("INSERT INTO activity.activities " +
        "(id, project_id, code, name, work_activity_id, created_at, updated_at) " +
        "VALUES (?1, ?2, 'A001', 'Excavation', ?3, now(), now())")
        .setParameter(1, activityId).setParameter(2, projectId).setParameter(3, workActivityId).executeUpdate();

    // 4 outputs across 2 supervisors, 2 dates
    insertOutput(activityId, today,     supervisorAlice, "Alice", 600, 1.0);
    insertOutput(activityId, today,     supervisorBob,   "Bob",   400, 0.5);
    insertOutput(activityId, yesterday, supervisorAlice, "Alice", 700, 1.0);
    insertOutput(activityId, yesterday, supervisorBob,   "Bob",   300, 0.5);
  }

  private void insertOutput(UUID activityId, LocalDate date, UUID supId, String supName, int qty, double days) {
    em.createNativeQuery("INSERT INTO project.daily_activity_resource_outputs " +
        "(id, project_id, output_date, activity_id, resource_id, qty_executed, unit, days_worked, " +
        " supervisor_id, supervisor_name, created_at, updated_at) " +
        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'Cum', ?7, ?8, ?9, now(), now())")
        .setParameter(1, UUID.randomUUID()).setParameter(2, projectId).setParameter(3, date)
        .setParameter(4, activityId).setParameter(5, resourceId).setParameter(6, BigDecimal.valueOf(qty))
        .setParameter(7, days).setParameter(8, supId).setParameter(9, supName).executeUpdate();
  }

  @Test
  void detailLevel_returnsRowsWithSiteNormFromProjectOverride() {
    CapacityUtilizationReport report = service.build(
        projectId, yesterday, today, "RESOURCE_TYPE", null, "DETAIL", null, false);

    assertThat(report.rows()).hasSize(1);
    var row = report.rows().get(0);
    assertThat(row.kind()).isEqualTo(RowKind.DETAIL);
    assertThat(row.budgeted().outputPerDay()).isEqualByComparingTo("1200");      // project override wins
    assertThat(row.budgeted().siteOutputPerDay()).isEqualByComparingTo("1200");  // surfaced
    assertThat(row.budgeted().source()).isEqualTo("PROJECT_SPECIFIC_TYPE");
    // forTheDay = today's outputs only (1000 Cum across both supervisors)
    assertThat(row.forTheDay().qty()).isEqualByComparingTo("1000.00");
    // cumulative across 2 days = 2000
    assertThat(row.cumulative().qty()).isEqualByComparingTo("2000.00");
  }

  @Test
  void summaryLevel_collapsesToOneRowPlusAverageRow() {
    CapacityUtilizationReport report = service.build(
        projectId, yesterday, today, "RESOURCE_TYPE", null, "SUMMARY", null, false);

    assertThat(report.rows()).hasSize(2);  // 1 SUMMARY + 1 AVERAGE
    assertThat(report.rows().get(0).kind()).isEqualTo(RowKind.SUMMARY);
    assertThat(report.rows().get(1).kind()).isEqualTo(RowKind.AVERAGE);
    assertThat(report.rows().get(1).groupKey().displayLabel()).isEqualTo("AVERAGE");
  }

  @Test
  void bySupervisor_splitsPeriodIntoSubBuckets() {
    CapacityUtilizationReport report = service.build(
        projectId, yesterday, today, "RESOURCE_TYPE", null, "DETAIL", null, true);

    var row = report.rows().get(0);
    assertThat(row.cumulative().bySupervisor()).hasSize(2);
    assertThat(row.cumulative().bySupervisor()).extracting("supervisorName")
        .containsExactlyInAnyOrder("Alice", "Bob");
  }

  @Test
  void supervisorIdFilter_narrowsToSingleSupervisor() {
    CapacityUtilizationReport report = service.build(
        projectId, yesterday, today, "RESOURCE_TYPE", null, "DETAIL", supervisorAlice, false);

    var row = report.rows().get(0);
    // Alice contributed 600 + 700 = 1300
    assertThat(row.cumulative().qty()).isEqualByComparingTo("1300.00");
  }
}
```

- [ ] **Step 2: Run the test**

```bash
cd backend && mvn -pl bipros-api -am test -Dtest=CapacityUtilizationReportIntegrationTest -q
```
Expected: 4 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-api/src/test/java/com/bipros/api/integration/CapacityUtilizationReportIntegrationTest.java
git commit -m "test(api): integration test for capacity utilization SUMMARY + bySupervisor + site-norm"
```

---

## Task 15: Frontend — extend `dailyActivityResourceOutputApi.ts`

**Files:**
- Modify: `frontend/src/lib/api/dailyActivityResourceOutputApi.ts`

- [ ] **Step 1: Add supervisor fields to the request and response types**

Read the file first:
```bash
cat frontend/src/lib/api/dailyActivityResourceOutputApi.ts
```

Add `supervisorId: string` and `supervisorName: string` to the `CreateDailyActivityResourceOutputRequest` interface (both required), and add the same two fields to the `DailyActivityResourceOutput` response interface.

- [ ] **Step 2: Type-check**

```bash
cd frontend && pnpm typecheck
```
Expected: errors at any UI call site that constructs the request without supervisor fields. Fix call sites by passing `supervisorId` (from a user picker) and `supervisorName` (denormalised).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api/dailyActivityResourceOutputApi.ts frontend/src/
git commit -m "feat(frontend): require supervisor fields when creating daily activity resource outputs"
```

---

## Task 16: Frontend — extend `capacityUtilizationApi.ts`

**Files:**
- Modify: `frontend/src/lib/api/capacityUtilizationApi.ts`

- [ ] **Step 1: Replace the file contents**

Read the file first to preserve the axios import and call patterns:
```bash
cat frontend/src/lib/api/capacityUtilizationApi.ts
```

Update the `CapacityUtilizationRow` interface — add `kind: "DETAIL" | "SUMMARY" | "AVERAGE"`. Make `workActivity` and `budgeted` `nullable`. In the `Budgeted` interface, add `siteOutputPerDay: number | null`. In the `Period` interface, add:
```ts
bySupervisor?: SupervisorPeriod[] | null;
```
Define `SupervisorPeriod`:
```ts
export interface SupervisorPeriod {
  supervisorId: string;
  supervisorName: string;
  qty: number | null;
  actualDays: number | null;
  utilizationPct: number | null;
}
```
Add to `GetCapacityUtilizationParams`:
```ts
level?: "DETAIL" | "SUMMARY";
supervisorId?: string;
bySupervisor?: boolean;
```
Update the `get(params)` function to append the three new params to the query string when defined.
Update the `CapacityUtilizationReport` interface to add `level: string` and `bySupervisor: boolean`.

- [ ] **Step 2: Type-check**

```bash
cd frontend && pnpm typecheck
```
Expected: errors at the page consuming this API will surface in Task 17.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api/capacityUtilizationApi.ts
git commit -m "feat(frontend): extend capacityUtilizationApi with level, supervisorId, bySupervisor + new DTO fields"
```

---

## Task 17: Frontend — extend the capacity-utilization page

**Files:**
- Modify: `frontend/src/app/(app)/projects/[projectId]/capacity-utilization/page.tsx`

- [ ] **Step 1: Add state + UI controls**

Read the page:
```bash
cat frontend/src/app/\(app\)/projects/\[projectId\]/capacity-utilization/page.tsx
```

Add three new state hooks alongside the existing ones:
```tsx
const [level, setLevel] = useState<"DETAIL" | "SUMMARY">("DETAIL");
const [bySupervisor, setBySupervisor] = useState(false);
const [supervisorId, setSupervisorId] = useState<string | undefined>(undefined);
```

Add the new params to the `useQuery` `queryKey` and to the `capacityUtilizationApi.get(...)` call.

- [ ] **Step 2: Add UI controls**

Above the table, add a Level toggle (Detail/Summary), a "By supervisor" checkbox, and an optional Supervisor filter dropdown (populated from a separate `usersApi.list({ role: 'SITE_SUPERVISOR' })` call — if no such API exists yet, leave the dropdown as a free-text input for now and follow up; the field is optional).

- [ ] **Step 3: Render the new columns**

In the row render loop:
- When `row.kind === "SUMMARY"` or `"AVERAGE"`, omit the work-activity columns (render the row group label spanning them).
- When `row.kind === "AVERAGE"`, style the row distinctly (e.g. `font-bold border-t-2`).
- Render a "Site Norm" cell next to "Norm/Day" — show `row.budgeted.siteOutputPerDay` when present, otherwise an em-dash.
- When `bySupervisor` is true, expand each Period block (Day / Month / Cum) into sub-columns per `Period.bySupervisor[]`. Suggested implementation: when `bySupervisor`, render an extra detail row under each main row with a horizontally-scrolling table of supervisor breakdowns.

- [ ] **Step 4: Update CSV download**

In `downloadCsv(...)`, add columns for "Site Norm" and (when `bySupervisor`) one column per (period × supervisor × metric).

- [ ] **Step 5: Run dev server, smoke test**

```bash
cd frontend && pnpm dev
```
Open `http://localhost:3000/projects/<id>/capacity-utilization`. Toggle Detail/Summary; verify SUMMARY shows roll-up + AVERAGE row. Toggle By Supervisor; verify per-supervisor sub-columns appear.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/\(app\)/projects/\[projectId\]/capacity-utilization/page.tsx
git commit -m "feat(frontend): add Detail/Summary + by-supervisor + site-norm rendering to capacity-utilization page"
```

---

## Task 18: Frontend — Playwright e2e walking through the extended report

**Files:**
- Create: `frontend/tests/e2e/capacity-utilization-extension.spec.ts`

- [ ] **Step 1: Write the e2e**

```ts
import { test, expect } from "@playwright/test";

test.describe("Capacity Utilization extension", () => {
  test("supervisor split + site-norm column + SUMMARY/AVERAGE row appear", async ({ page, request, baseURL }) => {
    // Login + grab token via the existing auth flow
    await page.goto("/auth/login");
    await page.fill('input[name="username"]', "admin");
    await page.fill('input[name="password"]', "admin123");
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/projects/);

    // Pick the first project from the list
    const projectLink = page.getByRole("link", { name: /open project/i }).first();
    await projectLink.click();

    // Navigate to capacity utilization
    await page.getByRole("link", { name: /capacity util/i }).click();
    await expect(page.getByRole("heading", { name: /capacity utilization/i })).toBeVisible();

    // Toggle SUMMARY level — expect AVERAGE row
    await page.getByRole("button", { name: /summary/i }).click();
    await expect(page.getByText("AVERAGE")).toBeVisible();

    // Toggle By Supervisor — expect supervisor sub-columns or detail rows
    await page.getByLabel(/by supervisor/i).check();
    // The exact selector for the supervisor breakdown depends on the implementation in Task 17;
    // assert at least one supervisor name pill renders.
    await expect(page.locator('[data-testid="supervisor-breakdown"]').first()).toBeVisible();
  });
});
```

- [ ] **Step 2: Run the e2e**

Backend must be running. Then:
```bash
cd frontend && pnpm test:e2e -- capacity-utilization-extension
```
Expected: PASS. If selectors don't match, adjust the page to add `data-testid` attributes used in the spec.

- [ ] **Step 3: Commit**

```bash
git add frontend/tests/e2e/capacity-utilization-extension.spec.ts
git commit -m "test(frontend): e2e for capacity-utilization extension (SUMMARY, supervisor split)"
```

---

## Task 19: Run the full test suites end-to-end

- [ ] **Step 1: Backend full build + tests**

```bash
cd backend && mvn -q clean test
```
Expected: BUILD SUCCESS.

- [ ] **Step 2: Frontend type-check + e2e**

```bash
cd frontend && pnpm typecheck && pnpm test:e2e
```
Expected: 0 type errors, all e2e green.

- [ ] **Step 3: Smoke-test the API**

With backend running:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')
PROJECT_ID=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/projects | jq -r '.data[0].id')
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/v1/reports/capacity-utilization?projectId=$PROJECT_ID&level=SUMMARY&bySupervisor=true" | jq '.data | { level, bySupervisor, rowCount: (.rows | length), kinds: (.rows | map(.kind) | unique) }'
```
Expected JSON: `{ level: "SUMMARY", bySupervisor: true, rowCount: <n>, kinds: ["AVERAGE","SUMMARY"] }`.

- [ ] **Step 4: Commit (no-op if everything was already committed)**

```bash
git status
```
Expected: clean working tree.

---

## Self-Review Notes

**Spec coverage check (against `docs/superpowers/specs/2026-04-27-capacity-utilization-reports-design.md`):**
- §2.1 (project_id on productivity_norms) → Tasks 1, 2, 3, 7
- §2.2 (supervisor on DAR) → Tasks 8, 9, 10
- §3.1 (endpoint signature) → Task 13
- §3.2 (DTO additions) → Task 11
- §3.3 (service changes) → Task 12
- §3.4 (frontend) → Tasks 15, 16, 17
- §8 testing → Tasks 6, 14, 18, 19
- §9 migration → Tasks 1, 8

**Out-of-plan (covered by separate plans, intentionally not here):** §2.3 (MonthlyBoqProjection), §2.4 (DailyResourcePlan), §4 (Daily Deployment matrix), §5 (DPR matrix), §7 (seeders).

**Type consistency:**
- `Budgeted.source` strings used in service ("PROJECT_SPECIFIC_RESOURCE", "PROJECT_SPECIFIC_TYPE", "SPECIFIC_RESOURCE", "RESOURCE_TYPE", "RESOURCE_LEGACY", "NONE") match the `ResolvedNorm.Source` enum names from Task 4 — frontend treats as opaque strings.
- `Row.kind` values ("DETAIL", "SUMMARY", "AVERAGE") match the `RowKind` enum from Task 11.
- The `build(...)` signature in Task 12 (`projectId, fromDate, toDate, groupBy, normType, level, supervisorId, bySupervisor`) matches the controller call in Task 13.
- Liquibase changeset id format (`045-...`, `046-...`) matches existing convention.
