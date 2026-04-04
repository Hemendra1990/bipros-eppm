# Bipros EPPM - End-to-End Testing Guide

## Overview

This guide covers setting up and running Playwright E2E tests for the Bipros EPPM application (Oracle P6 clone). The tests validate critical user flows across the full stack: PostgreSQL + Spring Boot backend + Next.js frontend.

---

## Prerequisites

| Component        | Version   | Purpose                    |
|------------------|-----------|----------------------------|
| Docker Desktop   | Latest    | PostgreSQL, Redis, PgAdmin |
| Java             | 23+       | Spring Boot backend        |
| Node.js          | 20+       | Next.js frontend           |
| pnpm             | 10+       | Package manager            |
| Maven            | 3.9+      | Backend build              |

---

## 1. Environment Setup

### 1.1 Start Infrastructure Services

```bash
cd /Volumes/Java/Projects/bipros-eppm
docker-compose up -d
```

This starts:
- **PostgreSQL** on `localhost:5432` (db: `bipros`, user: `bipros`, password: `bipros_dev`)
- **Redis** on `localhost:6379`
- **PgAdmin** on `localhost:5050`

### 1.2 Build and Start Backend

```bash
cd backend
mvn clean package -DskipTests
mvn spring-boot:run -pl bipros-api
```

Backend starts on **http://localhost:8080**. The `DataSeeder` automatically seeds:
- 5 roles: ADMIN, PROJECT_MANAGER, SCHEDULER, RESOURCE_MANAGER, VIEWER
- Admin user: `admin` / `admin123`
- Standard calendar (Mon-Fri, 8 hrs/day)
- USD currency + global settings

### 1.3 Start Frontend

```bash
cd frontend
pnpm install
pnpm dev
```

Frontend starts on **http://localhost:3000**

### 1.4 Install Playwright

```bash
cd frontend
pnpm add -D @playwright/test
npx playwright install --with-deps chromium
```

---

## 2. Playwright Configuration

File: `frontend/playwright.config.ts`

```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,        // Sequential for data-dependent tests
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,                  // Single worker to preserve test order
  reporter: [
    ['html', { open: 'never' }],
    ['list'],
  ],
  timeout: 30_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL: process.env.FRONTEND_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: [
    {
      command: 'pnpm dev',
      url: 'http://localhost:3000',
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
  ],
});
```

---

## 3. Test Structure

```
frontend/e2e/
  fixtures/
    auth.fixture.ts        # Shared login helper
  tests/
    01-auth.spec.ts        # Login/register flows
    02-eps.spec.ts         # EPS hierarchy CRUD
    03-project.spec.ts     # Project lifecycle
    04-wbs.spec.ts         # WBS management
    05-activity.spec.ts    # Activity CRUD + relationships
    06-schedule.spec.ts    # CPM scheduling
    07-gantt.spec.ts       # Gantt chart rendering
    08-resource.spec.ts    # Resource management
    09-baseline.spec.ts    # Baseline creation + comparison
    10-calendar.spec.ts    # Calendar management
    11-reports.spec.ts     # Report generation
    12-import-export.spec.ts # File export/import
    13-portfolio.spec.ts   # Portfolio management
    14-evm.spec.ts         # Earned Value Management
    15-admin.spec.ts       # Admin settings
```

---

## 4. Test Scenarios by Feature

### 4.1 Authentication (`01-auth.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | Login with valid credentials        | Navigate to `/auth/login` > Enter `admin` / `admin123` > Click "Sign In"               | Redirected to dashboard `/`  |
| 2 | Login with invalid credentials      | Enter wrong password > Click "Sign In"                                                 | Error toast displayed        |
| 3 | Redirect unauthenticated user       | Navigate to `/projects` without login                                                  | Redirected to `/auth/login`  |
| 4 | Register new user                   | Navigate to register > Fill form > Submit                                              | Account created, logged in   |
| 5 | Logout                              | Click user menu > Logout                                                               | Redirected to login          |

