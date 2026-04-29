# Labour Master Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Labour Master Module: a global catalogue of construction-worker designations (44 Oman seed rows across 5 categories) plus a per-project deployment overlay, exposed as 6 UI screens at `/labour-master` and a REST API under `bipros-resource`.

**Architecture:** Two new JPA entities (`LabourDesignation`, `ProjectLabourDeployment`) in the existing `bipros-resource` module, schema `resource`. Three fixed Java enums (`LabourCategory`, `LabourGrade`, `NationalityType`). Two REST controllers (`/v1/labour-designations`, `/v1/projects/{projectId}/labour-deployments`). Per-project effective-rate fallback. Six-screen Next.js 16 frontend at `/labour-master`. Boot seeder gated on `seed` profile loads the 44 Oman designations + Oman demo-project deployments.

**Tech Stack:** Java 23 / Spring Boot 3.5 (backend), Lombok, Hibernate 6 with `@JdbcTypeCode(SqlTypes.JSON)` for JSONB columns, JUnit 5, Mockito, `@SpringBootTest`. Next.js 16 App Router, React 19, TypeScript, TanStack Query, axios, Tailwind, shadcn/ui, Recharts, pnpm. Playwright for e2e.

**Spec:** `docs/superpowers/specs/2026-04-28-labour-master-module-design.md`

---

## File Structure

### Backend — `backend/bipros-resource/src/main/java/com/bipros/resource/`

| File | Responsibility |
|---|---|
| `domain/model/LabourCategory.java` | Enum: 5 categories with code-prefix + display name |
| `domain/model/LabourGrade.java` | Enum: A–E with band metadata accessor |
| `domain/model/NationalityType.java` | Enum: OMANI / EXPAT / OMANI_OR_EXPAT |
| `domain/model/LabourDesignation.java` | Entity: global designation row |
| `domain/model/ProjectLabourDeployment.java` | Entity: per-project deployment overlay |
| `domain/repository/LabourDesignationRepository.java` | JPA repo |
| `domain/repository/ProjectLabourDeploymentRepository.java` | JPA repo |
| `application/dto/LabourDesignationRequest.java` | Create/update request DTO with bean validation |
| `application/dto/LabourDesignationResponse.java` | Listing/detail DTO incl. optional `deployment` block |
| `application/dto/ProjectLabourDeploymentRequest.java` | Create/update request DTO |
| `application/dto/ProjectLabourDeploymentResponse.java` | Deployment row joined with designation |
| `application/dto/LabourCategorySummary.java` | Per-category roll-up |
| `application/dto/LabourMasterDashboardSummary.java` | Top-level KPI summary |
| `application/dto/LabourGradeReference.java` | Static A–E reference response |
| `application/dto/LabourCategoryReference.java` | Static category reference response |
| `application/service/LabourDesignationService.java` | Catalogue CRUD, list/filter, code-validation, soft delete |
| `application/service/ProjectLabourDeploymentService.java` | Deployment CRUD, summaries, bulk-seed |
| `presentation/controller/LabourDesignationController.java` | `/v1/labour-designations/**` |
| `presentation/controller/ProjectLabourDeploymentController.java` | `/v1/projects/{projectId}/labour-deployments/**` |

### Backend — `backend/bipros-resource/src/test/java/com/bipros/resource/`

| File | Responsibility |
|---|---|
| `application/service/LabourDesignationServiceTest.java` | Unit tests for catalogue service |
| `application/service/ProjectLabourDeploymentServiceTest.java` | Unit tests for deployment service |
| `presentation/controller/LabourDesignationControllerIT.java` | `@SpringBootTest` integration |
| `presentation/controller/ProjectLabourDeploymentControllerIT.java` | `@SpringBootTest` integration |

### Backend — `backend/bipros-api/`

| File | Responsibility |
|---|---|
| `src/main/java/com/bipros/api/config/seeder/OmanLabourMasterSeeder.java` | Boot seeder under `seed` profile |
| `src/main/resources/oman-labour-master.json` | The 44 Oman designations dataset |
| `src/main/resources/db/changelog/045-labour-master-tables.yaml` | Liquibase changelog (production schema source-of-truth) |

### Frontend — `frontend/src/`

| File | Responsibility |
|---|---|
| `lib/api/labourMasterApi.ts` | API client (designations + deployments namespaces) |
| `app/(app)/labour-master/layout.tsx` | Module shell: tabs (Dashboard / Cards / Table / Reference) |
| `app/(app)/labour-master/page.tsx` | Screen 1 — Dashboard |
| `app/(app)/labour-master/cards/page.tsx` | Screens 2 & 3 — Cards View |
| `app/(app)/labour-master/table/page.tsx` | Screen 4 — Table View |
| `app/(app)/labour-master/new/page.tsx` | Screen 6 — Add form |
| `app/(app)/labour-master/[code]/page.tsx` | Designation detail page (deep-linked Screen 5) |
| `app/(app)/labour-master/reference/page.tsx` | Grade Reference + Oman regulatory notes |
| `components/labour-master/KpiTiles.tsx` | 5 dashboard KPI tiles |
| `components/labour-master/WorkforceCategoryBarChart.tsx` | Recharts horizontal bar |
| `components/labour-master/CategoryFilterBar.tsx` | Category + grade + search controls |
| `components/labour-master/WorkerCard.tsx` | One designation card |
| `components/labour-master/CategoryCardsSection.tsx` | Header + grid of cards per category |
| `components/labour-master/WorkerTable.tsx` | Compact tabular register |
| `components/labour-master/WorkerDetailModal.tsx` | Screen 5 modal |
| `components/labour-master/AddDesignationForm.tsx` | Screen 6 form |
| `components/labour-master/WorkforceSummaryTable.tsx` | Per-category roll-up table |
| `components/labour-master/GradeReferenceTable.tsx` | A–E reference + regulatory notes |
| `components/labour-master/labourMasterTokens.ts` | Tailwind colour tokens for category accents + grade badges |

### Frontend — tests

| File | Responsibility |
|---|---|
| `frontend/e2e/tests/labour-master.spec.ts` | Playwright e2e for the 6 screens |

---

## Conventions used by every task

- **Branch:** all work goes on `main` (or whatever branch the executing agent is on); commit after each task.
- **JSONB columns:** use `@JdbcTypeCode(SqlTypes.JSON)` plus `@Column(columnDefinition = "jsonb")`. Pattern is in `bipros-gis/.../WbsPolygon.java`.
- **Audit fields:** every entity extends `com.bipros.common.model.BaseEntity`; do not redeclare `id`, `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, or `version`.
- **API envelope:** every endpoint returns `ApiResponse<T>` (or `ApiResponse<PagedResponse<T>>`). `ApiResponse.ok(data)` for the success path. Errors bubble through `bipros-common`'s `GlobalExceptionHandler`.
- **Security:** controllers annotate at class level: `@PreAuthorize("hasAnyRole('ADMIN')")` for the catalogue (matches `RoleController`); deployments use the same. Read endpoints can later be opened to authenticated users — out of scope for this plan.
- **Logging:** controllers use Lombok `@Slf4j` and `log.info` on each request, mirroring `RoleController`.
- **Profile gating:** the seeder uses `@Profile("seed")` (matches `9800652 refactor(seeders): gate seeders behind 'seed' profile`) and `@Order(140)` to run after IC-PMS and NHAI seeders. Idempotent: short-circuit if `labourDesignationRepository.count() > 0`.
- **Frontend project context:** read the active project from the existing project-selector store (the same one `permits/`, `risk/`, `resources/` use). The implementation step that wires this includes the import line — copy it from `permits/page.tsx`.
- **Next.js 16 caveat:** before each frontend task that touches App Router primitives (`layout.tsx`, server vs client components, route handlers), open the relevant page under `frontend/node_modules/next/dist/docs/` first. Do not assume Next 14/15 conventions.
- **Test conventions (backend):** unit tests use Mockito; integration tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, and seed an admin user via the existing test-fixture pattern (see any `*ControllerIT` already in the resource module — e.g., `LabourReturnControllerIT` if present, otherwise a controller IT in another module).
- **Commit style:** prefix `feat(labour-master): ...` for production code, `test(labour-master): ...` for tests, `chore(labour-master): ...` for seeders/scripts. End every commit message with the standard `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` line.

---

## Phase 0 — Pre-flight

### Task 0.1: Read the spec end-to-end

**Files:** none (read-only)

- [ ] **Step 1:** Open `docs/superpowers/specs/2026-04-28-labour-master-module-design.md` and read sections 1–10 in full. The spec is the contract; the plan below is the recipe.

- [ ] **Step 2:** Open `docs/superpowers/specs/2026-04-28-labour-master-module-design.md` and skim section 9 ("Open implementation choices"). This plan resolves them as: dataset stored as a JSON resource (§ Phase 4), no admin "re-seed" button (script-only), Grade Reference lives at `/labour-master/reference` as a separate route.

### Task 0.2: Verify environment

**Files:** none

- [ ] **Step 1:** From repo root, run:
  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`. If not, do not proceed; fix the env first.

- [ ] **Step 2:** Run:
  ```bash
  (cd frontend && pnpm install)
  ```
  Expected: lockfile up to date, no errors.

- [ ] **Step 3:** Confirm Postgres is up and the `resource` schema exists:
  ```bash
  psql -h localhost -U bipros -d bipros -c "\dn" | grep resource
  ```
  Expected: a row containing `resource`. If not, run `docker compose up -d` first.

---

## Phase 1 — Domain enums

