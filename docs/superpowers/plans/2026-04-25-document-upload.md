# Document Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the project Documents tab usable end-to-end — auto-seed 7 standard folders per project, expose a "New folder" dialog (root and sub-folder), and fix the existing root-folder query bug so seeded folders actually surface.

**Architecture:** Spring `ApplicationEventPublisher` for project-creation seeding (event lives in `bipros-common`, listener in `bipros-document`); `bipros-api` adds an `ApplicationReadyEvent` backfill that covers projects created by demo seeders bypassing `ProjectService`; React Query mutation + custom modal component on the frontend reuses the existing `documentApi.createFolder()`.

**Tech Stack:** Java 23 + Spring Boot 3.5, JPA/Hibernate, JUnit 5 + Mockito, Next.js 16 + React 19, TanStack Query, Tailwind, Playwright.

**Spec:** `docs/superpowers/specs/2026-04-25-document-upload-design.md`

---

## File map

**Backend — new files**
- `backend/bipros-common/src/main/java/com/bipros/common/event/ProjectCreatedEvent.java` — immutable record event
- `backend/bipros-document/src/main/java/com/bipros/document/application/listener/DefaultFolderSeeder.java` — `@Component` with event listener + idempotent seed method
- `backend/bipros-document/src/test/java/com/bipros/document/application/listener/DefaultFolderSeederTest.java` — Mockito unit tests
- `backend/bipros-api/src/main/java/com/bipros/api/config/seeder/DefaultFolderStartupBackfill.java` — `ApplicationReadyEvent` listener, loops all projects

**Backend — modified files**
- `backend/bipros-document/src/main/java/com/bipros/document/domain/repository/DocumentFolderRepository.java` — add `findByProjectIdAndParentIdIsNullOrderBySortOrder`
- `backend/bipros-document/src/main/java/com/bipros/document/application/service/DocumentFolderService.java` — switch `listRootFolders` to the new query
- `backend/bipros-project/src/main/java/com/bipros/project/application/service/ProjectService.java` — inject `ApplicationEventPublisher`, publish event in `createProject(...)`

**Frontend — new files**
- `frontend/src/components/document/NewFolderDialog.tsx` — modal with name/code/category fields
- `frontend/e2e/tests/12-documents.spec.ts` — Playwright happy-path test
- `frontend/e2e/fixtures/sample.pdf` — small fixture file for upload assertion

**Frontend — modified files**
- `frontend/src/app/(app)/projects/[projectId]/documents/page.tsx` — add "New folder" button (sidebar header), per-row `+` button, dialog state, `createFolder` mutation, query invalidation, auto-select new folder

---

## Task 1: Add `ProjectCreatedEvent` to `bipros-common`

**Files:**
- Create: `backend/bipros-common/src/main/java/com/bipros/common/event/ProjectCreatedEvent.java`

- [ ] **Step 1: Create the event class**

```java
package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by ProjectService after a Project row is committed. Listeners run
 * via @TransactionalEventListener(AFTER_COMMIT), so the project is already
 * visible to fresh transactions opened in handlers.
 */
public record ProjectCreatedEvent(UUID projectId, String projectCode, String projectName) {
}
```

- [ ] **Step 2: Verify it compiles**

Run: `(cd backend && mvn -pl bipros-common compile -q)`
Expected: BUILD SUCCESS, no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-common/src/main/java/com/bipros/common/event/ProjectCreatedEvent.java
git commit -m "feat(common): add ProjectCreatedEvent for cross-module project lifecycle"
```

---

## Task 2: Publish `ProjectCreatedEvent` from `ProjectService.createProject`

**Files:**
- Modify: `backend/bipros-project/src/main/java/com/bipros/project/application/service/ProjectService.java`

- [ ] **Step 1: Add the event publisher field and import**

Add this import block alongside the existing imports near the top:

```java
import com.bipros.common.event.ProjectCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

Add this field to the dependency list (the class is `@RequiredArgsConstructor` so the constructor is generated; just add the `private final` field):

```java
    private final ApplicationEventPublisher eventPublisher;
```

Place it next to the other `private final` repository/service fields (around lines 44-49).

- [ ] **Step 2: Publish the event after project save**

In `createProject(...)` (line 51), find this section near the end (currently around lines 92-105):

```java
        Project saved = projectRepository.save(project);
        log.info("Project created with ID: {}", saved.getId());

        if (request.contract() != null) {
            upsertPrimaryContract(saved, request.contract());
        }

        // Audit log creation
        auditService.logCreate("Project", saved.getId(), buildProjectResponse(saved));

        // Auto-create root WBS node
        createRootWbsNode(saved);

        return buildProjectResponse(saved);
```

Replace with:

