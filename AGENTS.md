# Bipros EPPM — Agent Guide

Enterprise Project Portfolio Management system. Full-stack monorepo with three sub-projects:
- **Backend** — Java 23 / Spring Boot 3.5.0, Maven multi-module (20 modules including `bipros-api` aggregator)
- **Frontend** — TypeScript / Next.js 16.2.2 / React 19.2.4 / Tailwind CSS 4 (pnpm)
- **User Guide** — Docusaurus 3.9.2 (yarn, in `user-guide/`)

PostgreSQL 17 + Redis 7 via Docker Compose.

---

## Build & Run Commands

### Prerequisites
```bash
docker compose up -d          # PostgreSQL 17, pgAdmin, Redis 7
```

### Backend (from `backend/`)
```bash
mvn clean install             # Full build all modules
mvn -pl bipros-api spring-boot:run   # Run API server (port 8080)
```
- Swagger UI: http://localhost:8080/swagger-ui.html
- Dev profile (`application.yml`) uses `ddl-auto: create-drop` and **Liquibase is disabled**; schema is rebuilt on every boot.
- Integration tests use Testcontainers (`jdbc:tc:postgresql:17-alpine:///`) and **Liquibase is enabled** in `application-test.yml`.

### Frontend (from `frontend/`)
```bash
pnpm install                  # Install dependencies (prefer pnpm; both package-lock.json and pnpm-lock.yaml exist)
pnpm dev                      # Dev server (http://localhost:3000)
pnpm build                    # Production build
pnpm lint                     # ESLint 9 flat config
```
- API base URL: `NEXT_PUBLIC_API_URL=http://localhost:8080` (see `.env.example`)
- **Tailwind CSS v4** uses `@tailwindcss/postcss`; there is no `tailwind.config` file.

### User Guide (from `user-guide/`)
```bash
yarn install
yarn start                    # Dev server
yarn build                    # Static build into `build/`
```

---

## Testing

### Backend — JUnit 5 + Mockito + Testcontainers
```bash
mvn test                                          # All tests
mvn test -Dtest=EpsServiceTest                    # Single test class
mvn test -Dtest=EpsServiceTest#createNodeWithUniqueCodeSucceeds  # Single method
mvn test -Dtest=ProjectApiIntegrationTest         # Integration test (Testcontainers PG)
```
- Unit tests: `@ExtendWith(MockitoExtension.class)`, `@Nested` inner classes, `@DisplayName`
- Integration tests: `@ActiveProfiles("test")`, Testcontainers auto-provision PostgreSQL 17
- Test config: `backend/bipros-api/src/test/resources/application-test.yml`

### Frontend — Vitest + Testing Library
```bash
pnpm vitest run                          # All unit tests (none exist yet)
pnpm vitest run --reporter=verbose       # Verbose output
```
- Tests use jsdom environment, `@testing-library/jest-dom` matchers.

### Frontend — Playwright E2E
```bash
pnpm test:e2e                    # Run headless E2E tests
pnpm test:e2e:ui                 # Run with UI mode
pnpm test:e2e:headed             # Run headed
```
- Test directory: `frontend/e2e/`
- Config: `playwright.config.ts` — single Chromium project, `workers: 1`, `fullyParallel: false`
- Auto-starts `pnpm dev` via `webServer`; set `FRONTEND_URL` to override base URL.

---

## Code Style — Backend (Java)

### Architecture
- Domain-Driven Design, **schema-per-bounded-context** in PostgreSQL
- Each module: `api/` (controllers) → `application/service/` + `application/dto/` → `domain/model/` + `domain/repository/` → `infrastructure/`
- Package naming: `com.bipros.<module>.<layer>`

### Entities
- Extend `BaseEntity` — UUID PK (`@GeneratedValue(strategy = GenerationType.UUID)`), audit fields (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`), optimistic locking via `@Version`
- Use Lombok: `@Data` or `@Getter/@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`

### DTOs
- Java `record` (immutable) with Jakarta Bean Validation: `@NotBlank`, `@NotNull`, `@Size`

### Services
- `@Service @Transactional @RequiredArgsConstructor @Slf4j`
- Constructor injection via Lombok (no field injection)
- SLF4J logging: `log.info("Creating EPS node with code: {}", request.code())`

### Controllers
- `@RestController`, versioned paths (`/v1/projects`), standard CRUD
- Wrap responses in `ApiResponse<T>` envelope
- Return `ResponseEntity<>` with appropriate HTTP status codes

### Exceptions
- Use custom exceptions: `ResourceNotFoundException`, `BusinessRuleException` with rule codes
- Example: `throw new BusinessRuleException("EPS_CODE_DUPLICATE", "message")`
- Global handler: `GlobalExceptionHandler` with `@RestControllerAdvice`

### Imports
- Explicit imports only — no wildcard imports
- Order: jakarta → org → java → com.bipros

### Naming
- PascalCase classes, camelCase methods/fields
- DB columns: `snake_case` via `@Column(name = "created_at")`
- Schemas: `project`, `activity`, `scheduling`, `resource`, `cost`, `evm`, `baseline`, `udf`, `risk`, `portfolio`, `contract`, `document`

### Key Libraries
- Lombok (boilerplate), MapStruct (DTO mapping with `lombok-mapstruct-binding`), JJWT (auth)
- SpringDoc OpenAPI (Swagger at `/swagger-ui.html`)
- Liquibase (migrations, currently disabled in dev profile)

---

## Code Style — Frontend (TypeScript/React)

### Architecture
- Next.js App Router with route groups `(app)/` for authenticated pages
- Path alias: `@/*` → `./src/*`

### Components
- Functional components, `"use client"` directive where needed
- File naming: PascalCase for components (`DataTable.tsx`), camelCase for utilities (`projectApi.ts`)
- Tailwind CSS 4 utility-first styling with `tailwind-merge` for class merging

### State & Data
- Zustand stores with `persist` middleware for auth/app state
- TanStack React Query for server state; Axios with JWT auto-refresh interceptor
- React Hook Form + Zod for form validation

### Types
- Shared types in `src/lib/types/index.ts`
- `strict: true` in tsconfig — use proper TypeScript types, avoid `any`
- API response types use generic `ApiResponse<T>` and `PagedResponse<T>` wrappers

### Imports
- Use `@/` path alias for project imports (never relative `../../../`)
- Group: React/Next → third-party → `@/lib` → `@/components` → relative

### Linting
- ESLint 9 flat config: `eslint-config-next/core-web-vitals` + `eslint-config-next/typescript`
- Run `pnpm lint` before committing frontend changes

### ⚠️ Next.js 16 Breaking Changes
Next.js 16 has significant breaking changes compared to earlier versions. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.

---

## Docker
```bash
docker compose up -d        # Start PostgreSQL, pgAdmin, Redis
docker compose down          # Stop all
```
- PostgreSQL: port 5432, database `bipros`, schemas initialized via `docker/init-schemas.sql`
- pgAdmin: http://localhost:5050
- Redis: port 6379

---

## Operational Gotchas
- **Dev profile rebuilds schema on every boot** (`ddl-auto: create-drop`); seeders are authoritative. Liquibase is disabled in dev.
- **Document storage** uses local filesystem at `./storage/documents` in dev (configurable via `bipros.document.storage.path`).
- **Error detail inclusion** (`bipros.errors.include-detail`) is `true` in dev (shows exception class + message in 500 responses) and `false` in prod.
- **No CI/CD pipelines** are configured in this repo (no `.github/workflows/`).
