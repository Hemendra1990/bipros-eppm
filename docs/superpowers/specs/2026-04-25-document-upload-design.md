# Document Upload — Make It Usable

**Date:** 2026-04-25
**Status:** Approved
**Owner:** hemendra

## Problem

A user opens a project's **Documents** tab and sees:

```
Folders                Select a folder to view documents
No folders
```

There is no upload control, no "New folder" button, and no path forward. They are stuck.

The backend (`bipros-document`) is feature-complete: multipart upload, hierarchical folders, versioning, transmittals, RFI/drawing registers — all working. The frontend page (`frontend/src/app/(app)/projects/[projectId]/documents/page.tsx`) is ~95% wired: it fetches root folders, lazy-loads children, has an upload form, downloads files. The upload form is gated behind `selectedFolderId`, and there are no folders to select because:

1. The UI has no way to create a folder.
2. No seeder populates folders when a project is created.

## Goal

When a user opens the Documents tab on a newly created project they should:

1. Immediately see a sensible default folder structure.
2. Be able to upload a file into any folder.
3. Be able to create their own custom folders (root or sub-folder) when the defaults aren't enough.

## Non-Goals

Explicitly **out of scope** for this spec — these are valid features but belong in separate work items:

- Versions UI (upload-new-version button, version history dialog). Backend exists; frontend not wired.
- Transmittals UI.
- Folder rename / move / delete UI. (Backend `DELETE` endpoint exists; we don't expose it yet.)
- Document edit/delete UI beyond what already exists.
- Backfilling default folders into pre-existing projects. Dev DB is `create-drop`; demo seeders re-create projects on each boot, so they will pick up the new folders automatically. There are no production projects.
- Permission model changes. Existing `@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")` is unchanged.

## Architecture

Two pieces of work, decoupled across the existing module boundaries:

### 1. Backend — auto-seed default folders on project creation

**Module boundary rule (from CLAUDE.md):** `bipros-project` and `bipros-document` must not depend on each other directly. Cross-module communication flows through `bipros-common`.

**Mechanism:** Spring `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)`.

- New event class `ProjectCreatedEvent` lives in `bipros-common` (`com.bipros.common.event.ProjectCreatedEvent`). Carries `projectId: UUID`, `projectCode: String`, `projectName: String`. Immutable record.
- `bipros-project`'s `ProjectService.createProject()` injects `ApplicationEventPublisher` and calls `publishEvent(...)` after the project is saved (still inside the transaction; the listener runs after commit).
- `DocumentFolderRepository` gets a new derived query: `List<DocumentFolder> findByProjectIdAndParentIdIsNullOrderBySortOrder(UUID projectId)`. **This also fixes an existing bug:** `DocumentFolderService.listRootFolders()` currently calls `findByProjectIdAndParentIdOrderBySortOrder(projectId, null)`, which Spring Data binds as SQL `WHERE parent_id = NULL` — never matches, always returns empty. Switch the service to the new `IsNull` method so the root-folder endpoint actually returns rows. (This is the bug fix that makes seeded folders visible end-to-end.)
- `bipros-document` adds `DefaultFolderSeeder` — a `@Component` with one method:

  ```java
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProjectCreated(ProjectCreatedEvent event) { ... }
  ```

  The listener:
  1. Calls `documentFolderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(event.projectId())`.
  2. If non-empty → log INFO and return (idempotent guard).
  3. Otherwise creates 7 root `DocumentFolder` rows in this order, with `sortOrder` 0..6:

     | Name             | Code  | Category       |
     |------------------|-------|----------------|
     | Drawings         | DRW   | DRAWING        |
     | Specifications   | SPEC  | SPECIFICATION  |
     | Contracts        | CON   | CONTRACT       |
     | Approvals        | APR   | APPROVAL       |
     | Correspondence   | COR   | CORRESPONDENCE |
     | As-Built         | AB    | AS_BUILT       |
     | General          | GEN   | GENERAL        |

- Failures inside the listener are caught and logged at WARN (do not propagate). The project is already committed; folder seeding is best-effort. A user who hits an empty Documents tab can still use the manual "New folder" button.

**Why `AFTER_COMMIT` and not the default `AFTER_COMPLETION` or synchronous?** We want the project row to be visible to the listener's transaction (the seeder opens a fresh `@Transactional` to write folders). If the project transaction rolls back, no folders are created — the project never existed.

**Startup backfill — required because demo seeders bypass `ProjectService`:** `IcpmsPhaseASeeder`, `NhaiRoadProjectSeeder`, `IoclPanipatSeeder` and other demo seeders implement `CommandLineRunner` and construct `Project` directly via `projectRepository.save(project)`, never going through `ProjectService.createProject()`. The event therefore won't fire for demo projects on a `create-drop` boot, and the Documents tab would still show "No folders".

To cover this without violating the "modules don't depend on each other" rule (CLAUDE.md), the backfill lives in **`bipros-api`** (which already depends on every module):

- `DefaultFolderSeeder` (in `bipros-document`) exposes a public method `seedDefaultsIfMissing(UUID projectId)` containing the same idempotent logic the event listener uses. It depends only on `DocumentFolderRepository`.
- New class `DefaultFolderStartupBackfill` (in `bipros-api/src/main/java/com/bipros/api/config/seeder/`) is a `@Component` with:

  ```java
  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
      projectRepository.findAll().forEach(p -> defaultFolderSeeder.seedDefaultsIfMissing(p.getId()));
  }
  ```

  Spring Boot fires `ApplicationReadyEvent` *after* all `CommandLineRunner` beans (demo seeders) complete, so ordering is correct without explicit `@Order` annotations. Per-project failures are caught and logged at WARN; the loop continues.

### 2. Frontend — "New folder" button + modal

**File changes:**

- New: `frontend/src/components/document/NewFolderDialog.tsx` — modal component (open / close / submit props).
- Modified: `frontend/src/app/(app)/projects/[projectId]/documents/page.tsx` — add buttons + dialog state + mutation.

**Two entry points:**

1. **`+ New folder`** button in the **Folders** sidebar header (top of the left panel, next to the "Folders" label). Clicking opens the dialog with `parentId = null` → creates a root folder.
2. **`+`** icon button on each folder row (visible on hover). Opens the dialog with `parentId = <that folder's id>` → creates a sub-folder. The dialog title shows the parent's name for context ("New sub-folder under *Drawings*").

**Dialog fields:**

| Field    | Type                               | Required | Notes                                                 |
|----------|------------------------------------|----------|-------------------------------------------------------|
| Name     | text                               | yes      | Max 255 chars.                                        |
| Code     | text                               | yes      | Max 100 chars. Backend already requires it.           |
| Category | dropdown (DocumentCategory enum)   | no       | Defaults to parent's category for sub-folders, else GENERAL. |

**Submit behavior:**

- Calls `documentApi.createFolder(projectId, { parentId, name, code, category })` (already exists in `documentApi.ts:277`).
- On success: invalidate `['document-folders', 'root', projectId]` and (if parent) `['document-folders', 'children', parentId]` queries. Auto-select the new folder (`setSelectedFolderId(newFolder.id)`). Close dialog. Toast "Folder created".
- On error: keep dialog open, show inline error (name / code uniqueness validation comes from backend `ApiResponse.error`).

### 3. Tests

**Backend** (`backend/bipros-document/src/test/java/com/bipros/document/application/listener/DefaultFolderSeederTest.java`):

- `onProjectCreated_createsSevenRootFolders_whenNoneExist` — publish event, assert 7 rows in repo with the right `(name, code, category, sortOrder)` tuples.
- `onProjectCreated_isIdempotent_whenRootFoldersExist` — pre-seed one folder, publish event, assert count is still 1.
- `onProjectCreated_doesNotPropagate_whenSeederFails` — mock repo to throw, assert no exception bubbles up.
- `backfillExistingProjects_seedsEveryProjectThatHasNoRootFolders` — pre-create 2 projects, one with folders (idempotent skip) and one without (gets 7), invoke backfill, assert correct counts.

**Frontend** (`frontend/e2e/tests/12-documents.spec.ts` — new):

- Happy path: create a project → navigate to Documents tab → expect 7 folders visible → select "Drawings" → upload a file (use a small PDF fixture) → expect it in the documents table → click download → expect a non-zero blob.
- Custom folder: click "+ New folder" → fill name=`Site Photos`, code=`SP` → submit → expect new folder in tree → expect it auto-selected.
- Sub-folder: hover "Drawings" → click `+` → expect dialog title "New sub-folder under Drawings" → submit → expand "Drawings" → expect child folder visible.

## Data flow

### Project creation → folder seeding

```
User                  ProjectController            ProjectService           ApplicationEventPublisher    DefaultFolderSeeder    DocumentFolderRepository
 │  POST /projects      │                            │                           │                            │                       │
 ├──────────────────────►                            │                           │                           │                       │
 │                       │  createProject(req)       │                           │                           │                       │
 │                       ├──────────────────────────►│                           │                           │                       │
 │                       │                           │  save(project)            │                           │                       │
 │                       │                           ├──────────────►(JPA)       │                           │                       │
 │                       │                           │  publishEvent(ProjectCreatedEvent)                    │                       │
 │                       │                           ├───────────────────────────►                           │                       │
 │                       │                           │                           │  (transaction commits)    │                       │
 │                       │                           │                           ├───────────────────────────►                       │
 │                       │                           │                           │                           │ findByProjectIdAndParentIdIsNull
 │                       │                           │                           │                           ├──────────────────────►│
 │                       │                           │                           │                           │  (empty list)         │
 │                       │                           │                           │                           │ saveAll(7 folders)    │
 │                       │                           │                           │                           ├──────────────────────►│
 │  201 Created          │                           │                           │                           │                       │
 ◄───────────────────────┤                           │                           │                           │                       │
```

### Folder creation (manual, frontend)

```
User → "+ New folder" → <NewFolderDialog /> → documentApi.createFolder()
   → POST /v1/projects/{projectId}/document-folders
   → DocumentFolderController → DocumentFolderService → repo.save()
   → 201 → invalidate queries → select new folder → close dialog
```

## Files affected

**New:**

- `backend/bipros-common/src/main/java/com/bipros/common/event/ProjectCreatedEvent.java`
- `backend/bipros-document/src/main/java/com/bipros/document/application/listener/DefaultFolderSeeder.java`
- `backend/bipros-document/src/test/java/com/bipros/document/application/listener/DefaultFolderSeederTest.java`
- `backend/bipros-api/src/main/java/com/bipros/api/config/seeder/DefaultFolderStartupBackfill.java`
- `frontend/src/components/document/NewFolderDialog.tsx`
- `frontend/e2e/tests/12-documents.spec.ts`
- `frontend/e2e/fixtures/sample.pdf` (small PDF for upload test)

**Modified:**

- `backend/bipros-project/src/main/java/com/bipros/project/application/service/ProjectService.java` — inject `ApplicationEventPublisher`, publish `ProjectCreatedEvent` in `createProject(...)`.
- `backend/bipros-document/src/main/java/com/bipros/document/domain/repository/DocumentFolderRepository.java` — add `findByProjectIdAndParentIdIsNullOrderBySortOrder(UUID projectId)` derived query.
- `backend/bipros-document/src/main/java/com/bipros/document/application/service/DocumentFolderService.java` — `listRootFolders(...)` switches to the new `IsNull` query (bug fix).
- `frontend/src/app/(app)/projects/[projectId]/documents/page.tsx` — add `+ New folder` button (sidebar header), per-row `+` button, dialog state, success handler that invalidates queries and selects the new folder.

**Unchanged but referenced (sanity check before implementation):**

- `backend/bipros-document/src/main/java/com/bipros/document/domain/model/DocumentFolder.java` — fields are already `projectId`, `parentId`, `name`, `code`, `category`, `wbsNodeId`, `sortOrder`. No migration needed.
- `frontend/src/lib/api/documentApi.ts` — `createFolder()` and `listRootFolders()` already correct.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Listener fails silently → user sees empty Documents tab on a brand-new project | Manual "New folder" button is the fallback. WARN log surfaces the failure. |
| `bipros-common` adds a Spring dependency for events | `ApplicationEventPublisher` and the event record are pure POJO + Spring Context, which `bipros-common` already depends on (it's a Spring Boot app). No new transitive dependency. |
| Race: user opens Documents tab before the post-commit listener fires | The listener runs synchronously on the same thread by default (`@TransactionalEventListener` is sync unless `@Async`). The HTTP response to `POST /projects` only returns after both commit and listener finish. So by the time the frontend navigates to Documents, folders exist. |
| Idempotency check uses `findByProjectIdAndParentIdIsNullOrderBySortOrder` — what if a future feature creates a sub-folder before the seeder runs? | Not possible: project creation publishes the event in the same transaction. No other code path creates folders for a project that doesn't yet exist. Even if it did, sub-folders have a non-null `parentId` and would not satisfy this query. |
| Existing projects in dev DB have no default folders | Dev runs `ddl-auto: create-drop`, demo seeders recreate projects on every boot, listener fires for each → all dev projects pick up the 7 folders on next backend restart. |

## Acceptance criteria

1. Restart backend → log shows `DefaultFolderSeeder created 7 root folders for project <id>` for each demo project created by `DataSeeder` / `seed-icpms-data.sh`.
2. Hit `GET /v1/projects/{projectId}/document-folders/root` for any seeded project → returns 7 folders in the order Drawings, Specifications, Contracts, Approvals, Correspondence, As-Built, General.
3. Open Documents tab on a seeded project → 7 folders visible in the sidebar, no "No folders" message.
4. Select a folder → upload form is visible → upload a file → file appears in the documents table → click download → file downloads.
5. Click `+ New folder` → fill the form → submit → new folder appears in the tree, is auto-selected, dialog closes.
6. Hover a folder row → `+` button appears → click → dialog opens with title naming the parent → submit → sub-folder appears under parent when expanded.
7. `pnpm test:e2e -- 12-documents` passes.
8. `mvn test -pl bipros-document` passes the new `DefaultFolderSeederTest`.