### 4.2 EPS Hierarchy (`02-eps.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | View EPS tree                       | Login > Navigate to `/eps`                                                             | EPS tree displayed           |
| 2 | Create root EPS node                | Click "Add Node" > Enter code `INFRA` + name `Infrastructure` > Save                  | Node appears in tree         |
| 3 | Create child EPS node               | Select parent > Add child > Enter code `ROADS` + name `Road Projects` > Save          | Child appears under parent   |
| 4 | Edit EPS node name                  | Hover node > Click edit > Change name > Save                                           | Name updated                 |
| 5 | Delete EPS node                     | Hover node > Click delete > Confirm                                                   | Node removed from tree       |

### 4.3 Project Lifecycle (`03-project.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | Create new project                  | Navigate to `/projects` > Click "New Project" > Fill form (code, name, dates, EPS) > Save | Project created, listed      |
| 2 | View project detail                 | Click project name in list                                                             | Project detail page with tabs |
| 3 | Update project details              | Edit name/dates > Save                                                                 | Changes persisted            |
| 4 | View project overview tab           | Open project > Overview tab                                                            | Summary stats displayed      |
| 5 | Delete project (no activities)      | Delete project > Confirm                                                               | Project removed              |
| 6 | Delete project (with activities)    | Try deleting project with activities                                                   | Error: "Cannot delete..."    |

### 4.4 WBS Management (`04-wbs.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | View WBS tree                       | Open project > WBS tab                                                                 | WBS tree displayed           |
| 2 | Create WBS node                     | Click "Add WBS" > Enter code + name > Save                                            | Node appears in tree         |
| 3 | Create child WBS node               | Select parent WBS > Add child                                                         | Child node created           |
| 4 | Delete WBS node                     | Delete WBS with no activities                                                          | Node removed                 |
| 5 | Delete WBS (with activities)        | Delete WBS that has activities                                                         | Error: "Cannot delete..."    |

### 4.5 Activity Management (`05-activity.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | View activity list                  | Open project > Activities tab                                                          | Activity grid displayed      |
| 2 | Create task dependent activity      | Click "New Activity" > Type: Task Dependent > Enter code, name, duration > Save        | Activity created             |
| 3 | Create milestone                    | New Activity > Type: Start Milestone > Save                                            | Milestone created (0 dur)    |
| 4 | Add FS relationship                 | Select predecessor + successor > Type: FS > Lag: 0 > Save                             | Relationship created         |
| 5 | Add SS relationship with lag        | Add SS link with lag 2d                                                                | Relationship with lag saved  |
| 6 | Edit activity                       | Click activity > Edit name/duration > Save                                             | Changes persisted            |
| 7 | Delete activity                     | Delete activity > Confirm                                                              | Activity removed             |
| 8 | Update percent complete             | Edit activity > Set % complete to 50                                                   | Progress updated             |

### 4.6 CPM Scheduling (`06-schedule.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | Run CPM schedule                    | Open project > Activities tab > Click "Run Schedule"                                   | Schedule calculated          |
| 2 | Verify early dates                  | Check ES/EF on activities after scheduling                                             | Dates populated correctly    |
| 3 | Verify late dates                   | Check LS/LF on activities                                                              | Late dates calculated        |
| 4 | Verify critical path                | Check isCritical flag on activities                                                    | Critical activities flagged  |
| 5 | Verify float                        | Check totalFloat/freeFloat values                                                      | Float values correct         |

### 4.7 Gantt Chart (`07-gantt.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | View Gantt chart                    | Open project > Gantt tab                                                               | Gantt chart rendered         |
| 2 | Verify task bars                    | Check activity bars are rendered                                                       | Bars visible with correct width |
| 3 | Critical path highlighting          | Verify critical activities shown in red                                                | Red bars for critical path   |
| 4 | Relationship lines                  | Verify FS/SS/FF/SF arrows displayed                                                    | Lines connecting activities  |
| 5 | Zoom in/out                         | Use zoom slider                                                                        | Timeline scale changes       |
| 6 | Today line                          | Verify today line displayed                                                            | Vertical line at current date |

### 4.8 Resource Management (`08-resource.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | Create labor resource               | Navigate to `/resources` > New > Type: Labor > Fill details > Save                    | Resource created             |
| 2 | Create non-labor resource           | New > Type: NonLabor > Fill details > Save                                             | Resource created             |
| 3 | Create material resource            | New > Type: Material > Fill details > Save                                             | Resource created             |
| 4 | Assign resource to activity         | Open project > Resources tab > Assign resource > Select activity + resource            | Assignment created           |
| 5 | View resource assignments           | Check Resources tab                                                                    | Assignments listed           |
| 6 | Edit resource                       | Edit resource details > Save                                                           | Changes persisted            |