```java
        Project saved = projectRepository.save(project);
        log.info("Project created with ID: {}", saved.getId());

        if (request.contract() != null) {
            upsertPrimaryContract(saved, request.contract());
        }

        // Audit log creation
        auditService.logCreate("Project", saved.getId(), buildProjectResponse(saved));

        // Auto-create root WBS node
        createRootWbsNode(saved);

        eventPublisher.publishEvent(
            new ProjectCreatedEvent(saved.getId(), saved.getCode(), saved.getName())
        );

        return buildProjectResponse(saved);
```

- [ ] **Step 3: Verify it compiles**

Run: `(cd backend && mvn -pl bipros-project -am compile -q)`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/bipros-project/src/main/java/com/bipros/project/application/service/ProjectService.java
git commit -m "feat(project): publish ProjectCreatedEvent after project save"
```

---

## Task 3: Fix `listRootFolders` query bug — add `findByProjectIdAndParentIdIsNull` derived method

**Files:**
- Modify: `backend/bipros-document/src/main/java/com/bipros/document/domain/repository/DocumentFolderRepository.java`
- Modify: `backend/bipros-document/src/main/java/com/bipros/document/application/service/DocumentFolderService.java`

The current code calls `findByProjectIdAndParentIdOrderBySortOrder(projectId, null)`. Spring Data binds the second arg as SQL `parent_id = ?` with NULL — never matches. We add a proper `IsNull` derived query and switch the service over.

- [ ] **Step 1: Add the new repository method**

Replace the entire `DocumentFolderRepository.java` file:

```java
package com.bipros.document.domain.repository;

import com.bipros.document.domain.model.DocumentFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentFolderRepository extends JpaRepository<DocumentFolder, UUID> {
    List<DocumentFolder> findByProjectIdOrderBySortOrder(UUID projectId);

    List<DocumentFolder> findByProjectIdAndParentIdOrderBySortOrder(UUID projectId, UUID parentId);

    /**
     * Returns the project's root folders (parentId IS NULL). Used both by the
     * Documents UI sidebar and by DefaultFolderSeeder's idempotency check.
     * Distinct from the {@code AndParentId} variant above because Spring Data
     * derived queries bind a null parameter as SQL {@code = NULL}, which never
     * matches; we need explicit {@code IS NULL}.
     */
    List<DocumentFolder> findByProjectIdAndParentIdIsNullOrderBySortOrder(UUID projectId);

    Optional<DocumentFolder> findByProjectIdAndId(UUID projectId, UUID id);
}
```

- [ ] **Step 2: Switch `listRootFolders` to use the new method**

In `DocumentFolderService.java`, find this method (around line 47):

```java
    @Transactional(readOnly = true)
    public List<DocumentFolderResponse> listRootFolders(UUID projectId) {
        return folderRepository.findByProjectIdAndParentIdOrderBySortOrder(projectId, null)
            .stream()
            .map(DocumentFolderResponse::from)
            .toList();
    }
```

Replace with:

```java
    @Transactional(readOnly = true)
    public List<DocumentFolderResponse> listRootFolders(UUID projectId) {
        return folderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId)
            .stream()
            .map(DocumentFolderResponse::from)
            .toList();
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `(cd backend && mvn -pl bipros-document -am compile -q)`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/bipros-document/src/main/java/com/bipros/document/domain/repository/DocumentFolderRepository.java backend/bipros-document/src/main/java/com/bipros/document/application/service/DocumentFolderService.java
git commit -m "fix(document): listRootFolders binds null as IS NULL (was broken =NULL)"
```

---

## Task 4: Write failing test for `DefaultFolderSeeder.seedDefaultsIfMissing` — happy path

**Files:**
- Test: `backend/bipros-document/src/test/java/com/bipros/document/application/listener/DefaultFolderSeederTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bipros.document.application.listener;

import com.bipros.common.event.ProjectCreatedEvent;
import com.bipros.document.domain.model.DocumentCategory;
import com.bipros.document.domain.model.DocumentFolder;
import com.bipros.document.domain.repository.DocumentFolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultFolderSeeder")
class DefaultFolderSeederTest {

    @Mock private DocumentFolderRepository folderRepository;

