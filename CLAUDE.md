# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Start here

`AGENTS.md` at the repo root is the canonical reference for build commands, test commands, Docker, and the per-language coding conventions (Java entity/DTO/service/controller patterns, TypeScript/Next.js patterns, ESLint). **Read it first.** This file only adds the cross-cutting architecture and project-specific gotchas that aren't in AGENTS.md.

The frontend has its own `frontend/AGENTS.md` and `frontend/CLAUDE.md` — both important; see the Next.js 16 note below.

## Big-picture architecture

Full-stack monorepo. Two deployables, one database:

- **`backend/`** — Maven multi-module Spring Boot 3.5 / Java 23 app. 21 modules, one module per DDD bounded context plus `bipros-common` (shared utilities, `BaseEntity`, `ApiResponse`, exceptions, `GlobalExceptionHandler`) and `bipros-api` (the aggregator — depends on every domain module, owns `BiprosApplication.java`, `application.yml`, seeders). All domain modules produce Spring beans via component scan; they only run when wired in through `bipros-api`.
- **`frontend/`** — Next.js 16 / React 19 app using App Router, pnpm. Authenticated pages live under `src/app/(app)/...`; unauthenticated auth flows under `src/app/auth/`. API calls go through `src/lib/api/*Api.ts` (one file per backend domain), all sharing the axios client in `src/lib/api/client.ts` which attaches JWT + auto-refresh.
- **`docker/init-schemas.sql`** — creates a `bipros` DB user and one PostgreSQL **schema per bounded context** (`project`, `activity`, `scheduling`, `resource`, `cost`, `evm`, `baseline`, `udf`, `risk`, `portfolio`, `contract`, `document`). Each backend module maps its entities to its own schema via `@Table(schema = "...")`. When adding a new domain module, add its schema here too.

### Key consequences of this structure

- **Dev schema is rebuilt every boot.** `application.yml` sets `ddl-auto: create-drop` in the default profile and Liquibase is disabled. This means (a) schema changes take effect by restarting the backend, (b) any test data must come from seeders or the `scripts/seed-*.sh` scripts — nothing persists. Production profile (`prod`) flips to `ddl-auto: validate` and enables Liquibase; migrations under `backend/bipros-api/src/main/resources/db/changelog/` are the source of truth there.
- **Seeders live in `bipros-api/src/main/java/com/bipros/api/config/seeder/`** (ICPMS demo data) and run at boot via Spring. The `scripts/seed-*.sh` scripts are HTTP-based seeders that hit the running API — use them when you need to reset demo data without a backend restart.
- **Cross-module dependencies flow inward through `bipros-common`.** Domain modules should not depend on each other; if they need to, extend `bipros-common` or coordinate via `bipros-api`. Keep each module's `api/` → `application/` → `domain/` → `infrastructure/` layering intact.
- **Every response is wrapped in `ApiResponse<T>`** (from `bipros-common`). The frontend `ApiResponse<T>` / `PagedResponse<T>` types in `src/lib/types/index.ts` mirror this — keep them in sync when changing the envelope shape.

## Commands not in AGENTS.md

### Running the full stack locally
```bash
docker compose up -d                        # Postgres + pgAdmin + Redis
(cd backend && mvn spring-boot:run -pl bipros-api)   # starts on :8080
(cd frontend && pnpm dev)                   # starts on :3000
```
Backend seeds an admin user on first boot: `admin` / `admin123`.

### Seeding demo data (backend must be running)
```bash
./scripts/seed-demo-data.sh        # Generic construction/engineering demo
./scripts/seed-icpms-data.sh       # ICPMS-specific dataset
./scripts/seed-post-data.sh        # Post-boot seed extensions
./scripts/restore-seed-data.sh     # Reset to a known state
./scripts/e2e-test.sh              # Curl-based end-to-end API walkthrough
```

### Frontend e2e (Playwright)
```bash
cd frontend
pnpm test:e2e             # headless
pnpm test:e2e:ui          # Playwright UI
pnpm test:e2e:headed      # headed browser
pnpm test:e2e:report      # view last report
```
`playwright.config.ts` auto-starts `pnpm dev` if it's not already running; backend must be started separately.

## Next.js 16 warning

`frontend/AGENTS.md` is blunt about this and it's worth repeating: **this is Next.js 16, not the Next.js in your training data.** APIs, conventions, and file layout may differ. Before writing frontend code that touches Next.js specifics (routing, server components, caching, `next/*` imports), read the relevant guide in `frontend/node_modules/next/dist/docs/`. Heed deprecation notices.

## Swagger / API discovery

Backend running → `http://localhost:8080/swagger-ui.html` for the live OpenAPI spec. Faster than grepping controllers when you need to find an endpoint.

## graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep — these traverse the graph's EXTRACTED + INFERRED edges instead of scanning files
- After modifying code files in this session, run `graphify update .` to keep the graph current (AST-only, no API cost)