### 4.9 Baseline Management (`09-baseline.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | Create baseline                     | Open project > Baselines tab > "Create Baseline" > Enter name > Save                  | Baseline created             |
| 2 | View baselines list                 | Check Baselines tab                                                                    | Baselines listed with dates  |
| 3 | Schedule comparison                 | Click "Compare" on a baseline                                                          | Variance table displayed     |
| 4 | Verify variance colors              | Check start/finish variance coloring                                                   | Red=delayed, Green=improved  |
| 5 | Delete baseline                     | Delete baseline > Confirm                                                              | Baseline removed             |

### 4.10 Calendar Management (`10-calendar.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | View calendars                      | Navigate to `/admin/calendars`                                                         | Calendar list displayed      |
| 2 | Create calendar                     | Click "New Calendar" > Fill form > Save                                                | Calendar created             |
| 3 | Edit work week                      | Edit working hours for a day                                                           | Changes saved                |
| 4 | Add holiday exception               | Add non-working exception date                                                         | Exception saved              |
| 5 | Delete calendar (not in use)        | Delete calendar with no activities                                                     | Calendar removed             |
| 6 | Delete calendar (in use)            | Delete calendar used by activities                                                     | Error: "Cannot delete..."    |

### 4.11 Reports (`11-reports.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | View reports page                   | Navigate to `/reports`                                                                 | Report page with project selector |
| 2 | Generate S-Curve                    | Select project > View S-Curve chart                                                    | PV/EV/AC lines rendered      |
| 3 | Generate Resource Histogram         | Select project > View histogram                                                        | Bar chart rendered           |
| 4 | Generate Cash Flow                  | Select project > View cash flow                                                        | Cash flow chart rendered     |
| 5 | Export report (Excel)               | Click "Download Excel"                                                                 | .xlsx file downloaded        |

### 4.12 Import/Export (`12-import-export.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | Export P6 XML                       | Open project > Export > P6 XML                                                         | XML file downloaded          |
| 2 | Export MSP XML                      | Open project > Export > MSP XML                                                        | XML file downloaded          |
| 3 | Export Excel                        | Open project > Export > Excel                                                          | .xlsx with 6 sheets          |
| 4 | Export CSV                          | Open project > Export > CSV                                                            | CSV file downloaded          |
| 5 | Import XER file                     | Upload .xer file                                                                       | Project created from XER     |

### 4.13 Portfolio Management (`13-portfolio.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | Create portfolio                    | Navigate to `/portfolios` > Create > Fill name + description > Save                   | Portfolio created            |
| 2 | Add project to portfolio            | Open portfolio > Add project                                                           | Project linked               |
| 3 | Remove project from portfolio       | Remove project > Confirm                                                               | Project removed              |
| 4 | View portfolio detail               | Click portfolio name                                                                   | Detail page with project list |

### 4.14 Earned Value Management (`14-evm.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | View EVM tab                        | Open project > EVM tab                                                                 | EVM metrics displayed        |
| 2 | Calculate EVM                       | Click "Calculate EVM"                                                                  | PV/EV/AC/SPI/CPI calculated |
| 3 | Verify S-Curve                      | Check EVM S-Curve chart                                                                | PV vs EV vs AC over time     |
| 4 | Verify performance indices          | Check SPI, CPI, TCPI values                                                           | Non-zero valid values        |

### 4.15 Admin Settings (`15-admin.spec.ts`)

| # | Test Case                           | Steps                                                                                  | Expected Result              |
|---|-------------------------------------|----------------------------------------------------------------------------------------|------------------------------|
| 1 | View admin settings                 | Navigate to `/admin/settings`                                                          | Settings page displayed      |
| 2 | Update scheduling option            | Change scheduling option > Save                                                        | Setting persisted            |

---

## 5. Running Tests

### Run all tests

```bash
cd frontend
npx playwright test
```

### Run specific test file

```bash
npx playwright test e2e/tests/01-auth.spec.ts
```

### Run with UI mode (interactive)