### Task 1.1: `LabourCategory` enum

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/LabourCategory.java`

- [ ] **Step 1: Write the file**

  ```java
  package com.bipros.resource.domain.model;

  public enum LabourCategory {
      SITE_MANAGEMENT      ("SM", "Site Management"),
      PLANT_EQUIPMENT      ("PO", "Plant & Equipment Operators"),
      SKILLED_LABOUR       ("SL", "Skilled Labour"),
      SEMI_SKILLED_LABOUR  ("SS", "Semi-Skilled Labour"),
      GENERAL_UNSKILLED    ("GL", "General / Unskilled Labour");

      private final String codePrefix;
      private final String displayName;

      LabourCategory(String codePrefix, String displayName) {
          this.codePrefix = codePrefix;
          this.displayName = displayName;
      }

      public String getCodePrefix() { return codePrefix; }
      public String getDisplayName() { return displayName; }

      public static LabourCategory fromCodePrefix(String prefix) {
          for (LabourCategory c : values()) {
              if (c.codePrefix.equals(prefix)) return c;
          }
          throw new IllegalArgumentException("Unknown labour category prefix: " + prefix);
      }
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/LabourCategory.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add LabourCategory enum

  Five categories with stable code prefixes (SM/PO/SL/SS/GL) for the
  Oman labour catalogue.

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 1.2: `LabourGrade` enum

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/LabourGrade.java`

- [ ] **Step 1: Write the file**

  ```java
  package com.bipros.resource.domain.model;

  public enum LabourGrade {
      A("Senior Management / Principal Engineer", "OMR 95 – 125/day",
        "15+ years experience, PMP/FIDIC/RE certified, contract authority, direct client interface."),
      B("Mid-Level Engineer / Specialist", "OMR 50 – 70/day",
        "7–12 years experience, professional certification (NEBOSH/RICS/AWS), team supervision role."),
      C("Skilled Tradesperson / Senior Operator", "OMR 26 – 48/day",
        "4–8 years experience, trade-tested Grade II or equipment license, independent work execution."),
      D("Semi-Skilled Worker / Junior Operator", "OMR 16 – 28/day",
        "2–4 years experience, site safety card mandatory, supervised task execution."),
      E("General / Unskilled Labour", "OMR 10 – 14/day",
        "1+ year experience, site induction card, works under direct supervision at all times.");

      private final String classification;
      private final String dailyRateRange;
      private final String description;

      LabourGrade(String classification, String dailyRateRange, String description) {
          this.classification = classification;
          this.dailyRateRange = dailyRateRange;
          this.description = description;
      }

      public String getClassification() { return classification; }
      public String getDailyRateRange() { return dailyRateRange; }
      public String getDescription() { return description; }
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/LabourGrade.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add LabourGrade enum with Oman band metadata

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 1.3: `NationalityType` enum

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/NationalityType.java`

- [ ] **Step 1: Write the file**

  ```java
  package com.bipros.resource.domain.model;

  public enum NationalityType {
      OMANI,
      EXPAT,
      OMANI_OR_EXPAT
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/NationalityType.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add NationalityType enum

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 2 — Entities & repositories

### Task 2.1: `LabourDesignation` entity

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/LabourDesignation.java`

- [ ] **Step 1: Write the file**

  ```java
  package com.bipros.resource.domain.model;

  import com.bipros.common.model.BaseEntity;
  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.EnumType;
  import jakarta.persistence.Enumerated;
  import jakarta.persistence.Index;
  import jakarta.persistence.Table;
  import jakarta.persistence.UniqueConstraint;
  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Builder.Default;
  import lombok.Getter;
  import lombok.NoArgsConstructor;
  import lombok.Setter;
  import org.hibernate.annotations.JdbcTypeCode;
  import org.hibernate.type.SqlTypes;

  import java.math.BigDecimal;
  import java.util.ArrayList;
  import java.util.List;

  @Entity
  @Table(
      name = "labour_designations",
      schema = "resource",
      uniqueConstraints = @UniqueConstraint(name = "uk_labour_designation_code", columnNames = "code"),
      indexes = {
          @Index(name = "idx_labour_designation_category", columnList = "category"),
          @Index(name = "idx_labour_designation_grade", columnList = "grade"),
          @Index(name = "idx_labour_designation_status", columnList = "status")
      })
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public class LabourDesignation extends BaseEntity {

      @Column(nullable = false, length = 20, unique = true)
      private String code;

      @Column(nullable = false, length = 100)
      private String designation;

      @Enumerated(EnumType.STRING)
      @Column(nullable = false, length = 30)
      private LabourCategory category;

      @Column(nullable = false, length = 80)
      private String trade;

      @Enumerated(EnumType.STRING)
      @Column(nullable = false, length = 2)
      private LabourGrade grade;

      @Enumerated(EnumType.STRING)
      @Column(nullable = false, length = 20)
      private NationalityType nationality;

      @Column(name = "experience_years_min", nullable = false)
      private Integer experienceYearsMin;

      @Column(name = "default_daily_rate", nullable = false, precision = 10, scale = 2)
      private BigDecimal defaultDailyRate;

      @Column(nullable = false, length = 3)
      @Default
      private String currency = "OMR";

      @JdbcTypeCode(SqlTypes.JSON)
      @Column(columnDefinition = "jsonb", nullable = false)
      @Default
      private List<String> skills = new ArrayList<>();

      @JdbcTypeCode(SqlTypes.JSON)
      @Column(columnDefinition = "jsonb", nullable = false)
      @Default
      private List<String> certifications = new ArrayList<>();

      @Column(name = "key_role_summary", length = 500)
      private String keyRoleSummary;

      @Column(nullable = false, length = 20)
      @Default
      private String status = "ACTIVE";

      @Column(name = "sort_order", nullable = false)
      @Default
      private Integer sortOrder = 0;
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/LabourDesignation.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add LabourDesignation entity

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 2.2: `ProjectLabourDeployment` entity

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/ProjectLabourDeployment.java`

- [ ] **Step 1: Write the file**

  ```java
  package com.bipros.resource.domain.model;

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

  @Entity
  @Table(
      name = "project_labour_deployments",
      schema = "resource",
      uniqueConstraints = @UniqueConstraint(
          name = "uk_project_labour_deployment_project_designation",
          columnNames = {"project_id", "designation_id"}),
      indexes = {
          @Index(name = "idx_project_labour_deployment_project", columnList = "project_id"),
          @Index(name = "idx_project_labour_deployment_designation", columnList = "designation_id")
      })
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public class ProjectLabourDeployment extends BaseEntity {

      @Column(name = "project_id", nullable = false)
      private UUID projectId;

      @Column(name = "designation_id", nullable = false)
      private UUID designationId;

      @Column(name = "worker_count", nullable = false)
      private Integer workerCount;

      @Column(name = "actual_daily_rate", precision = 10, scale = 2)
      private BigDecimal actualDailyRate;

      @Column(length = 500)
      private String notes;
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/model/ProjectLabourDeployment.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add ProjectLabourDeployment entity

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 2.3: Repositories

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/repository/LabourDesignationRepository.java`
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/domain/repository/ProjectLabourDeploymentRepository.java`

- [ ] **Step 1: Write `LabourDesignationRepository.java`**

  ```java
  package com.bipros.resource.domain.repository;

  import com.bipros.resource.domain.model.LabourCategory;
  import com.bipros.resource.domain.model.LabourDesignation;
  import com.bipros.resource.domain.model.LabourGrade;
  import org.springframework.data.domain.Page;
  import org.springframework.data.domain.Pageable;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.data.jpa.repository.Query;
  import org.springframework.data.repository.query.Param;
  import org.springframework.stereotype.Repository;

  import java.util.List;
  import java.util.Optional;
  import java.util.UUID;

  @Repository
  public interface LabourDesignationRepository extends JpaRepository<LabourDesignation, UUID> {

      Optional<LabourDesignation> findByCode(String code);

      boolean existsByCode(String code);

      List<LabourDesignation> findAllByOrderBySortOrderAscCodeAsc();

      @Query("""
          SELECT d FROM LabourDesignation d
          WHERE (:category IS NULL OR d.category = :category)
            AND (:grade    IS NULL OR d.grade    = :grade)
            AND (:status   IS NULL OR d.status   = :status)
            AND (:q IS NULL OR LOWER(d.code) LIKE LOWER(CONCAT('%', :q, '%'))
                            OR LOWER(d.designation) LIKE LOWER(CONCAT('%', :q, '%'))
                            OR LOWER(d.trade) LIKE LOWER(CONCAT('%', :q, '%')))
          """)
      Page<LabourDesignation> search(@Param("category") LabourCategory category,
                                     @Param("grade")    LabourGrade grade,
                                     @Param("status")   String status,
                                     @Param("q")        String q,
                                     Pageable pageable);
  }
  ```

- [ ] **Step 2: Write `ProjectLabourDeploymentRepository.java`**

  ```java
  package com.bipros.resource.domain.repository;

  import com.bipros.resource.domain.model.ProjectLabourDeployment;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.stereotype.Repository;

  import java.util.List;
  import java.util.Optional;
  import java.util.UUID;

  @Repository
  public interface ProjectLabourDeploymentRepository extends JpaRepository<ProjectLabourDeployment, UUID> {

      List<ProjectLabourDeployment> findAllByProjectId(UUID projectId);

      Optional<ProjectLabourDeployment> findByProjectIdAndDesignationId(UUID projectId, UUID designationId);

      boolean existsByProjectIdAndDesignationId(UUID projectId, UUID designationId);

      boolean existsByDesignationId(UUID designationId);

      long countByProjectId(UUID projectId);

      void deleteAllByProjectId(UUID projectId);
  }
  ```

- [ ] **Step 3: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/domain/repository/LabourDesignationRepository.java \
          backend/bipros-resource/src/main/java/com/bipros/resource/domain/repository/ProjectLabourDeploymentRepository.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add labour designation + deployment repositories

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 2.4: Smoke-test the schema generation

**Files:** none (manual verification)

- [ ] **Step 1: Boot the API**

  In a separate terminal:
  ```bash
  (cd backend && mvn -q -pl bipros-api -am spring-boot:run)
  ```
  Wait for `Started BiprosApplication`.

- [ ] **Step 2: Verify the tables exist**

  ```bash
  psql -h localhost -U bipros -d bipros -c "\d resource.labour_designations" | head -30
  psql -h localhost -U bipros -d bipros -c "\d resource.project_labour_deployments" | head -20
  ```
  Expected: each table prints with the columns and indexes from §4.2/4.3 of the spec; both `skills` and `certifications` show as `jsonb`.

- [ ] **Step 3: Stop the backend**

  Ctrl-C the running backend. No commit (read-only verification).

---

## Phase 3 — DTOs

### Task 3.1: Catalogue request/response DTOs

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/LabourDesignationRequest.java`
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/LabourDesignationResponse.java`

- [ ] **Step 1: Write `LabourDesignationRequest.java`**

  ```java
  package com.bipros.resource.application.dto;

  import com.bipros.resource.domain.model.LabourCategory;
  import com.bipros.resource.domain.model.LabourGrade;
  import com.bipros.resource.domain.model.NationalityType;
  import jakarta.validation.constraints.Min;
  import jakarta.validation.constraints.NotBlank;
  import jakarta.validation.constraints.NotNull;
  import jakarta.validation.constraints.Pattern;
  import jakarta.validation.constraints.PositiveOrZero;
  import jakarta.validation.constraints.Size;

  import java.math.BigDecimal;
  import java.util.List;

  public record LabourDesignationRequest(
      @NotBlank @Pattern(regexp = "^(SM|PO|SL|SS|GL)-\\d{3}$",
                         message = "code must match (SM|PO|SL|SS|GL)-NNN")
      String code,

      @NotBlank @Size(max = 100) String designation,
      @NotNull LabourCategory category,
      @NotBlank @Size(max = 80) String trade,
      @NotNull LabourGrade grade,
      @NotNull NationalityType nationality,
      @NotNull @Min(0) Integer experienceYearsMin,
      @NotNull @PositiveOrZero BigDecimal defaultDailyRate,
      @Size(max = 3) String currency,
      List<String> skills,
      List<String> certifications,
      @Size(max = 500) String keyRoleSummary,
      @Size(max = 20)  String status,
      Integer sortOrder
  ) {}
  ```

- [ ] **Step 2: Write `LabourDesignationResponse.java`**

  ```java
  package com.bipros.resource.application.dto;

  import com.bipros.resource.domain.model.LabourCategory;
  import com.bipros.resource.domain.model.LabourGrade;
  import com.bipros.resource.domain.model.NationalityType;

  import java.math.BigDecimal;
  import java.util.List;
  import java.util.UUID;

  public record LabourDesignationResponse(
      UUID id,
      String code,
      String designation,
      LabourCategory category,
      String categoryDisplay,
      String codePrefix,
      String trade,
      LabourGrade grade,
      NationalityType nationality,
      Integer experienceYearsMin,
      BigDecimal defaultDailyRate,
      String currency,
      List<String> skills,
      List<String> certifications,
      String keyRoleSummary,
      String status,
      Integer sortOrder,
      DeploymentBlock deployment
  ) {
      public record DeploymentBlock(
          UUID id,
          Integer workerCount,
          BigDecimal actualDailyRate,
          BigDecimal effectiveRate,
          BigDecimal dailyCost,
          String notes
      ) {}
  }
  ```

- [ ] **Step 3: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/LabourDesignationRequest.java \
          backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/LabourDesignationResponse.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add LabourDesignation request/response DTOs

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 3.2: Deployment request/response + summary DTOs

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/ProjectLabourDeploymentRequest.java`
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/ProjectLabourDeploymentResponse.java`
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/LabourCategorySummary.java`
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/LabourMasterDashboardSummary.java`
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/LabourGradeReference.java`
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/LabourCategoryReference.java`

- [ ] **Step 1: Write `ProjectLabourDeploymentRequest.java`**

  ```java
  package com.bipros.resource.application.dto;

  import jakarta.validation.constraints.NotNull;
  import jakarta.validation.constraints.PositiveOrZero;
  import jakarta.validation.constraints.Size;

  import java.math.BigDecimal;
  import java.util.UUID;

  public record ProjectLabourDeploymentRequest(
      @NotNull UUID designationId,
      @NotNull @PositiveOrZero Integer workerCount,
      @PositiveOrZero BigDecimal actualDailyRate,
      @Size(max = 500) String notes
  ) {}
  ```

- [ ] **Step 2: Write `ProjectLabourDeploymentResponse.java`**

  ```java
  package com.bipros.resource.application.dto;

  import java.math.BigDecimal;
  import java.util.UUID;

  public record ProjectLabourDeploymentResponse(
      UUID id,
      UUID projectId,
      UUID designationId,
      Integer workerCount,
      BigDecimal actualDailyRate,
      BigDecimal effectiveRate,
      BigDecimal dailyCost,
      String notes,
      LabourDesignationResponse designation
  ) {}
  ```

- [ ] **Step 3: Write `LabourCategorySummary.java`**

  ```java
  package com.bipros.resource.application.dto;

  import com.bipros.resource.domain.model.LabourCategory;

  import java.math.BigDecimal;

  public record LabourCategorySummary(
      LabourCategory category,
      String categoryDisplay,
      String codePrefix,
      Integer designationCount,
      Integer workerCount,
      BigDecimal dailyCost,
      String gradeRange,
      String dailyRateRange,
      String keyRolesSummary
  ) {}
  ```

- [ ] **Step 4: Write `LabourMasterDashboardSummary.java`**

  ```java
  package com.bipros.resource.application.dto;

  import java.math.BigDecimal;
  import java.util.List;
  import java.util.UUID;

  public record LabourMasterDashboardSummary(
      UUID projectId,
      Integer totalDesignations,
      Integer totalWorkforce,
      BigDecimal dailyPayroll,
      String currency,
      Integer skillCategoryCount,
      NationalityMix nationalityMix,
      List<LabourCategorySummary> byCategory
  ) {
      public record NationalityMix(Integer omani, Integer expat, Integer omaniOrExpat) {}
  }
  ```

- [ ] **Step 5: Write `LabourGradeReference.java`**

  ```java
  package com.bipros.resource.application.dto;

  import com.bipros.resource.domain.model.LabourGrade;

  public record LabourGradeReference(
      LabourGrade grade,
      String classification,
      String dailyRateRange,
      String description
  ) {}
  ```

- [ ] **Step 6: Write `LabourCategoryReference.java`**

  ```java
  package com.bipros.resource.application.dto;

  import com.bipros.resource.domain.model.LabourCategory;

  public record LabourCategoryReference(
      LabourCategory category,
      String codePrefix,
      String displayName
  ) {}
  ```

- [ ] **Step 7: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/application/dto/
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add deployment + summary DTOs

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 4 — Services (TDD)

### Task 4.1: `LabourDesignationService` — failing test first

**Files:**
- Create: `backend/bipros-resource/src/test/java/com/bipros/resource/application/service/LabourDesignationServiceTest.java`

- [ ] **Step 1: Write the failing test**

  ```java
  package com.bipros.resource.application.service;

  import com.bipros.resource.application.dto.LabourDesignationRequest;
  import com.bipros.resource.application.dto.LabourDesignationResponse;
  import com.bipros.resource.domain.model.LabourCategory;
  import com.bipros.resource.domain.model.LabourDesignation;
  import com.bipros.resource.domain.model.LabourGrade;
  import com.bipros.resource.domain.model.NationalityType;
  import com.bipros.resource.domain.repository.LabourDesignationRepository;
  import com.bipros.resource.domain.repository.ProjectLabourDeploymentRepository;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.math.BigDecimal;
  import java.util.List;
  import java.util.Optional;
  import java.util.UUID;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.when;

  @ExtendWith(MockitoExtension.class)
  class LabourDesignationServiceTest {

      @Mock LabourDesignationRepository designationRepo;
      @Mock ProjectLabourDeploymentRepository deploymentRepo;

      @InjectMocks LabourDesignationService service;

      private LabourDesignationRequest baseRequest() {
          return new LabourDesignationRequest(
              "SM-001", "Project Manager",
              LabourCategory.SITE_MANAGEMENT, "Civil Engineering",
              LabourGrade.A, NationalityType.OMANI_OR_EXPAT,
              15, new BigDecimal("125.00"), "OMR",
              List.of("Project Planning", "FIDIC"),
              List.of("PMP", "B.Eng Civil"),
              null, null, 1);
      }

      @Test
      void create_persistsAndReturnsResponse() {
          when(designationRepo.existsByCode("SM-001")).thenReturn(false);
          when(designationRepo.save(any(LabourDesignation.class)))
              .thenAnswer(inv -> {
                  LabourDesignation d = inv.getArgument(0);
                  d.setId(UUID.randomUUID());
                  return d;
              });

          LabourDesignationResponse out = service.create(baseRequest());

          assertThat(out.code()).isEqualTo("SM-001");
          assertThat(out.category()).isEqualTo(LabourCategory.SITE_MANAGEMENT);
          assertThat(out.codePrefix()).isEqualTo("SM");
          assertThat(out.skills()).containsExactly("Project Planning", "FIDIC");
      }

      @Test
      void create_rejectsCodePrefixCategoryMismatch() {
          LabourDesignationRequest bad = new LabourDesignationRequest(
              "PO-001", "Project Manager",
              LabourCategory.SITE_MANAGEMENT, "Civil Engineering",
              LabourGrade.A, NationalityType.OMANI_OR_EXPAT,
              15, new BigDecimal("125.00"), "OMR",
              List.of(), List.of(), null, null, 0);

          assertThatThrownBy(() -> service.create(bad))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("prefix");
      }

      @Test
      void create_rejectsDuplicateCode() {
          when(designationRepo.existsByCode("SM-001")).thenReturn(true);
          assertThatThrownBy(() -> service.create(baseRequest()))
              .isInstanceOf(IllegalStateException.class);
      }

      @Test
      void delete_softDeletesByDefault() {
          UUID id = UUID.randomUUID();
          LabourDesignation existing = LabourDesignation.builder().code("SM-001")
              .designation("X").category(LabourCategory.SITE_MANAGEMENT).trade("T")
              .grade(LabourGrade.A).nationality(NationalityType.EXPAT)
              .experienceYearsMin(1).defaultDailyRate(BigDecimal.ONE)
              .currency("OMR").skills(List.of()).certifications(List.of())
              .status("ACTIVE").sortOrder(0).build();
          existing.setId(id);

          when(designationRepo.findById(id)).thenReturn(Optional.of(existing));
          when(deploymentRepo.existsByDesignationId(id)).thenReturn(true);
          when(designationRepo.save(any(LabourDesignation.class))).thenAnswer(inv -> inv.getArgument(0));

          service.delete(id);

          assertThat(existing.getStatus()).isEqualTo("INACTIVE");
      }
  }
  ```

- [ ] **Step 2: Run the test to verify it fails (compile-error level)**

  ```bash
  (cd backend && mvn -q -pl bipros-resource test -Dtest=LabourDesignationServiceTest)
  ```
  Expected: compile failure — `LabourDesignationService` does not exist yet.

### Task 4.2: `LabourDesignationService` — minimal implementation

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/service/LabourDesignationService.java`

- [ ] **Step 1: Write the file**

  ```java
  package com.bipros.resource.application.service;

  import com.bipros.common.dto.PagedResponse;
  import com.bipros.resource.application.dto.LabourCategoryReference;
  import com.bipros.resource.application.dto.LabourDesignationRequest;
  import com.bipros.resource.application.dto.LabourDesignationResponse;
  import com.bipros.resource.application.dto.LabourGradeReference;
  import com.bipros.resource.domain.model.LabourCategory;
  import com.bipros.resource.domain.model.LabourDesignation;
  import com.bipros.resource.domain.model.LabourGrade;
  import com.bipros.resource.domain.repository.LabourDesignationRepository;
  import com.bipros.resource.domain.repository.ProjectLabourDeploymentRepository;
  import jakarta.persistence.EntityNotFoundException;
  import lombok.RequiredArgsConstructor;
  import org.springframework.data.domain.Page;
  import org.springframework.data.domain.Pageable;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;

  import java.util.Arrays;
  import java.util.List;
  import java.util.UUID;

  @Service
  @RequiredArgsConstructor
  public class LabourDesignationService {

      private final LabourDesignationRepository designationRepo;
      private final ProjectLabourDeploymentRepository deploymentRepo;

      @Transactional
      public LabourDesignationResponse create(LabourDesignationRequest req) {
          validatePrefixMatchesCategory(req.code(), req.category());
          if (designationRepo.existsByCode(req.code())) {
              throw new IllegalStateException("Designation code already exists: " + req.code());
          }
          LabourDesignation entity = LabourDesignation.builder()
              .code(req.code())
              .designation(req.designation())
              .category(req.category())
              .trade(req.trade())
              .grade(req.grade())
              .nationality(req.nationality())
              .experienceYearsMin(req.experienceYearsMin())
              .defaultDailyRate(req.defaultDailyRate())
              .currency(req.currency() == null ? "OMR" : req.currency())
              .skills(req.skills() == null ? List.of() : req.skills())
              .certifications(req.certifications() == null ? List.of() : req.certifications())
              .keyRoleSummary(req.keyRoleSummary())
              .status(req.status() == null ? "ACTIVE" : req.status())
              .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
              .build();
          return toResponse(designationRepo.save(entity));
      }

      @Transactional
      public LabourDesignationResponse update(UUID id, LabourDesignationRequest req) {
          validatePrefixMatchesCategory(req.code(), req.category());
          LabourDesignation existing = designationRepo.findById(id)
              .orElseThrow(() -> new EntityNotFoundException("LabourDesignation not found: " + id));
          if (!existing.getCode().equals(req.code()) && designationRepo.existsByCode(req.code())) {
              throw new IllegalStateException("Designation code already exists: " + req.code());
          }
          existing.setCode(req.code());
          existing.setDesignation(req.designation());
          existing.setCategory(req.category());
          existing.setTrade(req.trade());
          existing.setGrade(req.grade());
          existing.setNationality(req.nationality());
          existing.setExperienceYearsMin(req.experienceYearsMin());
          existing.setDefaultDailyRate(req.defaultDailyRate());
          if (req.currency() != null) existing.setCurrency(req.currency());
          if (req.skills() != null) existing.setSkills(req.skills());
          if (req.certifications() != null) existing.setCertifications(req.certifications());
          existing.setKeyRoleSummary(req.keyRoleSummary());
          if (req.status() != null) existing.setStatus(req.status());
          if (req.sortOrder() != null) existing.setSortOrder(req.sortOrder());
          return toResponse(designationRepo.save(existing));
      }

      @Transactional(readOnly = true)
      public LabourDesignationResponse get(UUID id) {
          return toResponse(designationRepo.findById(id)
              .orElseThrow(() -> new EntityNotFoundException("LabourDesignation not found: " + id)));
      }

      @Transactional(readOnly = true)
      public LabourDesignationResponse getByCode(String code) {
          return toResponse(designationRepo.findByCode(code)
              .orElseThrow(() -> new EntityNotFoundException("LabourDesignation not found: " + code)));
      }

      @Transactional(readOnly = true)
      public PagedResponse<LabourDesignationResponse> search(
              LabourCategory category, LabourGrade grade, String status, String q, Pageable pageable) {
          Page<LabourDesignation> page = designationRepo.search(category, grade, status, q, pageable);
          List<LabourDesignationResponse> rows = page.getContent().stream().map(this::toResponse).toList();
          return PagedResponse.of(rows, page.getTotalElements(), page.getTotalPages(),
              page.getNumber(), page.getSize());
      }

      @Transactional
      public void delete(UUID id) {
          LabourDesignation existing = designationRepo.findById(id)
              .orElseThrow(() -> new EntityNotFoundException("LabourDesignation not found: " + id));
          if (deploymentRepo.existsByDesignationId(id)) {
              existing.setStatus("INACTIVE");
              designationRepo.save(existing);
              return;
          }
          designationRepo.delete(existing);
      }

      public List<LabourCategoryReference> listCategories() {
          return Arrays.stream(LabourCategory.values())
              .map(c -> new LabourCategoryReference(c, c.getCodePrefix(), c.getDisplayName()))
              .toList();
      }

      public List<LabourGradeReference> listGrades() {
          return Arrays.stream(LabourGrade.values())
              .map(g -> new LabourGradeReference(g, g.getClassification(),
                                                 g.getDailyRateRange(), g.getDescription()))
              .toList();
      }

      // ── helpers ─────────────────────────────────────────────────

      private void validatePrefixMatchesCategory(String code, LabourCategory category) {
          if (code == null || code.length() < 3) {
              throw new IllegalArgumentException("Invalid code: " + code);
          }
          String prefix = code.substring(0, 2);
          if (!prefix.equals(category.getCodePrefix())) {
              throw new IllegalArgumentException(
                  "Code prefix '" + prefix + "' does not match category " + category);
          }
      }

      LabourDesignationResponse toResponse(LabourDesignation d) {
          return new LabourDesignationResponse(
              d.getId(), d.getCode(), d.getDesignation(), d.getCategory(),
              d.getCategory() == null ? null : d.getCategory().getDisplayName(),
              d.getCategory() == null ? null : d.getCategory().getCodePrefix(),
              d.getTrade(), d.getGrade(), d.getNationality(),
              d.getExperienceYearsMin(), d.getDefaultDailyRate(), d.getCurrency(),
              d.getSkills(), d.getCertifications(), d.getKeyRoleSummary(),
              d.getStatus(), d.getSortOrder(),
              null /* deployment block populated by deployment service */);
      }
  }
  ```

- [ ] **Step 2: Run the test to verify it passes**

  ```bash
  (cd backend && mvn -q -pl bipros-resource test -Dtest=LabourDesignationServiceTest)
  ```
  Expected: `BUILD SUCCESS`, all 4 tests green.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/application/service/LabourDesignationService.java \
          backend/bipros-resource/src/test/java/com/bipros/resource/application/service/LabourDesignationServiceTest.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add LabourDesignationService with CRUD + soft-delete

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 4.3: `ProjectLabourDeploymentService` — failing test

**Files:**
- Create: `backend/bipros-resource/src/test/java/com/bipros/resource/application/service/ProjectLabourDeploymentServiceTest.java`

- [ ] **Step 1: Write the failing test**

  ```java
  package com.bipros.resource.application.service;

  import com.bipros.resource.application.dto.LabourMasterDashboardSummary;
  import com.bipros.resource.application.dto.ProjectLabourDeploymentRequest;
  import com.bipros.resource.application.dto.ProjectLabourDeploymentResponse;
  import com.bipros.resource.domain.model.LabourCategory;
  import com.bipros.resource.domain.model.LabourDesignation;
  import com.bipros.resource.domain.model.LabourGrade;
  import com.bipros.resource.domain.model.NationalityType;
  import com.bipros.resource.domain.model.ProjectLabourDeployment;
  import com.bipros.resource.domain.repository.LabourDesignationRepository;
  import com.bipros.resource.domain.repository.ProjectLabourDeploymentRepository;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.math.BigDecimal;
  import java.util.List;
  import java.util.Optional;
  import java.util.UUID;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.when;

  @ExtendWith(MockitoExtension.class)
  class ProjectLabourDeploymentServiceTest {

      @Mock LabourDesignationRepository designationRepo;
      @Mock ProjectLabourDeploymentRepository deploymentRepo;
      @InjectMocks ProjectLabourDeploymentService service;

      private LabourDesignation pm() {
          LabourDesignation d = LabourDesignation.builder()
              .code("SM-001").designation("Project Manager")
              .category(LabourCategory.SITE_MANAGEMENT).trade("Civil")
              .grade(LabourGrade.A).nationality(NationalityType.OMANI_OR_EXPAT)
              .experienceYearsMin(15).defaultDailyRate(new BigDecimal("125.00"))
              .currency("OMR").skills(List.of()).certifications(List.of())
              .status("ACTIVE").sortOrder(1).build();
          d.setId(UUID.randomUUID());
          return d;
      }

      @Test
      void create_usesActualRateWhenProvided() {
          UUID projectId = UUID.randomUUID();
          LabourDesignation d = pm();
          when(designationRepo.findById(d.getId())).thenReturn(Optional.of(d));
          when(deploymentRepo.existsByProjectIdAndDesignationId(projectId, d.getId())).thenReturn(false);
          when(deploymentRepo.save(any(ProjectLabourDeployment.class)))
              .thenAnswer(inv -> { ProjectLabourDeployment p = inv.getArgument(0);
                                    p.setId(UUID.randomUUID()); return p; });

          ProjectLabourDeploymentResponse out = service.create(projectId,
              new ProjectLabourDeploymentRequest(d.getId(), 1, new BigDecimal("130.00"), null));

          assertThat(out.effectiveRate()).isEqualByComparingTo("130.00");
          assertThat(out.dailyCost()).isEqualByComparingTo("130.00");
      }

      @Test
      void create_fallsBackToDesignationDefaultRate() {
          UUID projectId = UUID.randomUUID();
          LabourDesignation d = pm();
          when(designationRepo.findById(d.getId())).thenReturn(Optional.of(d));
          when(deploymentRepo.existsByProjectIdAndDesignationId(projectId, d.getId())).thenReturn(false);
          when(deploymentRepo.save(any(ProjectLabourDeployment.class)))
              .thenAnswer(inv -> { ProjectLabourDeployment p = inv.getArgument(0);
                                    p.setId(UUID.randomUUID()); return p; });

          ProjectLabourDeploymentResponse out = service.create(projectId,
              new ProjectLabourDeploymentRequest(d.getId(), 2, null, null));

          assertThat(out.effectiveRate()).isEqualByComparingTo("125.00");
          assertThat(out.dailyCost()).isEqualByComparingTo("250.00");
      }

      @Test
      void dashboardSummary_aggregatesAcrossCategories() {
          UUID projectId = UUID.randomUUID();
          LabourDesignation d = pm();
          ProjectLabourDeployment dep = ProjectLabourDeployment.builder()
              .projectId(projectId).designationId(d.getId())
              .workerCount(1).actualDailyRate(null).build();
          dep.setId(UUID.randomUUID());

          when(deploymentRepo.findAllByProjectId(projectId)).thenReturn(List.of(dep));
          when(designationRepo.findAllById(List.of(d.getId()))).thenReturn(List.of(d));

          LabourMasterDashboardSummary out = service.dashboard(projectId);

          assertThat(out.totalDesignations()).isEqualTo(1);
          assertThat(out.totalWorkforce()).isEqualTo(1);
          assertThat(out.dailyPayroll()).isEqualByComparingTo("125.00");
          assertThat(out.skillCategoryCount()).isEqualTo(1);
          assertThat(out.byCategory()).hasSize(1);
      }
  }
  ```

- [ ] **Step 2: Run the test to verify it fails**

  ```bash
  (cd backend && mvn -q -pl bipros-resource test -Dtest=ProjectLabourDeploymentServiceTest)
  ```
  Expected: compile failure — `ProjectLabourDeploymentService` does not exist yet.

### Task 4.4: `ProjectLabourDeploymentService` — minimal implementation

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/application/service/ProjectLabourDeploymentService.java`

- [ ] **Step 1: Write the file**

  ```java
  package com.bipros.resource.application.service;

  import com.bipros.resource.application.dto.LabourCategorySummary;
  import com.bipros.resource.application.dto.LabourDesignationResponse;
  import com.bipros.resource.application.dto.LabourMasterDashboardSummary;
  import com.bipros.resource.application.dto.ProjectLabourDeploymentRequest;
  import com.bipros.resource.application.dto.ProjectLabourDeploymentResponse;
  import com.bipros.resource.domain.model.LabourCategory;
  import com.bipros.resource.domain.model.LabourDesignation;
  import com.bipros.resource.domain.model.NationalityType;
  import com.bipros.resource.domain.model.ProjectLabourDeployment;
  import com.bipros.resource.domain.repository.LabourDesignationRepository;
  import com.bipros.resource.domain.repository.ProjectLabourDeploymentRepository;
  import jakarta.persistence.EntityNotFoundException;
  import lombok.RequiredArgsConstructor;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;

  import java.math.BigDecimal;
  import java.util.ArrayList;
  import java.util.Comparator;
  import java.util.EnumMap;
  import java.util.List;
  import java.util.Map;
  import java.util.UUID;
  import java.util.stream.Collectors;

  @Service
  @RequiredArgsConstructor
  public class ProjectLabourDeploymentService {

      private final ProjectLabourDeploymentRepository deploymentRepo;
      private final LabourDesignationRepository designationRepo;
      private final LabourDesignationService designationService;

      @Transactional
      public ProjectLabourDeploymentResponse create(UUID projectId, ProjectLabourDeploymentRequest req) {
          LabourDesignation designation = designationRepo.findById(req.designationId())
              .orElseThrow(() -> new EntityNotFoundException(
                  "LabourDesignation not found: " + req.designationId()));
          if (deploymentRepo.existsByProjectIdAndDesignationId(projectId, req.designationId())) {
              throw new IllegalStateException(
                  "Deployment already exists for designation " + designation.getCode());
          }
          ProjectLabourDeployment dep = ProjectLabourDeployment.builder()
              .projectId(projectId)
              .designationId(req.designationId())
              .workerCount(req.workerCount())
              .actualDailyRate(req.actualDailyRate())
              .notes(req.notes())
              .build();
          return toResponse(deploymentRepo.save(dep), designation);
      }

      @Transactional
      public ProjectLabourDeploymentResponse update(UUID projectId, UUID deploymentId,
                                                    ProjectLabourDeploymentRequest req) {
          ProjectLabourDeployment existing = deploymentRepo.findById(deploymentId)
              .orElseThrow(() -> new EntityNotFoundException("Deployment not found: " + deploymentId));
          if (!existing.getProjectId().equals(projectId)) {
              throw new EntityNotFoundException("Deployment not found in project: " + deploymentId);
          }
          existing.setWorkerCount(req.workerCount());
          existing.setActualDailyRate(req.actualDailyRate());
          existing.setNotes(req.notes());
          LabourDesignation d = designationRepo.findById(existing.getDesignationId())
              .orElseThrow(() -> new EntityNotFoundException(
                  "LabourDesignation not found: " + existing.getDesignationId()));
          return toResponse(deploymentRepo.save(existing), d);
      }

      @Transactional
      public void delete(UUID projectId, UUID deploymentId) {
          ProjectLabourDeployment existing = deploymentRepo.findById(deploymentId)
              .orElseThrow(() -> new EntityNotFoundException("Deployment not found: " + deploymentId));
          if (!existing.getProjectId().equals(projectId)) {
              throw new EntityNotFoundException("Deployment not found in project: " + deploymentId);
          }
          deploymentRepo.delete(existing);
      }

      @Transactional(readOnly = true)
      public List<ProjectLabourDeploymentResponse> listForProject(UUID projectId) {
          List<ProjectLabourDeployment> deps = deploymentRepo.findAllByProjectId(projectId);
          Map<UUID, LabourDesignation> byId = loadDesignations(deps);
          return deps.stream()
              .map(dep -> toResponse(dep, byId.get(dep.getDesignationId())))
              .toList();
      }

      @Transactional(readOnly = true)
      public LabourMasterDashboardSummary dashboard(UUID projectId) {
          List<ProjectLabourDeployment> deps = deploymentRepo.findAllByProjectId(projectId);
          Map<UUID, LabourDesignation> byId = loadDesignations(deps);

          int totalWorkforce = 0;
          BigDecimal dailyPayroll = BigDecimal.ZERO;
          int omani = 0, expat = 0, omaniOrExpat = 0;
          Map<LabourCategory, List<DeployRow>> byCat = new EnumMap<>(LabourCategory.class);

          for (ProjectLabourDeployment dep : deps) {
              LabourDesignation d = byId.get(dep.getDesignationId());
              if (d == null) continue;
              BigDecimal rate = effectiveRate(dep, d);
              BigDecimal cost = rate.multiply(BigDecimal.valueOf(dep.getWorkerCount()));
              totalWorkforce += dep.getWorkerCount();
              dailyPayroll = dailyPayroll.add(cost);
              switch (d.getNationality()) {
                  case OMANI -> omani += dep.getWorkerCount();
                  case EXPAT -> expat += dep.getWorkerCount();
                  case OMANI_OR_EXPAT -> omaniOrExpat += dep.getWorkerCount();
              }
              byCat.computeIfAbsent(d.getCategory(), k -> new ArrayList<>())
                   .add(new DeployRow(dep, d, rate, cost));
          }

          List<LabourCategorySummary> summaries = byCat.entrySet().stream()
              .sorted(Comparator.comparing(e -> e.getKey().ordinal()))
              .map(e -> buildCategorySummary(e.getKey(), e.getValue()))
              .toList();

          return new LabourMasterDashboardSummary(
              projectId,
              deps.size(),
              totalWorkforce,
              dailyPayroll,
              "OMR",
              byCat.size(),
              new LabourMasterDashboardSummary.NationalityMix(omani, expat, omaniOrExpat),
              summaries);
      }

      @Transactional(readOnly = true)
      public List<LabourCategorySummary> byCategory(UUID projectId) {
          return dashboard(projectId).byCategory();
      }

      // ── helpers ─────────────────────────────────────────────────

      private record DeployRow(ProjectLabourDeployment dep, LabourDesignation designation,
                                BigDecimal effectiveRate, BigDecimal dailyCost) {}

      private Map<UUID, LabourDesignation> loadDesignations(List<ProjectLabourDeployment> deps) {
          List<UUID> ids = deps.stream().map(ProjectLabourDeployment::getDesignationId).toList();
          return designationRepo.findAllById(ids).stream()
              .collect(Collectors.toMap(LabourDesignation::getId, d -> d));
      }

      private BigDecimal effectiveRate(ProjectLabourDeployment dep, LabourDesignation d) {
          return dep.getActualDailyRate() != null ? dep.getActualDailyRate() : d.getDefaultDailyRate();
      }

      private LabourCategorySummary buildCategorySummary(LabourCategory cat, List<DeployRow> rows) {
          int designationCount = rows.size();
          int workerCount = rows.stream().mapToInt(r -> r.dep.getWorkerCount()).sum();
          BigDecimal dailyCost = rows.stream().map(DeployRow::dailyCost)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
          String gradeRange = rows.stream().map(r -> r.designation.getGrade().name())
              .distinct().sorted().collect(Collectors.joining(", "));
          BigDecimal minRate = rows.stream().map(DeployRow::effectiveRate)
              .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
          BigDecimal maxRate = rows.stream().map(DeployRow::effectiveRate)
              .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
          String rateRange = minRate.toPlainString() + " – " + maxRate.toPlainString();
          List<String> roleNames = rows.stream().map(r -> r.designation.getDesignation()).toList();
          String keyRoles = roleNames.size() <= 3
              ? String.join(", ", roleNames)
              : String.join(", ", roleNames.subList(0, 3)) + " +" + (roleNames.size() - 3) + " more";

          return new LabourCategorySummary(
              cat, cat.getDisplayName(), cat.getCodePrefix(),
              designationCount, workerCount, dailyCost,
              gradeRange, rateRange, keyRoles);
      }

      ProjectLabourDeploymentResponse toResponse(ProjectLabourDeployment dep, LabourDesignation d) {
          BigDecimal rate = effectiveRate(dep, d);
          BigDecimal cost = rate.multiply(BigDecimal.valueOf(dep.getWorkerCount()));
          LabourDesignationResponse designationView = designationService.toResponse(d);
          return new ProjectLabourDeploymentResponse(
              dep.getId(), dep.getProjectId(), dep.getDesignationId(),
              dep.getWorkerCount(), dep.getActualDailyRate(), rate, cost, dep.getNotes(),
              designationView);
      }
  }
  ```

- [ ] **Step 2: Run the test**

  ```bash
  (cd backend && mvn -q -pl bipros-resource test -Dtest=ProjectLabourDeploymentServiceTest)
  ```
  Expected: `BUILD SUCCESS`, all 3 tests green.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/application/service/ProjectLabourDeploymentService.java \
          backend/bipros-resource/src/test/java/com/bipros/resource/application/service/ProjectLabourDeploymentServiceTest.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add ProjectLabourDeploymentService with rate fallback + dashboard

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 5 — Controllers

### Task 5.1: `LabourDesignationController`

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/presentation/controller/LabourDesignationController.java`

- [ ] **Step 1: Write the file**

  ```java
  package com.bipros.resource.presentation.controller;

  import com.bipros.common.dto.ApiResponse;
  import com.bipros.common.dto.PagedResponse;
  import com.bipros.resource.application.dto.LabourCategoryReference;
  import com.bipros.resource.application.dto.LabourDesignationRequest;
  import com.bipros.resource.application.dto.LabourDesignationResponse;
  import com.bipros.resource.application.dto.LabourGradeReference;
  import com.bipros.resource.application.service.LabourDesignationService;
  import com.bipros.resource.domain.model.LabourCategory;
  import com.bipros.resource.domain.model.LabourGrade;
  import jakarta.validation.Valid;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.data.domain.Pageable;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.security.access.prepost.PreAuthorize;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;
  import java.util.UUID;

  @RestController
  @RequestMapping("/v1/labour-designations")
  @PreAuthorize("hasAnyRole('ADMIN')")
  @RequiredArgsConstructor
  @Slf4j
  public class LabourDesignationController {

      private final LabourDesignationService service;

      @GetMapping
      public ResponseEntity<ApiResponse<PagedResponse<LabourDesignationResponse>>> list(
              @RequestParam(required = false) LabourCategory category,
              @RequestParam(required = false) LabourGrade grade,
              @RequestParam(required = false) String status,
              @RequestParam(required = false) String q,
              Pageable pageable) {
          log.info("GET /v1/labour-designations category={} grade={} status={} q={}",
              category, grade, status, q);
          return ResponseEntity.ok(ApiResponse.ok(service.search(category, grade, status, q, pageable)));
      }

      @GetMapping("/{id}")
      public ResponseEntity<ApiResponse<LabourDesignationResponse>> get(@PathVariable UUID id) {
          return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
      }

      @GetMapping("/by-code/{code}")
      public ResponseEntity<ApiResponse<LabourDesignationResponse>> getByCode(@PathVariable String code) {
          return ResponseEntity.ok(ApiResponse.ok(service.getByCode(code)));
      }

      @PostMapping
      public ResponseEntity<ApiResponse<LabourDesignationResponse>> create(
              @Valid @RequestBody LabourDesignationRequest req) {
          log.info("POST /v1/labour-designations code={}", req.code());
          return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req)));
      }

      @PutMapping("/{id}")
      public ResponseEntity<ApiResponse<LabourDesignationResponse>> update(
              @PathVariable UUID id, @Valid @RequestBody LabourDesignationRequest req) {
          return ResponseEntity.ok(ApiResponse.ok(service.update(id, req)));
      }

      @DeleteMapping("/{id}")
      public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
          service.delete(id);
          return ResponseEntity.ok(ApiResponse.ok(null));
      }

      @GetMapping("/categories")
      @PreAuthorize("isAuthenticated()")
      public ResponseEntity<ApiResponse<List<LabourCategoryReference>>> categories() {
          return ResponseEntity.ok(ApiResponse.ok(service.listCategories()));
      }

      @GetMapping("/grades")
      @PreAuthorize("isAuthenticated()")
      public ResponseEntity<ApiResponse<List<LabourGradeReference>>> grades() {
          return ResponseEntity.ok(ApiResponse.ok(service.listGrades()));
      }
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/presentation/controller/LabourDesignationController.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add LabourDesignationController

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 5.2: `ProjectLabourDeploymentController`

**Files:**
- Create: `backend/bipros-resource/src/main/java/com/bipros/resource/presentation/controller/ProjectLabourDeploymentController.java`

- [ ] **Step 1: Write the file**

  ```java
  package com.bipros.resource.presentation.controller;

  import com.bipros.common.dto.ApiResponse;
  import com.bipros.resource.application.dto.LabourCategorySummary;
  import com.bipros.resource.application.dto.LabourMasterDashboardSummary;
  import com.bipros.resource.application.dto.ProjectLabourDeploymentRequest;
  import com.bipros.resource.application.dto.ProjectLabourDeploymentResponse;
  import com.bipros.resource.application.service.ProjectLabourDeploymentService;
  import jakarta.validation.Valid;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.security.access.prepost.PreAuthorize;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;
  import java.util.UUID;

  @RestController
  @RequestMapping("/v1/projects/{projectId}/labour-deployments")
  @PreAuthorize("hasAnyRole('ADMIN')")
  @RequiredArgsConstructor
  @Slf4j
  public class ProjectLabourDeploymentController {

      private final ProjectLabourDeploymentService service;

      @GetMapping
      public ResponseEntity<ApiResponse<List<ProjectLabourDeploymentResponse>>> list(
              @PathVariable UUID projectId) {
          log.info("GET /v1/projects/{}/labour-deployments", projectId);
          return ResponseEntity.ok(ApiResponse.ok(service.listForProject(projectId)));
      }

      @GetMapping("/dashboard")
      public ResponseEntity<ApiResponse<LabourMasterDashboardSummary>> dashboard(
              @PathVariable UUID projectId) {
          return ResponseEntity.ok(ApiResponse.ok(service.dashboard(projectId)));
      }

      @GetMapping("/by-category")
      public ResponseEntity<ApiResponse<List<LabourCategorySummary>>> byCategory(
              @PathVariable UUID projectId) {
          return ResponseEntity.ok(ApiResponse.ok(service.byCategory(projectId)));
      }

      @PostMapping
      public ResponseEntity<ApiResponse<ProjectLabourDeploymentResponse>> create(
              @PathVariable UUID projectId,
              @Valid @RequestBody ProjectLabourDeploymentRequest req) {
          log.info("POST /v1/projects/{}/labour-deployments designation={}", projectId, req.designationId());
          return ResponseEntity.status(HttpStatus.CREATED)
              .body(ApiResponse.ok(service.create(projectId, req)));
      }

      @PutMapping("/{deploymentId}")
      public ResponseEntity<ApiResponse<ProjectLabourDeploymentResponse>> update(
              @PathVariable UUID projectId,
              @PathVariable UUID deploymentId,
              @Valid @RequestBody ProjectLabourDeploymentRequest req) {
          return ResponseEntity.ok(ApiResponse.ok(service.update(projectId, deploymentId, req)));
      }

      @DeleteMapping("/{deploymentId}")
      public ResponseEntity<ApiResponse<Void>> delete(
              @PathVariable UUID projectId,
              @PathVariable UUID deploymentId) {
          service.delete(projectId, deploymentId);
          return ResponseEntity.ok(ApiResponse.ok(null));
      }
  }
  ```

- [ ] **Step 2: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-resource -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-resource/src/main/java/com/bipros/resource/presentation/controller/ProjectLabourDeploymentController.java
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add ProjectLabourDeploymentController

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 5.3: Integration tests for both controllers

**Files:**
- Create: `backend/bipros-resource/src/test/java/com/bipros/resource/presentation/controller/LabourDesignationControllerIT.java`
- Create: `backend/bipros-resource/src/test/java/com/bipros/resource/presentation/controller/ProjectLabourDeploymentControllerIT.java`

- [ ] **Step 1: First, find an existing `*ControllerIT` in this module to mirror**

  ```bash
  find /Volumes/Java/Projects/bipros-eppm/backend/bipros-resource/src/test -name "*ControllerIT.java" | head -3
  ```
  Open the first match and note: how it bootstraps the app context, how it gets an authenticated `TestRestTemplate`, how it picks `RANDOM_PORT`, how it cleans tables. **Use that pattern verbatim** in the two new ITs. (Do not invent a new harness — match the module's convention.)

- [ ] **Step 2: Write `LabourDesignationControllerIT`** following that pattern, with at minimum these test methods:

  - `create_returns201AndPersists` — POST a valid `LabourDesignationRequest`, expect 201 + the body, then GET by code returns the same row.
  - `create_rejectsCodePrefixCategoryMismatch` — POST `{ code: "PO-001", category: SITE_MANAGEMENT, ... }`, expect 400.
  - `delete_softDeletesWhenReferenced` — create designation, create deployment, DELETE designation, GET designation returns row with `status: INACTIVE`.
  - `categoriesEndpoint_returnsFiveEntries` — GET `/v1/labour-designations/categories` returns exactly 5.
  - `gradesEndpoint_returnsFiveEntries` — GET `/v1/labour-designations/grades` returns exactly 5.

- [ ] **Step 3: Write `ProjectLabourDeploymentControllerIT`** with at minimum:

  - `dashboard_aggregatesAcrossDeployments` — seed 2 designations + 2 deployments under one `projectId`, GET `/dashboard`, assert `totalDesignations=2`, `totalWorkforce` matches sum, `dailyPayroll` matches sum-of-products.
  - `update_changesEffectiveRate` — create deployment with no `actualDailyRate`, update it with one, GET the row back and assert `effectiveRate` switched to the new override.
  - `create_rejectsDuplicateDesignation` — POST same `designationId` twice, expect 4xx on the second.

- [ ] **Step 4: Run the integration tests**

  ```bash
  (cd backend && mvn -q -pl bipros-resource test \
      -Dtest='LabourDesignationControllerIT,ProjectLabourDeploymentControllerIT')
  ```
  Expected: `BUILD SUCCESS`. If a test depends on default category/grade ordering, fix the test, not the production code.

- [ ] **Step 5: Commit**

  ```bash
  git add backend/bipros-resource/src/test/java/com/bipros/resource/presentation/controller/LabourDesignationControllerIT.java \
          backend/bipros-resource/src/test/java/com/bipros/resource/presentation/controller/ProjectLabourDeploymentControllerIT.java
  git commit -m "$(cat <<'EOF'
  test(labour-master): integration tests for catalogue + deployment controllers

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 6 — Liquibase changelog (production schema)

### Task 6.1: Add `045-labour-master-tables.yaml`

**Files:**
- Create: `backend/bipros-api/src/main/resources/db/changelog/045-labour-master-tables.yaml`
- Modify: `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: First, sample the existing pattern**

  Read `backend/bipros-api/src/main/resources/db/changelog/008-resource-tables.yaml` (3-minute read). Note: how it declares `schema: resource`, how it names changeset IDs, how it adds indexes/uniques.

- [ ] **Step 2: Write `045-labour-master-tables.yaml`**

  ```yaml
  databaseChangeLog:
    - changeSet:
        id: 045-1-create-labour-designations
        author: bipros
        changes:
          - createTable:
              tableName: labour_designations
              schemaName: resource
              columns:
                - column: { name: id,                  type: UUID,           constraints: { primaryKey: true, nullable: false } }
                - column: { name: created_at,          type: TIMESTAMP,      constraints: { nullable: false } }
                - column: { name: updated_at,          type: TIMESTAMP,      constraints: { nullable: false } }
                - column: { name: created_by,          type: VARCHAR(100) }
                - column: { name: updated_by,          type: VARCHAR(100) }
                - column: { name: version,             type: BIGINT }
                - column: { name: code,                type: VARCHAR(20),    constraints: { nullable: false, unique: true, uniqueConstraintName: uk_labour_designation_code } }
                - column: { name: designation,         type: VARCHAR(100),   constraints: { nullable: false } }
                - column: { name: category,            type: VARCHAR(30),    constraints: { nullable: false } }
                - column: { name: trade,               type: VARCHAR(80),    constraints: { nullable: false } }
                - column: { name: grade,               type: VARCHAR(2),     constraints: { nullable: false } }
                - column: { name: nationality,         type: VARCHAR(20),    constraints: { nullable: false } }
                - column: { name: experience_years_min, type: INTEGER,       constraints: { nullable: false } }
                - column: { name: default_daily_rate,  type: NUMERIC(10,2),  constraints: { nullable: false } }
                - column: { name: currency,            type: VARCHAR(3),     constraints: { nullable: false }, defaultValue: "OMR" }
                - column: { name: skills,              type: JSONB,          constraints: { nullable: false }, defaultValueComputed: "'[]'::jsonb" }
                - column: { name: certifications,      type: JSONB,          constraints: { nullable: false }, defaultValueComputed: "'[]'::jsonb" }
                - column: { name: key_role_summary,    type: VARCHAR(500) }
                - column: { name: status,              type: VARCHAR(20),    constraints: { nullable: false }, defaultValue: "ACTIVE" }
                - column: { name: sort_order,          type: INTEGER,        constraints: { nullable: false }, defaultValueNumeric: 0 }

    - changeSet:
        id: 045-2-create-labour-designations-indexes
        author: bipros
        changes:
          - createIndex: { schemaName: resource, tableName: labour_designations, indexName: idx_labour_designation_category, columns: [{ column: { name: category } }] }
          - createIndex: { schemaName: resource, tableName: labour_designations, indexName: idx_labour_designation_grade,    columns: [{ column: { name: grade } }] }
          - createIndex: { schemaName: resource, tableName: labour_designations, indexName: idx_labour_designation_status,   columns: [{ column: { name: status } }] }

    - changeSet:
        id: 045-3-create-project-labour-deployments
        author: bipros
        changes:
          - createTable:
              tableName: project_labour_deployments
              schemaName: resource
              columns:
                - column: { name: id,                  type: UUID,           constraints: { primaryKey: true, nullable: false } }
                - column: { name: created_at,          type: TIMESTAMP,      constraints: { nullable: false } }
                - column: { name: updated_at,          type: TIMESTAMP,      constraints: { nullable: false } }
                - column: { name: created_by,          type: VARCHAR(100) }
                - column: { name: updated_by,          type: VARCHAR(100) }
                - column: { name: version,             type: BIGINT }
                - column: { name: project_id,          type: UUID,           constraints: { nullable: false } }
                - column: { name: designation_id,      type: UUID,           constraints: { nullable: false } }
                - column: { name: worker_count,        type: INTEGER,        constraints: { nullable: false } }
                - column: { name: actual_daily_rate,   type: NUMERIC(10,2) }
                - column: { name: notes,               type: VARCHAR(500) }
          - addUniqueConstraint:
              schemaName: resource
              tableName: project_labour_deployments
              constraintName: uk_project_labour_deployment_project_designation
              columnNames: project_id, designation_id

    - changeSet:
        id: 045-4-create-project-labour-deployments-indexes
        author: bipros
        changes:
          - createIndex: { schemaName: resource, tableName: project_labour_deployments, indexName: idx_project_labour_deployment_project,     columns: [{ column: { name: project_id } }] }
          - createIndex: { schemaName: resource, tableName: project_labour_deployments, indexName: idx_project_labour_deployment_designation, columns: [{ column: { name: designation_id } }] }
  ```

- [ ] **Step 3: Register in master**

  Open `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml`, find the line including `044-daily-activity-resource-outputs.yaml`, add **immediately after it**:
  ```yaml
    - include: { file: db/changelog/045-labour-master-tables.yaml }
  ```
  Match the indentation of the surrounding `- include` lines exactly.

- [ ] **Step 4: Sanity-check Liquibase parses it**

  ```bash
  (cd backend && mvn -q -pl bipros-api -am liquibase:validate -Dspring-boot.run.profiles=prod \
      -Dspring.datasource.url=$LIQ_VALIDATE_URL 2>/dev/null) || \
  (cd backend && mvn -q -pl bipros-api -am compile)
  ```
  If your local env doesn't have `liquibase:validate` configured, the fallback compile is fine — production validation happens in CI. Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

  ```bash
  git add backend/bipros-api/src/main/resources/db/changelog/045-labour-master-tables.yaml \
          backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml
  git commit -m "$(cat <<'EOF'
  chore(labour-master): liquibase changelog for labour designations + deployments

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 7 — Seed data

### Task 7.1: Add the 44-row Oman dataset as a JSON resource

**Files:**
- Create: `backend/bipros-api/src/main/resources/oman-labour-master.json`

- [ ] **Step 1: Write the file**

  This is a verbatim transcription of the source doc (BIPROS-ORC-LMM-001) into a JSON array. The structure for each row is:
  ```json
  {
    "code": "SM-001",
    "designation": "Project Manager",
    "category": "SITE_MANAGEMENT",
    "trade": "Civil Engineering",
    "grade": "A",
    "nationality": "OMANI_OR_EXPAT",
    "experienceYearsMin": 15,
    "defaultDailyRate": 125.00,
    "workerCount": 1,
    "skills": ["Project Planning", "Contract Management", "FIDIC", "MS Project", "Risk Management"],
    "certifications": ["PMP", "B.Eng Civil"],
    "sortOrder": 1
  }
  ```
  All 44 rows from the doc must be present, in the order they appear (SM-001..SM-009, PO-001..PO-011, SL-001..SL-012, SS-001..SS-007, GL-001..GL-005). Use exact codes, designations, trades, grades, rates, counts, skills, and certifications from the source doc.

  **Nationality mapping rule** (from the doc's "Nationality" field):
  - `"Omani/Expat"` → `OMANI_OR_EXPAT`
  - `"Expat"` → `EXPAT`
  - `"Omani"` → `OMANI`

  **Source-of-truth note:** the spec lives in `docs/superpowers/specs/2026-04-28-labour-master-module-design.md` and the source doc was extracted in the brainstorm transcript. If discrepancies arise, the source doc (`BIPROS-ORC-LMM-001`) wins.

- [ ] **Step 2: Validate the JSON parses**

  ```bash
  python3 -m json.tool backend/bipros-api/src/main/resources/oman-labour-master.json > /dev/null && echo OK
  ```
  Expected: `OK`. Also assert it has exactly 44 rows:
  ```bash
  python3 -c "import json; print(len(json.load(open('backend/bipros-api/src/main/resources/oman-labour-master.json'))))"
  ```
  Expected: `44`.

- [ ] **Step 3: Commit**

  ```bash
  git add backend/bipros-api/src/main/resources/oman-labour-master.json
  git commit -m "$(cat <<'EOF'
  chore(labour-master): add Oman 44-designation seed dataset

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 7.2: `OmanLabourMasterSeeder`

**Files:**
- Create: `backend/bipros-api/src/main/java/com/bipros/api/config/seeder/OmanLabourMasterSeeder.java`

- [ ] **Step 1: First find the Oman demo project's lookup key**

  ```bash
  grep -rln "Oman" backend/bipros-api/src/main/java/com/bipros/api/config/seeder/ | head -5
  ```
  Open one match and note the project `code` or `name` used in the existing Oman demo seeder. Use that exact value below where the placeholder `<<OMAN_PROJECT_CODE>>` appears.

- [ ] **Step 2: Write the file**

  ```java
  package com.bipros.api.config.seeder;

  import com.bipros.project.domain.model.Project;
  import com.bipros.project.domain.repository.ProjectRepository;
  import com.bipros.resource.domain.model.LabourCategory;
  import com.bipros.resource.domain.model.LabourDesignation;
  import com.bipros.resource.domain.model.LabourGrade;
  import com.bipros.resource.domain.model.NationalityType;
  import com.bipros.resource.domain.model.ProjectLabourDeployment;
  import com.bipros.resource.domain.repository.LabourDesignationRepository;
  import com.bipros.resource.domain.repository.ProjectLabourDeploymentRepository;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.boot.CommandLineRunner;
  import org.springframework.context.annotation.Profile;
  import org.springframework.core.annotation.Order;
  import org.springframework.core.io.ClassPathResource;
  import org.springframework.stereotype.Component;
  import org.springframework.transaction.annotation.Transactional;

  import java.io.InputStream;
  import java.math.BigDecimal;
  import java.util.List;
  import java.util.Map;
  import java.util.Optional;

  @Slf4j
  @Component
  @Profile("seed")
  @Order(140)
  @RequiredArgsConstructor
  public class OmanLabourMasterSeeder implements CommandLineRunner {

      private static final String OMAN_PROJECT_CODE = "<<OMAN_PROJECT_CODE>>"; // replace per Step 1

      private final LabourDesignationRepository designationRepo;
      private final ProjectLabourDeploymentRepository deploymentRepo;
      private final ProjectRepository projectRepo;
      private final ObjectMapper objectMapper;

      @Override
      @Transactional
      public void run(String... args) throws Exception {
          if (designationRepo.count() > 0) {
              log.info("Labour designations already present — skipping Oman seed");
              return;
          }
          List<Map<String, Object>> rows = readDataset();
          List<LabourDesignation> designations = rows.stream().map(this::toDesignation).toList();
          designationRepo.saveAll(designations);
          log.info("Seeded {} labour designations", designations.size());

          Optional<Project> omanProject = projectRepo.findByCode(OMAN_PROJECT_CODE);
          if (omanProject.isEmpty()) {
              log.warn("Oman demo project not found by code '{}' — skipping deployment seed",
                  OMAN_PROJECT_CODE);
              return;
          }
          var projectId = omanProject.get().getId();
          for (int i = 0; i < rows.size(); i++) {
              Map<String, Object> r = rows.get(i);
              LabourDesignation d = designations.get(i);
              Integer count = ((Number) r.get("workerCount")).intValue();
              ProjectLabourDeployment dep = ProjectLabourDeployment.builder()
                  .projectId(projectId)
                  .designationId(d.getId())
                  .workerCount(count)
                  .actualDailyRate(null)
                  .build();
              deploymentRepo.save(dep);
          }
          log.info("Seeded {} project labour deployments for Oman project {}",
              rows.size(), projectId);
      }

      private List<Map<String, Object>> readDataset() throws Exception {
          try (InputStream in = new ClassPathResource("oman-labour-master.json").getInputStream()) {
              return objectMapper.readValue(in, new com.fasterxml.jackson.core.type.TypeReference<>() {});
          }
      }

      @SuppressWarnings("unchecked")
      private LabourDesignation toDesignation(Map<String, Object> r) {
          return LabourDesignation.builder()
              .code((String) r.get("code"))
              .designation((String) r.get("designation"))
              .category(LabourCategory.valueOf((String) r.get("category")))
              .trade((String) r.get("trade"))
              .grade(LabourGrade.valueOf((String) r.get("grade")))
              .nationality(NationalityType.valueOf((String) r.get("nationality")))
              .experienceYearsMin(((Number) r.get("experienceYearsMin")).intValue())
              .defaultDailyRate(new BigDecimal(r.get("defaultDailyRate").toString()))
              .currency("OMR")
              .skills((List<String>) r.getOrDefault("skills", List.of()))
              .certifications((List<String>) r.getOrDefault("certifications", List.of()))
              .status("ACTIVE")
              .sortOrder(((Number) r.getOrDefault("sortOrder", 0)).intValue())
              .build();
      }
  }
  ```

  **Note:** if `ProjectRepository#findByCode` does not exist, replace with whatever lookup the existing Oman seeder uses (e.g., `findByName`). Step 1 will have surfaced the right one.

- [ ] **Step 3: Compile**

  ```bash
  (cd backend && mvn -q -pl bipros-api -am compile -DskipTests)
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Boot under the seed profile and verify**

  ```bash
  (cd backend && SPRING_PROFILES_ACTIVE=seed mvn -q -pl bipros-api -am spring-boot:run) &
  BACKEND_PID=$!
  sleep 30
  psql -h localhost -U bipros -d bipros -c "SELECT count(*) FROM resource.labour_designations;"
  psql -h localhost -U bipros -d bipros -c "SELECT count(*) FROM resource.project_labour_deployments;"
  kill $BACKEND_PID
  ```
  Expected: 44 designations; deployments either 44 (if Oman project found) or 0 (if not — check the logs).

- [ ] **Step 5: Commit**

  ```bash
  git add backend/bipros-api/src/main/java/com/bipros/api/config/seeder/OmanLabourMasterSeeder.java
  git commit -m "$(cat <<'EOF'
  chore(labour-master): add OmanLabourMasterSeeder under 'seed' profile

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 7.3: HTTP reset script

**Files:**
- Create: `scripts/seed-oman-labour.sh`

- [ ] **Step 1: Sample existing pattern**

  Read `scripts/seed-icpms-data.sh` to copy the login-and-token preamble (admin/admin123 → JWT).

- [ ] **Step 2: Write `scripts/seed-oman-labour.sh`**

  Mirror the existing script's auth handling. The body should:
  1. Login as admin/admin123, capture the JWT.
  2. `GET /v1/labour-designations?size=500`, `DELETE` each `id`. (Soft-delete is OK — existing deployments will block hard-delete; the script should accept that.)
  3. Read `backend/bipros-api/src/main/resources/oman-labour-master.json`, POST each row to `/v1/labour-designations`.
  4. If `--with-deployments <projectId>` is passed, also POST one deployment per row.

  The script must `set -euo pipefail` and exit non-zero on any 4xx/5xx.

- [ ] **Step 3: Make executable**

  ```bash
  chmod +x scripts/seed-oman-labour.sh
  ```

- [ ] **Step 4: Smoke test (backend running normally)**

  ```bash
  ./scripts/seed-oman-labour.sh
  ```
  Expected: `Seeded 44 labour designations` (or similar). No deployment seeding without `--with-deployments`.

- [ ] **Step 5: Commit**

  ```bash
  git add scripts/seed-oman-labour.sh
  git commit -m "$(cat <<'EOF'
  chore(labour-master): HTTP reset script for Oman labour catalogue

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 8 — Frontend API client

### Task 8.1: `labourMasterApi.ts`

**Files:**
- Create: `frontend/src/lib/api/labourMasterApi.ts`

- [ ] **Step 1: Sample the existing pattern**

  Read `frontend/src/lib/api/labourApi.ts` (already in the repo) to copy the import / wrapper / type style verbatim.

- [ ] **Step 2: Write the file**

  ```ts
  import { apiClient } from "./client";
  import type { ApiResponse, PagedResponse } from "../types";

  export type LabourCategory =
    | "SITE_MANAGEMENT"
    | "PLANT_EQUIPMENT"
    | "SKILLED_LABOUR"
    | "SEMI_SKILLED_LABOUR"
    | "GENERAL_UNSKILLED";

  export type LabourGrade = "A" | "B" | "C" | "D" | "E";
  export type NationalityType = "OMANI" | "EXPAT" | "OMANI_OR_EXPAT";

  export interface LabourDesignationResponse {
    id: string;
    code: string;
    designation: string;
    category: LabourCategory;
    categoryDisplay: string;
    codePrefix: string;
    trade: string;
    grade: LabourGrade;
    nationality: NationalityType;
    experienceYearsMin: number;
    defaultDailyRate: number;
    currency: string;
    skills: string[];
    certifications: string[];
    keyRoleSummary: string | null;
    status: "ACTIVE" | "INACTIVE";
    sortOrder: number;
    deployment?: {
      id: string;
      workerCount: number;
      actualDailyRate: number | null;
      effectiveRate: number;
      dailyCost: number;
      notes: string | null;
    } | null;
  }

  export interface LabourDesignationRequest {
    code: string;
    designation: string;
    category: LabourCategory;
    trade: string;
    grade: LabourGrade;
    nationality: NationalityType;
    experienceYearsMin: number;
    defaultDailyRate: number;
    currency?: string;
    skills?: string[];
    certifications?: string[];
    keyRoleSummary?: string;
    status?: "ACTIVE" | "INACTIVE";
    sortOrder?: number;
  }

  export interface ProjectLabourDeploymentRequest {
    designationId: string;
    workerCount: number;
    actualDailyRate?: number;
    notes?: string;
  }

  export interface ProjectLabourDeploymentResponse {
    id: string;
    projectId: string;
    designationId: string;
    workerCount: number;
    actualDailyRate: number | null;
    effectiveRate: number;
    dailyCost: number;
    notes: string | null;
    designation: LabourDesignationResponse;
  }

  export interface LabourCategorySummary {
    category: LabourCategory;
    categoryDisplay: string;
    codePrefix: string;
    designationCount: number;
    workerCount: number;
    dailyCost: number;
    gradeRange: string;
    dailyRateRange: string;
    keyRolesSummary: string;
  }

  export interface LabourMasterDashboardSummary {
    projectId: string;
    totalDesignations: number;
    totalWorkforce: number;
    dailyPayroll: number;
    currency: string;
    skillCategoryCount: number;
    nationalityMix: { omani: number; expat: number; omaniOrExpat: number };
    byCategory: LabourCategorySummary[];
  }

  export interface LabourGradeReference {
    grade: LabourGrade;
    classification: string;
    dailyRateRange: string;
    description: string;
  }

  export interface LabourCategoryReference {
    category: LabourCategory;
    codePrefix: string;
    displayName: string;
  }

  export const labourMasterApi = {
    designations: {
      list: (
        params?: {
          category?: LabourCategory;
          grade?: LabourGrade;
          status?: string;
          q?: string;
          page?: number;
          size?: number;
        }
      ) =>
        apiClient
          .get<ApiResponse<PagedResponse<LabourDesignationResponse>>>(
            "/v1/labour-designations",
            { params }
          )
          .then((r) => r.data),

      get: (id: string) =>
        apiClient
          .get<ApiResponse<LabourDesignationResponse>>(`/v1/labour-designations/${id}`)
          .then((r) => r.data),

      getByCode: (code: string) =>
        apiClient
          .get<ApiResponse<LabourDesignationResponse>>(`/v1/labour-designations/by-code/${code}`)
          .then((r) => r.data),

      create: (req: LabourDesignationRequest) =>
        apiClient
          .post<ApiResponse<LabourDesignationResponse>>("/v1/labour-designations", req)
          .then((r) => r.data),

      update: (id: string, req: LabourDesignationRequest) =>
        apiClient
          .put<ApiResponse<LabourDesignationResponse>>(`/v1/labour-designations/${id}`, req)
          .then((r) => r.data),

      remove: (id: string) =>
        apiClient
          .delete<ApiResponse<void>>(`/v1/labour-designations/${id}`)
          .then((r) => r.data),

      listCategories: () =>
        apiClient
          .get<ApiResponse<LabourCategoryReference[]>>("/v1/labour-designations/categories")
          .then((r) => r.data),

      listGrades: () =>
        apiClient
          .get<ApiResponse<LabourGradeReference[]>>("/v1/labour-designations/grades")
          .then((r) => r.data),
    },

    deployments: {
      listForProject: (projectId: string) =>
        apiClient
          .get<ApiResponse<ProjectLabourDeploymentResponse[]>>(
            `/v1/projects/${projectId}/labour-deployments`
          )
          .then((r) => r.data),

      getDashboard: (projectId: string) =>
        apiClient
          .get<ApiResponse<LabourMasterDashboardSummary>>(
            `/v1/projects/${projectId}/labour-deployments/dashboard`
          )
          .then((r) => r.data),

      getByCategory: (projectId: string) =>
        apiClient
          .get<ApiResponse<LabourCategorySummary[]>>(
            `/v1/projects/${projectId}/labour-deployments/by-category`
          )
          .then((r) => r.data),

      create: (projectId: string, req: ProjectLabourDeploymentRequest) =>
        apiClient
          .post<ApiResponse<ProjectLabourDeploymentResponse>>(
            `/v1/projects/${projectId}/labour-deployments`,
            req
          )
          .then((r) => r.data),

      update: (
        projectId: string,
        deploymentId: string,
        req: ProjectLabourDeploymentRequest
      ) =>
        apiClient
          .put<ApiResponse<ProjectLabourDeploymentResponse>>(
            `/v1/projects/${projectId}/labour-deployments/${deploymentId}`,
            req
          )
          .then((r) => r.data),

      remove: (projectId: string, deploymentId: string) =>
        apiClient
          .delete<ApiResponse<void>>(
            `/v1/projects/${projectId}/labour-deployments/${deploymentId}`
          )
          .then((r) => r.data),
    },
  };
  ```

- [ ] **Step 3: Type-check**

  ```bash
  (cd frontend && pnpm tsc --noEmit)
  ```
  Expected: no errors.

- [ ] **Step 4: Commit**

  ```bash
  git add frontend/src/lib/api/labourMasterApi.ts
  git commit -m "$(cat <<'EOF'
  feat(labour-master): frontend API client (designations + deployments)

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 9 — Frontend components

> **Before starting Phase 9:** read `frontend/node_modules/next/dist/docs/02-app/01-getting-started/01-installation.mdx` and `frontend/node_modules/next/dist/docs/02-app/01-getting-started/04-layouts-and-pages.mdx` (or the closest equivalents in your installed version) for the exact App Router layout/page conventions in this Next.js 16 install. Do not assume Next 14/15.

### Task 9.1: `labourMasterTokens.ts` — design tokens

**Files:**
- Create: `frontend/src/components/labour-master/labourMasterTokens.ts`

- [ ] **Step 1: Write the file**

  ```ts
  import type { LabourCategory, LabourGrade } from "@/lib/api/labourMasterApi";

  /** Tailwind colour-token mapping for category accents (used on cards + section headers). */
  export const CATEGORY_ACCENT: Record<LabourCategory, { bg: string; ring: string; text: string; chip: string }> = {
    SITE_MANAGEMENT:    { bg: "bg-emerald-50",  ring: "ring-emerald-200",  text: "text-emerald-900",  chip: "bg-emerald-100 text-emerald-800" },
    PLANT_EQUIPMENT:    { bg: "bg-amber-50",    ring: "ring-amber-200",    text: "text-amber-900",    chip: "bg-amber-100 text-amber-800" },
    SKILLED_LABOUR:     { bg: "bg-indigo-50",   ring: "ring-indigo-200",   text: "text-indigo-900",   chip: "bg-indigo-100 text-indigo-800" },
    SEMI_SKILLED_LABOUR:{ bg: "bg-purple-50",   ring: "ring-purple-200",   text: "text-purple-900",   chip: "bg-purple-100 text-purple-800" },
    GENERAL_UNSKILLED:  { bg: "bg-slate-50",    ring: "ring-slate-200",    text: "text-slate-900",    chip: "bg-slate-100 text-slate-800" },
  };

  /** Grade badge style. */
  export const GRADE_BADGE: Record<LabourGrade, string> = {
    A: "bg-rose-100 text-rose-800",
    B: "bg-orange-100 text-orange-800",
    C: "bg-amber-100 text-amber-800",
    D: "bg-sky-100 text-sky-800",
    E: "bg-slate-100 text-slate-800",
  };

  export const formatOMR = (value: number | null | undefined): string =>
    value == null ? "—" : `OMR ${value.toLocaleString("en-OM", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
  ```

- [ ] **Step 2: Type-check**

  ```bash
  (cd frontend && pnpm tsc --noEmit)
  ```
  Expected: no errors.

- [ ] **Step 3: Commit**

  ```bash
  git add frontend/src/components/labour-master/labourMasterTokens.ts
  git commit -m "$(cat <<'EOF'
  feat(labour-master): design tokens for category accents + grade badges

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 9.2: `KpiTiles` + `WorkforceCategoryBarChart`

**Files:**
- Create: `frontend/src/components/labour-master/KpiTiles.tsx`
- Create: `frontend/src/components/labour-master/WorkforceCategoryBarChart.tsx`

- [ ] **Step 1: Write `KpiTiles.tsx`**

  ```tsx
  "use client";

  import type { LabourMasterDashboardSummary } from "@/lib/api/labourMasterApi";
  import { formatOMR } from "./labourMasterTokens";

  type Props = { summary: LabourMasterDashboardSummary };

  export function KpiTiles({ summary }: Props) {
    const tiles: Array<{ label: string; value: string; sub?: string }> = [
      { label: "Total Designations", value: String(summary.totalDesignations) },
      { label: "Total Workforce",     value: String(summary.totalWorkforce) },
      { label: "Daily Payroll",       value: formatOMR(summary.dailyPayroll), sub: summary.currency },
      { label: "Skill Categories",    value: String(summary.skillCategoryCount) },
      { label: "Nationality Mix",     value: `${summary.nationalityMix.omani} / ${summary.nationalityMix.expat} / ${summary.nationalityMix.omaniOrExpat}`, sub: "Omani / Expat / Either" },
    ];
    return (
      <div className="grid gap-4 grid-cols-2 md:grid-cols-3 lg:grid-cols-5">
        {tiles.map((t) => (
          <div key={t.label} className="rounded-lg border bg-white p-4 shadow-sm">
            <div className="text-sm text-muted-foreground">{t.label}</div>
            <div className="mt-1 text-2xl font-semibold">{t.value}</div>
            {t.sub ? <div className="mt-1 text-xs text-muted-foreground">{t.sub}</div> : null}
          </div>
        ))}
      </div>
    );
  }
  ```

- [ ] **Step 2: Write `WorkforceCategoryBarChart.tsx`**

  ```tsx
  "use client";

  import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
  import type { LabourCategorySummary } from "@/lib/api/labourMasterApi";

  type Props = { rows: LabourCategorySummary[] };

  export function WorkforceCategoryBarChart({ rows }: Props) {
    const data = rows.map((r) => ({
      name: r.categoryDisplay,
      workers: r.workerCount,
      cost: r.dailyCost,
    }));
    return (
      <div className="rounded-lg border bg-white p-4 shadow-sm h-80">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout="vertical" margin={{ top: 8, right: 24, left: 32, bottom: 8 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis type="number" />
            <YAxis type="category" dataKey="name" width={180} />
            <Tooltip />
            <Bar dataKey="workers" fill="#6366f1" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    );
  }
  ```

- [ ] **Step 3: Type-check**

  ```bash
  (cd frontend && pnpm tsc --noEmit)
  ```
  Expected: no errors.

- [ ] **Step 4: Commit**

  ```bash
  git add frontend/src/components/labour-master/KpiTiles.tsx \
          frontend/src/components/labour-master/WorkforceCategoryBarChart.tsx
  git commit -m "$(cat <<'EOF'
  feat(labour-master): KPI tiles + workforce-by-category bar chart

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 9.3: `WorkerCard`, `CategoryCardsSection`, `CategoryFilterBar`

**Files:**
- Create: `frontend/src/components/labour-master/WorkerCard.tsx`
- Create: `frontend/src/components/labour-master/CategoryCardsSection.tsx`
- Create: `frontend/src/components/labour-master/CategoryFilterBar.tsx`

- [ ] **Step 1: Write `WorkerCard.tsx`**

  ```tsx
  "use client";

  import type { LabourDesignationResponse } from "@/lib/api/labourMasterApi";
  import { CATEGORY_ACCENT, GRADE_BADGE, formatOMR } from "./labourMasterTokens";

  type Props = {
    designation: LabourDesignationResponse;
    onClick?: (d: LabourDesignationResponse) => void;
  };

  export function WorkerCard({ designation, onClick }: Props) {
    const accent = CATEGORY_ACCENT[designation.category];
    const grade = GRADE_BADGE[designation.grade];
    const workerCount = designation.deployment?.workerCount ?? 0;
    const dailyRate = designation.deployment?.effectiveRate ?? designation.defaultDailyRate;
    return (
      <button
        type="button"
        onClick={onClick ? () => onClick(designation) : undefined}
        className={`text-left rounded-lg border ring-1 ${accent.ring} ${accent.bg} p-4 hover:shadow transition`}
      >
        <div className="flex items-center justify-between">
          <span className={`text-xs font-mono ${accent.text}`}>{designation.code}</span>
          <span className={`text-xs px-2 py-0.5 rounded ${grade}`}>Grade {designation.grade}</span>
        </div>
        <div className="mt-1 font-semibold">{designation.designation}</div>
        <div className="text-xs text-muted-foreground">{designation.trade}</div>
        <div className="mt-2 flex flex-wrap gap-1">
          {designation.skills.slice(0, 5).map((s) => (
            <span key={s} className={`text-[10px] px-1.5 py-0.5 rounded ${accent.chip}`}>{s}</span>
          ))}
        </div>
        <div className="mt-3 flex items-center justify-between text-sm">
          <span>{workerCount} worker{workerCount === 1 ? "" : "s"}</span>
          <span className="font-medium">{formatOMR(dailyRate)}</span>
        </div>
        <div className="mt-1 text-xs text-muted-foreground">
          {designation.nationality.replace("_", " / ")} · {designation.experienceYearsMin}+ yrs
        </div>
      </button>
    );
  }
  ```

- [ ] **Step 2: Write `CategoryCardsSection.tsx`**

  ```tsx
  "use client";

  import type { LabourDesignationResponse, LabourCategorySummary } from "@/lib/api/labourMasterApi";
  import { WorkerCard } from "./WorkerCard";
  import { CATEGORY_ACCENT, formatOMR } from "./labourMasterTokens";

  type Props = {
    summary: LabourCategorySummary;
    designations: LabourDesignationResponse[];
    onCardClick?: (d: LabourDesignationResponse) => void;
  };

  export function CategoryCardsSection({ summary, designations, onCardClick }: Props) {
    const accent = CATEGORY_ACCENT[summary.category];
    return (
      <section className="space-y-3">
        <header className={`rounded-md ${accent.bg} ring-1 ${accent.ring} px-4 py-3`}>
          <h2 className={`font-semibold ${accent.text}`}>{summary.categoryDisplay}</h2>
          <p className="text-xs text-muted-foreground">
            {summary.designationCount} designations · {summary.workerCount} workers · Daily cost {formatOMR(summary.dailyCost)}
          </p>
        </header>
        <div className="grid gap-3 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {designations.map((d) => (
            <WorkerCard key={d.id} designation={d} onClick={onCardClick} />
          ))}
        </div>
      </section>
    );
  }
  ```

- [ ] **Step 3: Write `CategoryFilterBar.tsx`**

  ```tsx
  "use client";

  import type { LabourCategory, LabourCategoryReference, LabourGrade } from "@/lib/api/labourMasterApi";

  type Props = {
    categories: LabourCategoryReference[];
    selectedCategory: LabourCategory | null;
    onCategoryChange: (c: LabourCategory | null) => void;
    selectedGrade: LabourGrade | null;
    onGradeChange: (g: LabourGrade | null) => void;
    query: string;
    onQueryChange: (q: string) => void;
  };

  const GRADES: LabourGrade[] = ["A", "B", "C", "D", "E"];

  export function CategoryFilterBar({
    categories, selectedCategory, onCategoryChange,
    selectedGrade, onGradeChange, query, onQueryChange,
  }: Props) {
    return (
      <div className="flex flex-wrap items-center gap-2 p-3 rounded-lg bg-white border">
        <button
          type="button"
          onClick={() => onCategoryChange(null)}
          className={`px-3 py-1.5 text-sm rounded ${selectedCategory == null ? "bg-slate-900 text-white" : "bg-slate-100"}`}
        >All</button>
        {categories.map((c) => (
          <button key={c.category} type="button"
            onClick={() => onCategoryChange(c.category)}
            className={`px-3 py-1.5 text-sm rounded ${selectedCategory === c.category ? "bg-slate-900 text-white" : "bg-slate-100"}`}>
            {c.codePrefix} · {c.displayName}
          </button>
        ))}
        <div className="mx-2 h-6 border-l" />
        <span className="text-xs text-muted-foreground">Grade:</span>
        <button type="button" onClick={() => onGradeChange(null)}
          className={`px-2 py-1 text-xs rounded ${selectedGrade == null ? "bg-slate-900 text-white" : "bg-slate-100"}`}>Any</button>
        {GRADES.map((g) => (
          <button key={g} type="button" onClick={() => onGradeChange(g)}
            className={`px-2 py-1 text-xs rounded ${selectedGrade === g ? "bg-slate-900 text-white" : "bg-slate-100"}`}>
            {g}
          </button>
        ))}
        <div className="ml-auto">
          <input
            type="search"
            placeholder="Search code, designation, trade…"
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
            className="px-3 py-1.5 text-sm rounded border min-w-[240px]"
          />
        </div>
      </div>
    );
  }
  ```

- [ ] **Step 4: Type-check & commit**

  ```bash
  (cd frontend && pnpm tsc --noEmit)
  git add frontend/src/components/labour-master/WorkerCard.tsx \
          frontend/src/components/labour-master/CategoryCardsSection.tsx \
          frontend/src/components/labour-master/CategoryFilterBar.tsx
  git commit -m "$(cat <<'EOF'
  feat(labour-master): worker cards, category sections, and filter bar

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 9.4: `WorkerTable`, `WorkerDetailModal`, `WorkforceSummaryTable`, `GradeReferenceTable`

**Files:**
- Create: `frontend/src/components/labour-master/WorkerTable.tsx`
- Create: `frontend/src/components/labour-master/WorkerDetailModal.tsx`
- Create: `frontend/src/components/labour-master/WorkforceSummaryTable.tsx`
- Create: `frontend/src/components/labour-master/GradeReferenceTable.tsx`

- [ ] **Step 1: Write `WorkerTable.tsx`**

  ```tsx
  "use client";

  import type { LabourDesignationResponse } from "@/lib/api/labourMasterApi";
  import { GRADE_BADGE, formatOMR } from "./labourMasterTokens";

  type Props = {
    rows: LabourDesignationResponse[];
    onRowClick?: (d: LabourDesignationResponse) => void;
  };

  export function WorkerTable({ rows, onRowClick }: Props) {
    return (
      <div className="overflow-auto rounded-lg border bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50">
            <tr className="text-left">
              {["Code","Designation","Category","Trade","Grade","Count","Experience","Daily Rate (OMR)","Nationality","Status"].map((h) => (
                <th key={h} className="px-3 py-2 font-semibold text-slate-700">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((d) => (
              <tr
                key={d.id}
                onClick={onRowClick ? () => onRowClick(d) : undefined}
                className="border-t hover:bg-slate-50 cursor-pointer"
              >
                <td className="px-3 py-2 font-mono">{d.code}</td>
                <td className="px-3 py-2">{d.designation}</td>
                <td className="px-3 py-2">{d.categoryDisplay}</td>
                <td className="px-3 py-2">{d.trade}</td>
                <td className="px-3 py-2">
                  <span className={`px-2 py-0.5 rounded text-xs ${GRADE_BADGE[d.grade]}`}>{d.grade}</span>
                </td>
                <td className="px-3 py-2">{d.deployment?.workerCount ?? 0}</td>
                <td className="px-3 py-2">{d.experienceYearsMin}+ yrs</td>
                <td className="px-3 py-2">{formatOMR(d.deployment?.effectiveRate ?? d.defaultDailyRate)}</td>
                <td className="px-3 py-2">{d.nationality.replace("_", " / ")}</td>
                <td className="px-3 py-2">{d.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }
  ```

- [ ] **Step 2: Write `WorkerDetailModal.tsx`**

  ```tsx
  "use client";

  import type { LabourDesignationResponse } from "@/lib/api/labourMasterApi";
  import { CATEGORY_ACCENT, GRADE_BADGE, formatOMR } from "./labourMasterTokens";

  type Props = {
    designation: LabourDesignationResponse | null;
    onClose: () => void;
  };

  export function WorkerDetailModal({ designation, onClose }: Props) {
    if (!designation) return null;
    const accent = CATEGORY_ACCENT[designation.category];
    const workerCount = designation.deployment?.workerCount ?? 0;
    const dailyRate = designation.deployment?.effectiveRate ?? designation.defaultDailyRate;
    const totalDailyCost = (designation.deployment?.dailyCost ?? workerCount * dailyRate);
    return (
      <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4" role="dialog" aria-modal="true">
        <div className="bg-white rounded-xl max-w-2xl w-full p-6 shadow-xl">
          <header className="flex items-start justify-between">
            <div>
              <div className="text-xs font-mono text-muted-foreground">{designation.code}</div>
              <h3 className="text-xl font-semibold">{designation.designation}</h3>
              <p className="text-sm text-muted-foreground">
                {designation.categoryDisplay} · {designation.trade}
              </p>
            </div>
            <button onClick={onClose} aria-label="Close" className="text-slate-500 hover:text-slate-900">✕</button>
          </header>
          <div className={`mt-4 grid grid-cols-2 gap-3 rounded ${accent.bg} ring-1 ${accent.ring} p-4`}>
            <Field label="Grade">
              <span className={`px-2 py-0.5 rounded text-xs ${GRADE_BADGE[designation.grade]}`}>Grade {designation.grade}</span>
            </Field>
            <Field label="Nationality">{designation.nationality.replace("_", " / ")}</Field>
            <Field label="Experience">{designation.experienceYearsMin}+ yrs</Field>
            <Field label="Worker Count">{workerCount}</Field>
            <Field label="Daily Rate">{formatOMR(dailyRate)}</Field>
            <Field label="Total Daily Cost">{formatOMR(totalDailyCost)}</Field>
          </div>
          <Section title="Skills">
            <div className="flex flex-wrap gap-1">
              {designation.skills.map((s) => (
                <span key={s} className={`text-xs px-2 py-0.5 rounded ${accent.chip}`}>{s}</span>
              ))}
            </div>
          </Section>
          <Section title="Required Certifications">
            <ul className="list-disc pl-5 text-sm">
              {designation.certifications.map((c) => <li key={c}>{c}</li>)}
            </ul>
          </Section>
        </div>
      </div>
    );
  }

  function Field({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <div>
        <div className="text-xs text-muted-foreground">{label}</div>
        <div className="text-sm font-medium">{children}</div>
      </div>
    );
  }

  function Section({ title, children }: { title: string; children: React.ReactNode }) {
    return (
      <div className="mt-5">
        <h4 className="text-sm font-semibold mb-2">{title}</h4>
        {children}
      </div>
    );
  }
  ```

- [ ] **Step 3: Write `WorkforceSummaryTable.tsx`**

  ```tsx
  "use client";

  import type { LabourCategorySummary } from "@/lib/api/labourMasterApi";
  import { formatOMR } from "./labourMasterTokens";

  type Props = { rows: LabourCategorySummary[] };

  export function WorkforceSummaryTable({ rows }: Props) {
    const totalDesigs = rows.reduce((a, r) => a + r.designationCount, 0);
    const totalWorkers = rows.reduce((a, r) => a + r.workerCount, 0);
    const totalCost = rows.reduce((a, r) => a + r.dailyCost, 0);
    return (
      <div className="overflow-auto rounded-lg border bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50">
            <tr className="text-left">
              {["Category","Total Designations","Total Workers","Grade Range","Daily Rate Range (OMR)","Daily Cost (OMR)","Key Roles"].map((h) =>
                <th key={h} className="px-3 py-2 font-semibold">{h}</th>
              )}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.category} className="border-t">
                <td className="px-3 py-2 font-medium">{r.categoryDisplay}</td>
                <td className="px-3 py-2">{r.designationCount}</td>
                <td className="px-3 py-2">{r.workerCount}</td>
                <td className="px-3 py-2">{r.gradeRange}</td>
                <td className="px-3 py-2">{r.dailyRateRange}</td>
                <td className="px-3 py-2">{formatOMR(r.dailyCost)}</td>
                <td className="px-3 py-2 text-xs">{r.keyRolesSummary}</td>
              </tr>
            ))}
            <tr className="border-t bg-slate-50 font-semibold">
              <td className="px-3 py-2">TOTAL</td>
              <td className="px-3 py-2">{totalDesigs}</td>
              <td className="px-3 py-2">{totalWorkers}</td>
              <td className="px-3 py-2">A – E</td>
              <td className="px-3 py-2">—</td>
              <td className="px-3 py-2">{formatOMR(totalCost)}</td>
              <td className="px-3 py-2 text-xs">{rows.length} categories</td>
            </tr>
          </tbody>
        </table>
      </div>
    );
  }
  ```

- [ ] **Step 4: Write `GradeReferenceTable.tsx`**

  ```tsx
  "use client";

  import type { LabourGradeReference } from "@/lib/api/labourMasterApi";

  type Props = { rows: LabourGradeReference[]; regulatoryNotes: string[] };

  export function GradeReferenceTable({ rows, regulatoryNotes }: Props) {
    return (
      <div className="space-y-6">
        <div className="overflow-auto rounded-lg border bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50">
              <tr className="text-left">
                {["Grade","Classification","Daily Rate","Description"].map((h) => (
                  <th key={h} className="px-3 py-2 font-semibold">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((g) => (
                <tr key={g.grade} className="border-t align-top">
                  <td className="px-3 py-2 font-medium">{g.grade}</td>
                  <td className="px-3 py-2">{g.classification}</td>
                  <td className="px-3 py-2">{g.dailyRateRange}</td>
                  <td className="px-3 py-2">{g.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <section>
          <h3 className="font-semibold mb-2">Regulatory & Compliance Notes — Sultanate of Oman</h3>
          <ul className="list-disc pl-5 text-sm space-y-1">
            {regulatoryNotes.map((n, i) => <li key={i}>{n}</li>)}
          </ul>
        </section>
      </div>
    );
  }
  ```

- [ ] **Step 5: Type-check & commit**

  ```bash
  (cd frontend && pnpm tsc --noEmit)
  git add frontend/src/components/labour-master/WorkerTable.tsx \
          frontend/src/components/labour-master/WorkerDetailModal.tsx \
          frontend/src/components/labour-master/WorkforceSummaryTable.tsx \
          frontend/src/components/labour-master/GradeReferenceTable.tsx
  git commit -m "$(cat <<'EOF'
  feat(labour-master): table view, detail modal, workforce summary, grade reference

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 9.5: `AddDesignationForm`

**Files:**
- Create: `frontend/src/components/labour-master/AddDesignationForm.tsx`

- [ ] **Step 1: Write the file**

  ```tsx
  "use client";

  import { useState } from "react";
  import { useRouter } from "next/navigation";
  import { useMutation, useQueryClient } from "@tanstack/react-query";
  import {
    labourMasterApi, type LabourCategory, type LabourGrade, type NationalityType,
  } from "@/lib/api/labourMasterApi";

  const CATEGORIES: { value: LabourCategory; prefix: string; label: string }[] = [
    { value: "SITE_MANAGEMENT",     prefix: "SM", label: "Site Management" },
    { value: "PLANT_EQUIPMENT",     prefix: "PO", label: "Plant & Equipment Operators" },
    { value: "SKILLED_LABOUR",      prefix: "SL", label: "Skilled Labour" },
    { value: "SEMI_SKILLED_LABOUR", prefix: "SS", label: "Semi-Skilled Labour" },
    { value: "GENERAL_UNSKILLED",   prefix: "GL", label: "General / Unskilled Labour" },
  ];
  const GRADES: LabourGrade[] = ["A","B","C","D","E"];
  const NATIONALITIES: NationalityType[] = ["OMANI", "EXPAT", "OMANI_OR_EXPAT"];

  export function AddDesignationForm() {
    const router = useRouter();
    const qc = useQueryClient();

    const [category, setCategory] = useState<LabourCategory>("SITE_MANAGEMENT");
    const [code, setCode] = useState("SM-001");
    const [designation, setDesignation] = useState("");
    const [trade, setTrade] = useState("");
    const [grade, setGrade] = useState<LabourGrade>("C");
    const [nationality, setNationality] = useState<NationalityType>("EXPAT");
    const [experienceYearsMin, setExperience] = useState(1);
    const [defaultDailyRate, setRate] = useState(0);
    const [skillsCsv, setSkillsCsv] = useState("");
    const [certsCsv, setCertsCsv] = useState("");
    const [error, setError] = useState<string | null>(null);

    const onCategoryChange = (c: LabourCategory) => {
      setCategory(c);
      const prefix = CATEGORIES.find((x) => x.value === c)!.prefix;
      setCode(`${prefix}-${(code.split("-")[1] ?? "001").padStart(3, "0")}`);
    };

    const create = useMutation({
      mutationFn: () =>
        labourMasterApi.designations.create({
          code, designation, category, trade, grade, nationality,
          experienceYearsMin,
          defaultDailyRate,
          skills: skillsCsv.split(",").map((s) => s.trim()).filter(Boolean),
          certifications: certsCsv.split(",").map((s) => s.trim()).filter(Boolean),
        }),
      onSuccess: () => {
        qc.invalidateQueries({ queryKey: ["labour-designations"] });
        router.push("/labour-master/cards");
      },
      onError: (e: Error) => setError(e.message),
    });

    return (
      <form
        onSubmit={(e) => { e.preventDefault(); setError(null); create.mutate(); }}
        className="grid gap-4 grid-cols-1 md:grid-cols-2 max-w-3xl"
      >
        <Field label="Worker Code">
          <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} pattern="^(SM|PO|SL|SS|GL)-\d{3}$" required className="input" />
        </Field>
        <Field label="Designation">
          <input value={designation} onChange={(e) => setDesignation(e.target.value)} required className="input" />
        </Field>
        <Field label="Category">
          <select value={category} onChange={(e) => onCategoryChange(e.target.value as LabourCategory)} className="input">
            {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
          </select>
        </Field>
        <Field label="Trade">
          <input value={trade} onChange={(e) => setTrade(e.target.value)} required className="input" />
        </Field>
        <Field label="Grade">
          <select value={grade} onChange={(e) => setGrade(e.target.value as LabourGrade)} className="input">
            {GRADES.map((g) => <option key={g} value={g}>{g}</option>)}
          </select>
        </Field>
        <Field label="Nationality">
          <select value={nationality} onChange={(e) => setNationality(e.target.value as NationalityType)} className="input">
            {NATIONALITIES.map((n) => <option key={n} value={n}>{n.replace("_", " / ")}</option>)}
          </select>
        </Field>
        <Field label="Experience (years)">
          <input type="number" min={0} value={experienceYearsMin} onChange={(e) => setExperience(Number(e.target.value))} className="input" />
        </Field>
        <Field label="Daily Rate (OMR)">
          <input type="number" min={0} step="0.01" value={defaultDailyRate} onChange={(e) => setRate(Number(e.target.value))} className="input" />
        </Field>
        <Field label="Skills (comma-separated)">
          <textarea rows={2} value={skillsCsv} onChange={(e) => setSkillsCsv(e.target.value)} className="input" />
        </Field>
        <Field label="Certifications (comma-separated)">
          <textarea rows={2} value={certsCsv} onChange={(e) => setCertsCsv(e.target.value)} className="input" />
        </Field>
        {error && <div className="md:col-span-2 text-sm text-red-700">{error}</div>}
        <div className="md:col-span-2 flex gap-2">
          <button type="submit" disabled={create.isPending} className="px-4 py-2 rounded bg-slate-900 text-white">Save</button>
          <button type="button" onClick={() => router.back()} className="px-4 py-2 rounded border">Cancel</button>
        </div>

        <style jsx>{`
          .input { padding: .5rem .75rem; border: 1px solid hsl(var(--border)); border-radius: .375rem; width: 100%; background: white; }
        `}</style>
      </form>
    );
  }

  function Field({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <label className="space-y-1">
        <span className="text-sm font-medium">{label}</span>
        <div>{children}</div>
      </label>
    );
  }
  ```

- [ ] **Step 2: Type-check & commit**

  ```bash
  (cd frontend && pnpm tsc --noEmit)
  git add frontend/src/components/labour-master/AddDesignationForm.tsx
  git commit -m "$(cat <<'EOF'
  feat(labour-master): add-designation form

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 10 — Frontend routes

> **Project context:** the active project ID comes from the existing project-selector store. Before writing route files, run:
> ```bash
> grep -rln "useActiveProject\|useProjectContext\|currentProject" frontend/src/app/\(app\)/permits | head -5
> ```
> Open one match and copy the import + hook usage line **verbatim**. The placeholder `<<USE_ACTIVE_PROJECT>>` below stands for that hook.

### Task 10.1: Module layout + dashboard

**Files:**
- Create: `frontend/src/app/(app)/labour-master/layout.tsx`
- Create: `frontend/src/app/(app)/labour-master/page.tsx`

- [ ] **Step 1: Write `layout.tsx`**

  ```tsx
  import Link from "next/link";

  export default function LabourMasterLayout({ children }: { children: React.ReactNode }) {
    return (
      <div className="space-y-4">
        <header className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Labour Master</h1>
          <nav className="flex gap-2 text-sm">
            <Link className="px-3 py-1.5 rounded bg-slate-100" href="/labour-master">Dashboard</Link>
            <Link className="px-3 py-1.5 rounded bg-slate-100" href="/labour-master/cards">Cards</Link>
            <Link className="px-3 py-1.5 rounded bg-slate-100" href="/labour-master/table">Table</Link>
            <Link className="px-3 py-1.5 rounded bg-slate-100" href="/labour-master/reference">Reference</Link>
            <Link className="px-3 py-1.5 rounded bg-slate-900 text-white" href="/labour-master/new">+ Add</Link>
          </nav>
        </header>
        {children}
      </div>
    );
  }
  ```

- [ ] **Step 2: Write `page.tsx` (dashboard)**

  ```tsx
  "use client";

  import { useQuery } from "@tanstack/react-query";
  import { labourMasterApi } from "@/lib/api/labourMasterApi";
  import { KpiTiles } from "@/components/labour-master/KpiTiles";
  import { WorkforceCategoryBarChart } from "@/components/labour-master/WorkforceCategoryBarChart";
  import { WorkforceSummaryTable } from "@/components/labour-master/WorkforceSummaryTable";
  // <<USE_ACTIVE_PROJECT>>: replace with the import line found via the grep above.
  import { useActiveProject } from "@/lib/hooks/useActiveProject";

  export default function LabourMasterDashboardPage() {
    const projectId = useActiveProject()?.id;
    const dashboardQuery = useQuery({
      queryKey: ["labour-deployments-dashboard", projectId],
      queryFn: () => labourMasterApi.deployments.getDashboard(projectId!),
      enabled: !!projectId,
    });

    if (!projectId) return <p className="text-sm text-muted-foreground">Select a project to view the labour dashboard.</p>;
    if (dashboardQuery.isLoading) return <p>Loading…</p>;
    if (dashboardQuery.isError) return <p className="text-red-700">Failed to load dashboard.</p>;
    const summary = dashboardQuery.data!.data!;
    return (
      <div className="space-y-6">
        <KpiTiles summary={summary} />
        <WorkforceCategoryBarChart rows={summary.byCategory} />
        <WorkforceSummaryTable rows={summary.byCategory} />
      </div>
    );
  }
  ```

  > If `useActiveProject` does not exist in this codebase, replace with the actual hook surfaced by Step 0 — do not invent one.

- [ ] **Step 3: Type-check & commit**

  ```bash
  (cd frontend && pnpm tsc --noEmit)
  git add frontend/src/app/\(app\)/labour-master/layout.tsx \
          frontend/src/app/\(app\)/labour-master/page.tsx
  git commit -m "$(cat <<'EOF'
  feat(labour-master): module shell + dashboard route

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

### Task 10.2: Cards, Table, Detail, New, Reference routes

**Files:**
- Create: `frontend/src/app/(app)/labour-master/cards/page.tsx`
- Create: `frontend/src/app/(app)/labour-master/table/page.tsx`
- Create: `frontend/src/app/(app)/labour-master/[code]/page.tsx`
- Create: `frontend/src/app/(app)/labour-master/new/page.tsx`
- Create: `frontend/src/app/(app)/labour-master/reference/page.tsx`

- [ ] **Step 1: Write `cards/page.tsx`**

  ```tsx
  "use client";

  import { useMemo, useState } from "react";
  import { useQuery } from "@tanstack/react-query";
  import { labourMasterApi, type LabourCategory, type LabourDesignationResponse, type LabourGrade } from "@/lib/api/labourMasterApi";
  import { CategoryFilterBar } from "@/components/labour-master/CategoryFilterBar";
  import { CategoryCardsSection } from "@/components/labour-master/CategoryCardsSection";
  import { WorkerDetailModal } from "@/components/labour-master/WorkerDetailModal";
  import { useActiveProject } from "@/lib/hooks/useActiveProject";

  export default function CardsPage() {
    const projectId = useActiveProject()?.id;
    const [selectedCategory, setCategory] = useState<LabourCategory | null>(null);
    const [selectedGrade, setGrade] = useState<LabourGrade | null>(null);
    const [query, setQuery] = useState("");
    const [open, setOpen] = useState<LabourDesignationResponse | null>(null);

    const cats = useQuery({ queryKey: ["labour-categories"], queryFn: () => labourMasterApi.designations.listCategories() });
    const dashboard = useQuery({
      queryKey: ["labour-deployments-dashboard", projectId],
      queryFn: () => labourMasterApi.deployments.getDashboard(projectId!),
      enabled: !!projectId,
    });
    const deployments = useQuery({
      queryKey: ["labour-deployments", projectId],
      queryFn: () => labourMasterApi.deployments.listForProject(projectId!),
      enabled: !!projectId,
    });

    const designations: LabourDesignationResponse[] = useMemo(() => {
      const rows = (deployments.data?.data ?? []).map((d) => ({ ...d.designation, deployment: { id: d.id, workerCount: d.workerCount, actualDailyRate: d.actualDailyRate, effectiveRate: d.effectiveRate, dailyCost: d.dailyCost, notes: d.notes } }));
      return rows
        .filter((r) => (selectedCategory ? r.category === selectedCategory : true))
        .filter((r) => (selectedGrade    ? r.grade    === selectedGrade    : true))
        .filter((r) => {
          if (!query) return true;
          const q = query.toLowerCase();
          return r.code.toLowerCase().includes(q) || r.designation.toLowerCase().includes(q) || r.trade.toLowerCase().includes(q);
        });
    }, [deployments.data, selectedCategory, selectedGrade, query]);

    if (!projectId) return <p className="text-sm text-muted-foreground">Select a project.</p>;

    return (
      <div className="space-y-4">
        <CategoryFilterBar
          categories={cats.data?.data ?? []}
          selectedCategory={selectedCategory} onCategoryChange={setCategory}
          selectedGrade={selectedGrade} onGradeChange={setGrade}
          query={query} onQueryChange={setQuery}
        />
        {(dashboard.data?.data?.byCategory ?? []).map((sum) => {
          const rows = designations.filter((d) => d.category === sum.category);
          if (rows.length === 0) return null;
          return <CategoryCardsSection key={sum.category} summary={sum} designations={rows} onCardClick={setOpen} />;
        })}
        <WorkerDetailModal designation={open} onClose={() => setOpen(null)} />
      </div>
    );
  }
  ```

- [ ] **Step 2: Write `table/page.tsx`**

  ```tsx
  "use client";

  import { useMemo, useState } from "react";
  import { useQuery } from "@tanstack/react-query";
  import { labourMasterApi, type LabourDesignationResponse } from "@/lib/api/labourMasterApi";
  import { WorkerTable } from "@/components/labour-master/WorkerTable";
  import { WorkerDetailModal } from "@/components/labour-master/WorkerDetailModal";
  import { useActiveProject } from "@/lib/hooks/useActiveProject";

  export default function TablePage() {
    const projectId = useActiveProject()?.id;
    const [open, setOpen] = useState<LabourDesignationResponse | null>(null);

    const deployments = useQuery({
      queryKey: ["labour-deployments", projectId],
      queryFn: () => labourMasterApi.deployments.listForProject(projectId!),
      enabled: !!projectId,
    });

    const rows: LabourDesignationResponse[] = useMemo(() =>
      (deployments.data?.data ?? []).map((d) => ({
        ...d.designation,
        deployment: { id: d.id, workerCount: d.workerCount, actualDailyRate: d.actualDailyRate, effectiveRate: d.effectiveRate, dailyCost: d.dailyCost, notes: d.notes },
      })), [deployments.data]);

    if (!projectId) return <p className="text-sm text-muted-foreground">Select a project.</p>;
    return (
      <>
        <WorkerTable rows={rows} onRowClick={setOpen} />
        <WorkerDetailModal designation={open} onClose={() => setOpen(null)} />
      </>
    );
  }
  ```

- [ ] **Step 3: Write `[code]/page.tsx`**

  ```tsx
  "use client";

  import { useQuery } from "@tanstack/react-query";
  import { useParams } from "next/navigation";
  import { labourMasterApi } from "@/lib/api/labourMasterApi";
  import { WorkerDetailModal } from "@/components/labour-master/WorkerDetailModal";
  import { useRouter } from "next/navigation";

  export default function DesignationDetailPage() {
    const { code } = useParams<{ code: string }>();
    const router = useRouter();
    const q = useQuery({
      queryKey: ["labour-designation", code],
      queryFn: () => labourMasterApi.designations.getByCode(code),
      enabled: !!code,
    });
    if (q.isLoading) return <p>Loading…</p>;
    if (q.isError || !q.data?.data) return <p className="text-red-700">Not found.</p>;
    return <WorkerDetailModal designation={q.data.data} onClose={() => router.push("/labour-master/cards")} />;
  }
  ```

- [ ] **Step 4: Write `new/page.tsx`**

  ```tsx
  import { AddDesignationForm } from "@/components/labour-master/AddDesignationForm";

  export default function NewLabourDesignationPage() {
    return (
      <div className="space-y-4">
        <h2 className="text-xl font-semibold">Add Worker Designation</h2>
        <AddDesignationForm />
      </div>
    );
  }
  ```

- [ ] **Step 5: Write `reference/page.tsx`**

  ```tsx
  "use client";

  import { useQuery } from "@tanstack/react-query";
  import { labourMasterApi } from "@/lib/api/labourMasterApi";
  import { GradeReferenceTable } from "@/components/labour-master/GradeReferenceTable";

  const REGULATORY_NOTES = [
    "All expatriate workers must hold a valid Oman Residence Card (ROP) and work permit issued under the sponsoring contractor.",
    "Heavy equipment operators require a valid Oman driving licence or an internationally recognised equivalent endorsed by the Road Transport Authority (RTA).",
    "All site workers must complete the mandatory site induction and obtain a Site Safety Card before commencing work.",
    "HSE Officers must hold NEBOSH IGC or equivalent; IOSH Managing Safely is the minimum acceptable for supervisory roles.",
    "Crane operators require a valid third-party inspection certificate for the crane and must carry an OPITO or equivalent rigging card.",
    "All workers involved in bitumen/asphalt operations must have completed chemical handling training and carry the relevant certification.",
    "Traffic Management Officers must produce a current Traffic Management Certificate aligned with PDO/Ministry of Transport requirements.",
    "Daily rates are inclusive of basic salary, accommodation, transport, and food allowance as per standard GCC construction norms.",
    "Omanisation targets must be met per MOM guidelines; Security and Environmental Officer roles have preference for Omani nationals.",
    "All certifications must be verified and copies retained in the HR dossier prior to site mobilisation.",
  ];

  export default function ReferencePage() {
    const grades = useQuery({ queryKey: ["labour-grades"], queryFn: () => labourMasterApi.designations.listGrades() });
    if (grades.isLoading) return <p>Loading…</p>;
    return <GradeReferenceTable rows={grades.data?.data ?? []} regulatoryNotes={REGULATORY_NOTES} />;
  }
  ```

- [ ] **Step 6: Type-check, run dev, smoke**

  ```bash
  (cd frontend && pnpm tsc --noEmit)
  ```
  Expected: no errors.

  Then start the backend (already running with `seed` profile from Phase 7) and the frontend:
  ```bash
  (cd frontend && pnpm dev) &
  FRONT_PID=$!
  sleep 15
  ```
  Open `http://localhost:3000/labour-master` in a browser. Verify each tab loads without console errors. Then `kill $FRONT_PID`.

- [ ] **Step 7: Commit**

  ```bash
  git add frontend/src/app/\(app\)/labour-master/cards/page.tsx \
          frontend/src/app/\(app\)/labour-master/table/page.tsx \
          frontend/src/app/\(app\)/labour-master/\[code\]/page.tsx \
          frontend/src/app/\(app\)/labour-master/new/page.tsx \
          frontend/src/app/\(app\)/labour-master/reference/page.tsx
  git commit -m "$(cat <<'EOF'
  feat(labour-master): cards, table, detail, new, reference routes

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 11 — End-to-end tests

### Task 11.1: Playwright spec

**Files:**
- Create: `frontend/e2e/tests/labour-master.spec.ts`

- [ ] **Step 1: Sample auth fixture**

  Read `frontend/e2e/fixtures/auth.fixture.ts` to copy the `login` helper signature.

- [ ] **Step 2: Write the spec**

  ```ts
  import { test, expect } from "@playwright/test";
  import { login } from "../fixtures/auth.fixture";

  test.describe("Labour Master", () => {
    test.beforeEach(async ({ page }) => {
      await login(page);
    });

    test("dashboard shows KPIs and category bar chart", async ({ page }) => {
      await page.goto("/labour-master");
      await expect(page.getByRole("heading", { name: "Labour Master" })).toBeVisible();
      for (const tile of ["Total Designations", "Total Workforce", "Daily Payroll", "Skill Categories", "Nationality Mix"]) {
        await expect(page.getByText(tile)).toBeVisible();
      }
    });

    test("cards filter by category narrows results", async ({ page }) => {
      await page.goto("/labour-master/cards");
      await page.getByRole("button", { name: /SM · Site Management/ }).click();
      await expect(page.locator("text=SM-001")).toBeVisible();
      await expect(page.locator("text=PO-001")).toHaveCount(0);
    });

    test("table view renders 44 rows after Oman seed", async ({ page }) => {
      await page.goto("/labour-master/table");
      await expect(page.locator("table tbody tr")).toHaveCount(44);
    });

    test("detail modal opens for SM-001 with PMP certification", async ({ page }) => {
      await page.goto("/labour-master/cards");
      await page.locator("text=SM-001").first().click();
      await expect(page.getByRole("dialog")).toContainText("Project Manager");
      await expect(page.getByRole("dialog")).toContainText("PMP");
    });

    test("add form creates a new designation and it appears in cards", async ({ page }) => {
      await page.goto("/labour-master/new");
      await page.getByLabel("Worker Code").fill("SM-099");
      await page.getByLabel("Designation").fill("Test Designation");
      await page.getByLabel("Trade").fill("Civil");
      await page.getByLabel("Daily Rate (OMR)").fill("100");
      await page.getByRole("button", { name: "Save" }).click();
      await expect(page).toHaveURL(/\/labour-master\/cards/);
      await expect(page.locator("text=SM-099")).toBeVisible();
    });
  });
  ```

- [ ] **Step 3: Run the test (backend in seed profile + frontend running)**

  In one terminal:
  ```bash
  (cd backend && SPRING_PROFILES_ACTIVE=seed mvn -q -pl bipros-api -am spring-boot:run)
  ```
  In another:
  ```bash
  (cd frontend && pnpm test:e2e -- labour-master.spec.ts)
  ```
  Expected: 5 tests pass.

  Note: the "44 rows" test depends on the Oman demo project existing. If your local seed doesn't include the Oman project, that test will fail. Either skip it locally with `test.skip` and rely on CI, or run the Oman project seeder first.

- [ ] **Step 4: Commit**

  ```bash
  git add frontend/e2e/tests/labour-master.spec.ts
  git commit -m "$(cat <<'EOF'
  test(labour-master): playwright e2e for the 6 labour-master screens

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  EOF
  )"
  ```

---

## Phase 12 — Final acceptance

### Task 12.1: Run the full backend test suite

- [ ] **Step 1:**
  ```bash
  (cd backend && mvn -q -pl bipros-resource test)
  ```
  Expected: all tests pass.

### Task 12.2: Run the frontend type-check and lint

- [ ] **Step 1:**
  ```bash
  (cd frontend && pnpm tsc --noEmit && pnpm lint)
  ```
  Expected: no errors.

### Task 12.3: Manual UI walkthrough

- [ ] **Step 1:** Start backend with `seed` profile and the frontend.
- [ ] **Step 2:** Open `http://localhost:3000/labour-master` and walk every tab. Verify:
  - Dashboard KPIs render with non-zero numbers.
  - Workforce-by-category chart shows 5 bars.
  - Cards page filters by category, grade, and search; clicking a card opens the detail modal.
  - Table page shows the full register with 44 rows (after Oman seed).
  - Detail modal shows skills and certifications; total daily cost is `workerCount × effectiveRate`.
  - Add form creates a new row that immediately appears.
  - Reference page shows the A–E table and the 10 Oman regulatory notes.
- [ ] **Step 3:** Take note of any console errors. Fix and recommit if found.

### Task 12.4: Open the PR

- [ ] **Step 1:** Push the branch and open a PR. Use the PR template if present; otherwise the title `feat(labour-master): Oman labour catalogue + 6-screen UI` and a body that links to `docs/superpowers/specs/2026-04-28-labour-master-module-design.md` and lists the screens implemented.

---

## Self-review

Spec coverage:
- §1 Goal — covered by Phases 2–10.
- §2 Scope — JSON arrays (Phase 2 ✓), no skill-master tables (intentionally omitted), enums fixed (Phase 1 ✓), OMR-default + currency column (Phase 2 ✓).
- §3 Architecture — module placement Phase 1–5, frontend Phase 8–10.
- §4 Data model — entities Phase 2, computed values Phase 4.4.
- §5 REST API — controllers Phase 5, validation in DTOs Phase 3.
- §6 Frontend — Phase 9–10.
- §7 Seed data — Phase 7 (seeder + script + dataset).
- §8 Testing — Phase 4 (unit), Phase 5.3 (integration), Phase 11 (e2e).
- §9 Deferred decisions — resolved at the top of the plan: dataset = JSON resource, no admin re-seed button, reference at its own route.
- §10 Acceptance criteria — Phase 12.

Type/method consistency:
- `LabourDesignationService.toResponse(d)` is used by `ProjectLabourDeploymentService.toResponse(...)` to populate the nested `designation` block — same name in both definitions ✓.
- Frontend `LabourDesignationResponse.deployment` shape matches `ProjectLabourDeploymentResponse` fields used to populate it in `cards/page.tsx` and `table/page.tsx` ✓.
- API URLs in `labourMasterApi.ts` match controller `@RequestMapping`s exactly ✓.
- Liquibase column types match JPA entity types (UUID, NUMERIC(10,2), JSONB, VARCHAR lengths) ✓.

Placeholder scan: explicit placeholders that the executing agent must replace are flagged inline with `<<...>>` (e.g., `<<OMAN_PROJECT_CODE>>`, `<<USE_ACTIVE_PROJECT>>`) and each is paired with a discovery step that tells the agent how to find the right value. No "TBD"/"TODO" patterns remain.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-29-labour-master-module.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