    private DefaultFolderSeeder seeder;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        seeder = new DefaultFolderSeeder(folderRepository);
        projectId = UUID.randomUUID();
    }

    @Test
    @DisplayName("creates 7 root folders in canonical order when none exist")
    void onProjectCreated_createsSevenRootFolders_whenNoneExist() {
        when(folderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId))
            .thenReturn(Collections.emptyList());

        seeder.onProjectCreated(new ProjectCreatedEvent(projectId, "P-001", "Test Project"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentFolder>> captor = ArgumentCaptor.forClass(List.class);
        verify(folderRepository).saveAll(captor.capture());

        List<DocumentFolder> created = captor.getValue();
        assertThat(created).hasSize(7);
        assertThat(created).extracting(DocumentFolder::getName)
            .containsExactly("Drawings", "Specifications", "Contracts",
                "Approvals", "Correspondence", "As-Built", "General");
        assertThat(created).extracting(DocumentFolder::getCode)
            .containsExactly("DRW", "SPEC", "CON", "APR", "COR", "AB", "GEN");
        assertThat(created).extracting(DocumentFolder::getCategory)
            .containsExactly(DocumentCategory.DRAWING, DocumentCategory.SPECIFICATION,
                DocumentCategory.CONTRACT, DocumentCategory.APPROVAL,
                DocumentCategory.CORRESPONDENCE, DocumentCategory.AS_BUILT,
                DocumentCategory.GENERAL);
        assertThat(created).extracting(DocumentFolder::getSortOrder)
            .containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(created).allMatch(f -> f.getProjectId().equals(projectId));
        assertThat(created).allMatch(f -> f.getParentId() == null);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `(cd backend && mvn -pl bipros-document -am test -Dtest=DefaultFolderSeederTest -q)`
Expected: COMPILATION ERROR — `DefaultFolderSeeder` class does not exist.

---

## Task 5: Implement `DefaultFolderSeeder` — minimum to pass the happy-path test

**Files:**
- Create: `backend/bipros-document/src/main/java/com/bipros/document/application/listener/DefaultFolderSeeder.java`

- [ ] **Step 1: Create the seeder class**

```java
package com.bipros.document.application.listener;

import com.bipros.common.event.ProjectCreatedEvent;
import com.bipros.document.domain.model.DocumentCategory;
import com.bipros.document.domain.model.DocumentFolder;
import com.bipros.document.domain.repository.DocumentFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Auto-seeds 7 standard root folders for a project. Two entry points:
 *  - @TransactionalEventListener for normal project creation via ProjectService
 *  - public seedDefaultsIfMissing(projectId) for the bipros-api startup backfill
 *    that covers demo seeders bypassing ProjectService.
 *
 * Idempotent: skips when the project already has root folders.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultFolderSeeder {

    private final DocumentFolderRepository folderRepository;

    private record FolderTemplate(String name, String code, DocumentCategory category) {}

    private static final List<FolderTemplate> DEFAULTS = List.of(
        new FolderTemplate("Drawings",       "DRW",  DocumentCategory.DRAWING),
        new FolderTemplate("Specifications", "SPEC", DocumentCategory.SPECIFICATION),
        new FolderTemplate("Contracts",      "CON",  DocumentCategory.CONTRACT),
        new FolderTemplate("Approvals",      "APR",  DocumentCategory.APPROVAL),
        new FolderTemplate("Correspondence", "COR",  DocumentCategory.CORRESPONDENCE),
        new FolderTemplate("As-Built",       "AB",   DocumentCategory.AS_BUILT),
        new FolderTemplate("General",        "GEN",  DocumentCategory.GENERAL)
    );

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProjectCreated(ProjectCreatedEvent event) {
        try {
            seedDefaultsIfMissing(event.projectId());
        } catch (RuntimeException e) {
            log.warn("DefaultFolderSeeder failed for project {}: {}", event.projectId(), e.getMessage(), e);
        }
    }

    /**
     * Idempotent: returns immediately if any root folder exists for the project.
     * Otherwise creates the 7 canonical folders in a single saveAll batch.
     * Public so {@code DefaultFolderStartupBackfill} (in bipros-api) can call it.
     */
    @Transactional
    public void seedDefaultsIfMissing(UUID projectId) {
        List<DocumentFolder> existing =
            folderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId);
        if (!existing.isEmpty()) {
            log.info("DefaultFolderSeeder: project {} already has {} root folders, skipping",
                projectId, existing.size());
            return;
        }

        List<DocumentFolder> toCreate = new ArrayList<>(DEFAULTS.size());
        for (int i = 0; i < DEFAULTS.size(); i++) {
            FolderTemplate t = DEFAULTS.get(i);
            DocumentFolder f = new DocumentFolder();
            f.setProjectId(projectId);
            f.setParentId(null);
            f.setName(t.name());
            f.setCode(t.code());
            f.setCategory(t.category());
            f.setSortOrder(i);
            toCreate.add(f);
        }
        folderRepository.saveAll(toCreate);
        log.info("DefaultFolderSeeder created {} root folders for project {}",
            toCreate.size(), projectId);
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `(cd backend && mvn -pl bipros-document -am test -Dtest=DefaultFolderSeederTest -q)`
Expected: 1 test passes.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-document/src/main/java/com/bipros/document/application/listener/DefaultFolderSeeder.java backend/bipros-document/src/test/java/com/bipros/document/application/listener/DefaultFolderSeederTest.java
git commit -m "feat(document): DefaultFolderSeeder creates 7 standard folders per project"
```

---

## Task 6: Add idempotency test (skip when folders exist)

**Files:**
- Modify: `backend/bipros-document/src/test/java/com/bipros/document/application/listener/DefaultFolderSeederTest.java`

- [ ] **Step 1: Append the test**

Add this method inside the existing `DefaultFolderSeederTest` class (before the closing `}`):

```java
    @Test
    @DisplayName("is idempotent — does not duplicate when root folders already exist")
    void onProjectCreated_isIdempotent_whenRootFoldersExist() {
        DocumentFolder existing = new DocumentFolder();
        existing.setProjectId(projectId);
        existing.setName("Drawings");
        existing.setCode("DRW");
        existing.setCategory(DocumentCategory.DRAWING);
        existing.setSortOrder(0);
        when(folderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId))
            .thenReturn(List.of(existing));

        seeder.onProjectCreated(new ProjectCreatedEvent(projectId, "P-001", "Test Project"));

        verify(folderRepository, never()).saveAll(anyList());
        verify(folderRepository, never()).save(any(DocumentFolder.class));
    }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `(cd backend && mvn -pl bipros-document test -Dtest=DefaultFolderSeederTest -q)`
Expected: 2 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-document/src/test/java/com/bipros/document/application/listener/DefaultFolderSeederTest.java
git commit -m "test(document): DefaultFolderSeeder is idempotent when folders exist"
```

---

## Task 7: Add failure-isolation test (event listener swallows exceptions)

**Files:**
- Modify: `backend/bipros-document/src/test/java/com/bipros/document/application/listener/DefaultFolderSeederTest.java`

- [ ] **Step 1: Append the test**

Add inside the test class:

```java
    @Test
    @DisplayName("does not propagate exceptions from the event listener")
    void onProjectCreated_doesNotPropagate_whenSeederFails() {
        when(folderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId))
            .thenThrow(new RuntimeException("simulated DB failure"));

        assertThatCode(() ->
            seeder.onProjectCreated(new ProjectCreatedEvent(projectId, "P-001", "Test Project"))
        ).doesNotThrowAnyException();
    }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `(cd backend && mvn -pl bipros-document test -Dtest=DefaultFolderSeederTest -q)`
Expected: 3 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-document/src/test/java/com/bipros/document/application/listener/DefaultFolderSeederTest.java
git commit -m "test(document): DefaultFolderSeeder swallows listener exceptions"
```

---

## Task 8: Add startup backfill in `bipros-api`

**Files:**
- Create: `backend/bipros-api/src/main/java/com/bipros/api/config/seeder/DefaultFolderStartupBackfill.java`

- [ ] **Step 1: Create the backfill component**

```java
package com.bipros.api.config.seeder;

import com.bipros.document.application.listener.DefaultFolderSeeder;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Demo seeders (NhaiRoadProjectSeeder, IcpmsPhaseASeeder, IoclPanipatSeeder)
 * implement CommandLineRunner and call projectRepository.save(project)
 * directly, bypassing ProjectService.createProject() — so ProjectCreatedEvent
 * never fires for them. Spring Boot guarantees ApplicationReadyEvent fires
 * after every CommandLineRunner has completed; we hook there and ensure each
 * existing project has the default folder set, calling the same idempotent
 * seed method used by the live event listener.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultFolderStartupBackfill {

    private final ProjectRepository projectRepository;
    private final DefaultFolderSeeder defaultFolderSeeder;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("DefaultFolderStartupBackfill: scanning projects for missing default folders");
        int processed = 0;
        for (var project : projectRepository.findAll()) {
            try {
                defaultFolderSeeder.seedDefaultsIfMissing(project.getId());
                processed++;
            } catch (RuntimeException e) {
                log.warn("DefaultFolderStartupBackfill failed for project {}: {}",
                    project.getId(), e.getMessage(), e);
            }
        }
        log.info("DefaultFolderStartupBackfill: processed {} projects", processed);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `(cd backend && mvn -pl bipros-api -am compile -q)`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Boot-test the full stack manually**

Start Postgres + the backend in one terminal:
```bash
docker compose up -d
(cd backend && mvn spring-boot:run -pl bipros-api)
```
Wait for `Started BiprosApplication` in the log. You should see lines like:
```
DefaultFolderStartupBackfill: scanning projects for missing default folders
DefaultFolderSeeder created 7 root folders for project <uuid>
... (one per seeded project)
DefaultFolderStartupBackfill: processed N projects
```

Hit the API for any seeded project to confirm the folders surface. First grab a token, then a project id, then list root folders:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')
PID=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/projects | jq -r '.data.content[0].id')
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/v1/projects/$PID/document-folders/root" | jq '.data | length, [.[].name]'
```
Expected: `7` and `["Drawings","Specifications","Contracts","Approvals","Correspondence","As-Built","General"]`.

Stop the backend (Ctrl-C in the spring-boot:run terminal) before continuing.

- [ ] **Step 4: Commit**

```bash
git add backend/bipros-api/src/main/java/com/bipros/api/config/seeder/DefaultFolderStartupBackfill.java
git commit -m "feat(api): backfill default folders for projects created outside ProjectService"
```

---

## Task 9: Run the full backend test suite to make sure nothing else regressed

- [ ] **Step 1: Run all backend tests**

Run: `(cd backend && mvn test -q)`
Expected: BUILD SUCCESS, all existing tests still pass.

If anything fails, fix it before continuing. Common suspects: `ProjectService` constructor signature change cascading into existing tests that hand-build the service.

---

## Task 10: Frontend — create the `NewFolderDialog` component

**Files:**
- Create: `frontend/src/components/document/NewFolderDialog.tsx`

The dialog mirrors the styling of `frontend/src/components/common/ConfirmDialog.tsx` so it fits the existing white-gold theme. It accepts a parent folder (or null for root creation), opens controlled by `open`, and calls `onSubmit` with the form values. Loading + error states are owned by the parent.

- [ ] **Step 1: Create the component**

```tsx
"use client";

import { useEffect, useRef, useState } from "react";
import { FolderPlus } from "lucide-react";
import type { DocumentFolder } from "@/lib/api/documentApi";

const CATEGORY_OPTIONS = [
  "DPR",
  "DRAWING",
  "SPECIFICATION",
  "CONTRACT",
  "APPROVAL",
  "CORRESPONDENCE",
  "AS_BUILT",
  "GENERAL",
] as const;

export interface NewFolderFormValues {
  name: string;
  code: string;
  category: string;
}

interface NewFolderDialogProps {
  open: boolean;
  parent: DocumentFolder | null;
  submitting: boolean;
  errorMessage: string | null;
  onSubmit: (values: NewFolderFormValues) => void;
  onCancel: () => void;
}

export function NewFolderDialog({
  open,
  parent,
  submitting,
  errorMessage,
  onSubmit,
  onCancel,
}: NewFolderDialogProps) {
  const nameRef = useRef<HTMLInputElement>(null);
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [category, setCategory] = useState<string>("GENERAL");

  // Reset fields whenever the dialog (re-)opens, and pre-fill category from parent.
  useEffect(() => {
    if (open) {
      setName("");
      setCode("");
      setCategory(parent?.category ?? "GENERAL");
      // Defer focus to next tick so the input exists in the DOM.
      requestAnimationFrame(() => nameRef.current?.focus());
    }
  }, [open, parent]);

  useEffect(() => {
    if (!open) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCancel();
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [open, onCancel]);

  if (!open) return null;

  const title = parent ? `New sub-folder under ${parent.name}` : "New folder";

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    onSubmit({ name: name.trim(), code: code.trim(), category });
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
      role="dialog"
      aria-modal="true"
      aria-labelledby="new-folder-title"
    >
      <div className="w-full max-w-md rounded-xl border border-border bg-surface p-6 shadow-xl">
        <div className="flex items-start gap-3">
          <div className="mt-0.5 text-accent">
            <FolderPlus size={20} />
          </div>
          <div className="flex-1">
            <h3 id="new-folder-title" className="text-base font-semibold text-text-primary">
              {title}
            </h3>
            <p className="mt-1 text-xs text-text-muted">
              Folders organise documents in this project. Both name and code are required.
            </p>
          </div>
        </div>

        {errorMessage && (
          <div className="mt-4 rounded-md bg-danger/10 p-3 text-sm text-danger">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-3">
          <div>
            <label htmlFor="new-folder-name" className="mb-1 block text-sm font-medium text-text-secondary">
              Name
            </label>
            <input
              id="new-folder-name"
              ref={nameRef}
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={255}
              required
              className="w-full rounded-lg border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
              placeholder="e.g., Site Photos"
            />
          </div>

          <div>
            <label htmlFor="new-folder-code" className="mb-1 block text-sm font-medium text-text-secondary">
              Code
            </label>
            <input
              id="new-folder-code"
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              maxLength={100}
              required
              className="w-full rounded-lg border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
              placeholder="e.g., SP"
            />
          </div>

          <div>
            <label htmlFor="new-folder-category" className="mb-1 block text-sm font-medium text-text-secondary">
              Category
            </label>
            <select
              id="new-folder-category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="w-full rounded-lg border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:ring-2 focus:ring-accent focus:ring-opacity-50"
            >
              {CATEGORY_OPTIONS.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </div>

          <div className="mt-5 flex justify-end gap-2">
            <button
              type="button"
              onClick={onCancel}
              className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover focus:outline-none focus:ring-1 focus:ring-border"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !name.trim() || !code.trim()}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-background"
            >
              {submitting ? "Creating..." : "Create folder"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `(cd frontend && pnpm tsc --noEmit)`
Expected: no errors. (If `lucide-react`'s `FolderPlus` is not present, swap to a literal `+` icon — verify with `grep FolderPlus frontend/node_modules/lucide-react/dist/esm/dynamicIconImports.d.ts`.)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/document/NewFolderDialog.tsx
git commit -m "feat(document): NewFolderDialog component for root + sub-folder creation"
```

---

## Task 11: Frontend — wire `NewFolderDialog` into the Documents page

**Files:**
- Modify: `frontend/src/app/(app)/projects/[projectId]/documents/page.tsx`

Add: dialog open/close state, the parent context (null for root, a folder for sub), a `useMutation` hook that calls `documentApi.createFolder`, success handling that invalidates queries and auto-selects the new folder, and the actual UI buttons (header `+ New folder` and per-row `+`).

- [ ] **Step 1: Add the import**

Near the top with the other component imports, add:

```tsx
import { NewFolderDialog, type NewFolderFormValues } from "@/components/document/NewFolderDialog";
```

- [ ] **Step 2: Add state and mutation inside the component**

Just after the existing `uploadDocumentMutation` declaration (around line 99), add:

```tsx
  const [newFolderParent, setNewFolderParent] = useState<DocumentFolder | null>(null);
  const [newFolderOpen, setNewFolderOpen] = useState(false);
  const [newFolderError, setNewFolderError] = useState<string | null>(null);

  const createFolderMutation = useMutation({
    mutationFn: (values: NewFolderFormValues) =>
      documentApi.createFolder(projectId, {
        name: values.name,
        code: values.code,
        category: values.category,
        parentId: newFolderParent?.id ?? null,
      }),
    onSuccess: (response) => {
      const created = response.data;
      toast.success("Folder created");
      setNewFolderOpen(false);
      setNewFolderError(null);
      queryClient.invalidateQueries({ queryKey: ["folders", projectId, "root"] });
      if (newFolderParent) {
        queryClient.invalidateQueries({ queryKey: ["folders", projectId, "children"] });
        // Make sure the parent is expanded so the new child is visible.
        setExpandedFolders((prev) => {
          if (prev.has(newFolderParent.id)) return prev;
          const next = new Set(prev);
          next.add(newFolderParent.id);
          return next;
        });
      }
      if (created?.id) {
        setSelectedFolderId(created.id);
      }
      setNewFolderParent(null);
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to create folder");
      setNewFolderError(msg);
      toast.error(msg);
    },
  });

  const openCreateRoot = () => {
    setNewFolderParent(null);
    setNewFolderError(null);
    setNewFolderOpen(true);
  };

  const openCreateChild = (parent: DocumentFolder) => {
    setNewFolderParent(parent);
    setNewFolderError(null);
    setNewFolderOpen(true);
  };
```

- [ ] **Step 3: Update the sidebar header to show the "+ New folder" button**

Find the existing sidebar block (around lines 224-231):

```tsx
      <div className="col-span-1 bg-surface/50 rounded-xl border border-border p-4 overflow-y-auto shadow-xl">
        <h2 className="text-sm font-semibold text-text-secondary mb-4">Folders</h2>
        {rootFolders.length > 0 ? (
          renderFolderTree(rootFolders)
        ) : (
          <p className="text-sm text-text-muted">No folders</p>
        )}
      </div>
```

Replace with:

```tsx
      <div className="col-span-1 bg-surface/50 rounded-xl border border-border p-4 overflow-y-auto shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-text-secondary">Folders</h2>
          <button
            onClick={openCreateRoot}
            className="rounded-md border border-border bg-surface-hover px-2 py-1 text-xs font-medium text-text-secondary hover:bg-surface-active hover:text-text-primary"
            data-testid="new-folder-root"
          >
            + New folder
          </button>
        </div>
        {rootFolders.length > 0 ? (
          renderFolderTree(rootFolders)
        ) : (
          <div className="text-sm text-text-muted">
            <p>No folders yet.</p>
            <p className="mt-1">Click &ldquo;+ New folder&rdquo; to create one.</p>
          </div>
        )}
      </div>
```

- [ ] **Step 4: Add per-row `+` button to the folder tree**

Find `renderFolderTree` (around lines 171-214). Locate the existing row markup:

```tsx
              <div className="flex items-center gap-2">
                {hasChildren && (
                  <button
                    onClick={() => toggleFolderExpansion(folder.id)}
                    className="flex items-center justify-center w-5 h-5 hover:bg-surface-active rounded"
                  >
                    <span className={`transition-transform ${isExpanded ? "rotate-90" : ""}`}>
                      ▶
                    </span>
                  </button>
                )}
                {!hasChildren && <div className="w-5" />}
                <button
                  onClick={() => setSelectedFolderId(folder.id)}
                  className={`flex-1 text-left px-2 py-1 rounded transition-colors ${
                    selectedFolderId === folder.id
                      ? "bg-accent/10 text-accent ring-1 ring-accent/20"
                      : "hover:bg-surface-hover/30 text-text-secondary"
                  }`}
                >
                  <span className="text-sm">📁 {folder.name}</span>
                </button>
              </div>
```

Replace with:

```tsx
              <div className="group flex items-center gap-2">
                {hasChildren && (
                  <button
                    onClick={() => toggleFolderExpansion(folder.id)}
                    className="flex items-center justify-center w-5 h-5 hover:bg-surface-active rounded"
                  >
                    <span className={`transition-transform ${isExpanded ? "rotate-90" : ""}`}>
                      ▶
                    </span>
                  </button>
                )}
                {!hasChildren && <div className="w-5" />}
                <button
                  onClick={() => setSelectedFolderId(folder.id)}
                  className={`flex-1 text-left px-2 py-1 rounded transition-colors ${
                    selectedFolderId === folder.id
                      ? "bg-accent/10 text-accent ring-1 ring-accent/20"
                      : "hover:bg-surface-hover/30 text-text-secondary"
                  }`}
                >
                  <span className="text-sm">📁 {folder.name}</span>
                </button>
                <button
                  type="button"
                  onClick={() => openCreateChild(folder)}
                  title={`New sub-folder under ${folder.name}`}
                  aria-label={`New sub-folder under ${folder.name}`}
                  data-testid={`new-folder-child-${folder.id}`}
                  className="opacity-0 group-hover:opacity-100 transition-opacity rounded px-1.5 py-0.5 text-xs text-text-secondary hover:bg-surface-active hover:text-text-primary"
                >
                  +
                </button>
              </div>
```

- [ ] **Step 5: Render the dialog at the end of the component JSX**

Find the outermost return's closing structure (around lines 430-433):

```tsx
      </div>
      </div>
    </div>
  );
}
```

Replace with:

```tsx
      </div>
      </div>
      <NewFolderDialog
        open={newFolderOpen}
        parent={newFolderParent}
        submitting={createFolderMutation.isPending}
        errorMessage={newFolderError}
        onSubmit={(values) => createFolderMutation.mutate(values)}
        onCancel={() => {
          setNewFolderOpen(false);
          setNewFolderError(null);
          setNewFolderParent(null);
        }}
      />
    </div>
  );
}
```

- [ ] **Step 6: Verify TypeScript compiles and ESLint passes**

Run: `(cd frontend && pnpm tsc --noEmit && pnpm lint -- --max-warnings=0)`
Expected: no type errors, lint clean.

- [ ] **Step 7: Smoke-test in the browser**

Make sure backend + frontend are both running:
```bash
docker compose up -d
(cd backend && mvn spring-boot:run -pl bipros-api &)
(cd frontend && pnpm dev)
```
Open http://localhost:3000, log in (`admin` / `admin123`), pick a project, click **Documents**.
Verify each of the following manually:
1. Sidebar shows the 7 default folders (Drawings, Specifications, Contracts, Approvals, Correspondence, As-Built, General).
2. Click "+ New folder" → dialog opens with title "New folder" → fill name "Site Photos", code "SP" → submit → dialog closes, "Site Photos" appears at the bottom of the list and is selected.
3. Hover "Drawings" → "+" appears on the right → click → dialog opens with title "New sub-folder under Drawings" → submit name "Plan View", code "PV" → expand "Drawings" → "Plan View" is visible underneath.
4. With "Site Photos" selected, click "+ Upload Document" → fill title, document number → pick a small file → submit → row appears in the table → click Download → file downloads.

If any step fails, fix and re-run before committing.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/\(app\)/projects/\[projectId\]/documents/page.tsx
git commit -m "feat(document): NewFolder UI with root + sub-folder entry points"
```

---

## Task 12: Add Playwright e2e test

**Files:**
- Create: `frontend/e2e/fixtures/sample.pdf`
- Create: `frontend/e2e/tests/12-documents.spec.ts`

- [ ] **Step 1: Create the PDF fixture**

A minimal valid PDF (compatible with all PDF parsers) is small enough to commit. Generate it from the shell using ghostscript or a one-liner. Easiest: use Playwright's existing `frontend/e2e` infrastructure to write a tiny dummy file. Run from the repo root:

```bash
printf '%%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000051 00000 n\n0000000093 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n143\n%%%%EOF' > frontend/e2e/fixtures/sample.pdf
```

Verify it's recognized as a PDF:
```bash
file frontend/e2e/fixtures/sample.pdf
```
Expected: `frontend/e2e/fixtures/sample.pdf: PDF document, version 1.4`

- [ ] **Step 2: Create the test file**

```ts
import path from "node:path";
import { test, expect } from "../fixtures/auth.fixture";

test.describe("Documents tab", () => {
  test("default folders, create custom folder, upload + download", async ({ authenticatedPage: page }) => {
    // Navigate to the first seeded project's Documents tab
    await page.goto("/projects");
    await page.getByRole("table").getByRole("link").first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });

    // The Documents tab link sits in the project tab strip
    await page.getByRole("link", { name: "Documents" }).first().click();
    await expect(page.getByRole("heading", { name: "Document Management" })).toBeVisible();

    // 1. Default folders are present
    await expect(page.getByText("📁 Drawings")).toBeVisible();
    await expect(page.getByText("📁 Specifications")).toBeVisible();
    await expect(page.getByText("📁 Contracts")).toBeVisible();
    await expect(page.getByText("📁 Approvals")).toBeVisible();
    await expect(page.getByText("📁 Correspondence")).toBeVisible();
    await expect(page.getByText("📁 As-Built")).toBeVisible();
    await expect(page.getByText("📁 General")).toBeVisible();

    // 2. Create a custom root folder
    const folderName = `E2E Folder ${Date.now()}`;
    await page.getByTestId("new-folder-root").click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.getByLabel("Name").fill(folderName);
    await page.getByLabel("Code").fill(`E2E${Date.now() % 10000}`);
    await page.getByRole("button", { name: "Create folder" }).click();
    await expect(page.getByRole("dialog")).toBeHidden();
    await expect(page.getByText(`📁 ${folderName}`)).toBeVisible();

    // The new folder is auto-selected → upload form is reachable
    await page.getByRole("button", { name: "+ Upload Document" }).click();

    // 3. Upload a file
    const docTitle = `E2E Doc ${Date.now()}`;
    const docNumber = `DOC-${Date.now() % 100000}`;
    await page.getByPlaceholder("Document title").fill(docTitle);
    await page.getByPlaceholder("e.g., DOC-001").fill(docNumber);
    await page.locator('input[type="file"]').setInputFiles(
      path.resolve(__dirname, "../fixtures/sample.pdf")
    );
    await page.getByRole("button", { name: /Upload Document/i }).last().click();

    // 4. Verify document appears in the table
    await expect(page.getByRole("cell", { name: docTitle })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("cell", { name: docNumber })).toBeVisible();

    // 5. Download the file (triggers a browser save event)
    const downloadPromise = page.waitForEvent("download");
    await page.getByRole("button", { name: "Download" }).first().click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.pdf$/i);

    // 6. Sub-folder under Drawings — hover reveals "+" button; create child
    const drawingsRow = page.locator("li", { has: page.getByText("📁 Drawings") }).first();
    await drawingsRow.hover();
    const subName = `Plan View ${Date.now()}`;
    await drawingsRow.getByRole("button", { name: /New sub-folder under Drawings/i }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: /New sub-folder under Drawings/i })
    ).toBeVisible();
    await page.getByLabel("Name").fill(subName);
    await page.getByLabel("Code").fill(`PV${Date.now() % 10000}`);
    await page.getByRole("button", { name: "Create folder" }).click();
    await expect(page.getByRole("dialog")).toBeHidden();
    // Drawings is auto-expanded by the success handler so the new child is visible.
    await expect(page.getByText(`📁 ${subName}`)).toBeVisible();
  });
});
```

- [ ] **Step 3: Run the test (backend must be running)**

In one terminal, restart the backend so the new code paths are loaded:
```bash
(cd backend && mvn spring-boot:run -pl bipros-api)
```
In another terminal:
```bash
(cd frontend && pnpm test:e2e -- 12-documents)
```
Expected: 1 test passes.

If the test fails on locator ambiguity (e.g., a folder name appears twice because Drawings sub-folders match), adjust the locator with `.first()` or a more specific selector before continuing — do not commit a flaky test.

- [ ] **Step 4: Commit**

```bash
git add frontend/e2e/fixtures/sample.pdf frontend/e2e/tests/12-documents.spec.ts
git commit -m "test(e2e): documents tab end-to-end — default folders, create, upload, download"
```

---

## Task 13: Final verification — full backend + e2e suite green

- [ ] **Step 1: Re-run all backend tests one more time**

Run: `(cd backend && mvn test -q)`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Re-run the full e2e suite**

```bash
(cd frontend && pnpm test:e2e)
```
Expected: every spec passes, including the new `12-documents`.

- [ ] **Step 3: Acceptance walkthrough**

Manually verify all eight acceptance criteria from the spec (`docs/superpowers/specs/2026-04-25-document-upload-design.md` § "Acceptance criteria"). Report each as ✅ or ❌ in the final summary.

If all green, the implementation is complete. Move on to the requesting-code-review skill if/when the user asks for review or PR creation.
