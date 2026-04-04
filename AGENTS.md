# Bipros EPPM — Agent Guide

Enterprise Project Portfolio Management system. Full-stack monorepo: Java 23 / Spring Boot 3.5.0 backend (Maven multi-module) + TypeScript / Next.js 16 / React 19 frontend (pnpm). PostgreSQL 17 + Redis 7 via Docker.

## Build & Run Commands

### Prerequisites
```bash
docker compose up -d          # PostgreSQL 17, pgAdmin, Redis 7
```

### Backend (from `backend/`)
```bash
mvn clean install             # Full build all 17 modules
mvn compile                   # Compile only
mvn package                   # Build Spring Boot JAR
mvn spring-boot:run           # Run from bipros-api module
```

### Frontend (from `frontend/`)
```bash
pnpm install                  # Install dependencies
pnpm dev                      # Dev server (http://localhost:3000)
pnpm build                    # Production build
pnpm lint                     # ESLint (flat config, next/core-web-vitals + typescript)
```

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
pnpm vitest run                          # All tests
pnpm vitest run --reporter=verbose       # Verbose output
pnpm vitest run src/lib/api/authApi.test.ts  # Single test file
```
- No test files exist yet; vitest 4.1.2 and @testing-library/react are configured in devDependencies
- Tests use jsdom environment, @testing-library/jest-dom matchers

## Code Style — Backend (Java)

### Architecture
- Domain-Driven Design, schema-per-bounded-context in PostgreSQL
- Each module: `api/` (controllers) → `application/service/` → `application/dto/` → `domain/model/` + `domain/repository/` → `infrastructure/`
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
- Schemas: `project`, `activity`, `scheduling`, `resource`, `cost`, `evm`, `baseline`, `udf`, `risk`, `portfolio`

### Key Libraries
- Lombok (boilerplate), MapStruct (DTO mapping with `lombok-mapstruct-binding`), JJWT (auth)
- SpringDoc OpenAPI (Swagger at `/swagger-ui.html`)
- Liquibase (migrations, currently disabled in dev profile)

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

## Docker
```bash
docker compose up -d        # Start PostgreSQL, pgAdmin, Redis
docker compose down          # Stop all
```
- PostgreSQL: port 5432, database `bipros`, schemas initialized via `docker/init-schemas.sql`
- pgAdmin: http://localhost:5050
- Redis: port 6379