```bash
npx playwright test --ui
```

### Run headed (see browser)

```bash
npx playwright test --headed
```

### Run specific test by name

```bash
npx playwright test -g "Login with valid credentials"
```

### View HTML report

```bash
npx playwright show-report
```

### Debug a failing test

```bash
npx playwright test --debug e2e/tests/03-project.spec.ts
```

---

## 6. Test Data Strategy

### Seed Data (automatic on backend start)

The `DataSeeder` creates baseline data on first run:

| Entity          | Data                                                          |
|-----------------|---------------------------------------------------------------|
| Roles           | ADMIN, PROJECT_MANAGER, SCHEDULER, RESOURCE_MANAGER, VIEWER  |
| Admin User      | username: `admin`, password: `admin123`                       |
| Calendar        | Standard (Mon-Fri, 8h/day)                                   |
| Currency        | USD                                                           |
| Global Settings | Retained Logic scheduling, Activity % Complete EVM            |

### Test Data Creation Order

Tests must run sequentially because they build on each other:

```
Login (admin)
  -> Create EPS node
    -> Create Project (under EPS)
      -> Create WBS nodes
        -> Create Activities (under WBS)
          -> Add Relationships
            -> Run Schedule (CPM)
              -> Create Baseline
              -> Assign Resources
              -> Calculate EVM
              -> Export/Import
```

### Cleanup Strategy

- **Option A (recommended)**: Tests create data with unique prefixes (`E2E-xxx`) and clean up in `afterAll`
- **Option B**: Fresh database per run via `docker-compose down -v && docker-compose up -d`

---

## 7. CI/CD Integration

### GitHub Actions example

```yaml
name: E2E Tests

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  e2e:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    services:
      postgres:
        image: postgres:17
        env:
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
          POSTGRES_DB: bipros
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 23
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 23

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: 20

      - name: Install pnpm
        run: npm install -g pnpm

      - name: Init database schemas
        run: psql -h localhost -U postgres -d bipros -f docker/init-schemas.sql
        env:
          PGPASSWORD: postgres

      - name: Build backend
        run: cd backend && mvn clean package -DskipTests -q

      - name: Start backend
        run: |
          cd backend
          mvn spring-boot:run -pl bipros-api &
          sleep 15
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/bipros
          SPRING_DATASOURCE_USERNAME: bipros
          SPRING_DATASOURCE_PASSWORD: bipros_dev

      - name: Install frontend dependencies
        run: cd frontend && pnpm install

      - name: Install Playwright
        run: cd frontend && npx playwright install --with-deps chromium

      - name: Run E2E tests
        run: cd frontend && npx playwright test
        env:
          FRONTEND_URL: http://localhost:3000
          CI: true

      - name: Upload test artifacts
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-report
          path: frontend/playwright-report/
          retention-days: 7
```

---

## 8. Troubleshooting

| Issue                             | Solution                                                          |
|-----------------------------------|-------------------------------------------------------------------|
| Backend not starting              | Check Docker is running: `docker ps`                              |
| Database connection refused       | Verify PostgreSQL: `docker-compose up -d postgresql`              |
| Login fails in tests              | Ensure DataSeeder ran (check console for "Seeded admin user")     |
| Timeout on page navigation        | Increase `timeout` in playwright.config.ts                        |
| Port already in use               | Kill existing process: `lsof -ti:8080 \| xargs kill`             |
| Stale test data                   | Reset DB: `docker-compose down -v && docker-compose up -d`       |
| Flaky tests                       | Add `retries: 2` in config, use `waitForResponse` for API calls  |
| Screenshots not captured          | Check `screenshot: 'only-on-failure'` in config                   |

---

## 9. Quick Start Checklist

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Build + start backend
cd backend && mvn clean package -DskipTests && mvn spring-boot:run -pl bipros-api &

# 3. Wait for backend (check http://localhost:8080/v1/auth/login returns 405)
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/v1/auth/login

# 4. Install + start frontend
cd frontend && pnpm install && pnpm dev &

# 5. Install Playwright
cd frontend && pnpm add -D @playwright/test && npx playwright install --with-deps chromium

# 6. Run tests
cd frontend && npx playwright test

# 7. View report
cd frontend && npx playwright show-report
```
