# Khasab Real-Data E2E Run — 2026-05-24

All artifacts from the fresh-environment + Khasab real-data import.

## Top-level deliverables

| File | Size | Purpose |
|---|---:|---|
| [`dpr-dbs-e2e-execution-log-2026-05-24.html`](dpr-dbs-e2e-execution-log-2026-05-24.html) | 30 KB | **Single consolidated HTML report** — phase summary, findings, deliverables list |
| [`dpr-dbs-e2e-test-execution-log-2026-05-24.md`](dpr-dbs-e2e-test-execution-log-2026-05-24.md) | 11 KB | Markdown execution log (pre-fix + post-review fixes) |
| [`khasab-dpr-2026-05-24.csv`](khasab-dpr-2026-05-24.csv) | 412 KB | **Flat DPR export** (3,431 rows, 12 columns) |
| [`khasab-dpr-2026-05-24.xlsx`](khasab-dpr-2026-05-24.xlsx) | 172 KB | DPR Excel (4 sheets: DPR / By-Supervisor / By-Activity / By-Date) |
| [`bipros-backup-2026-05-24.dump`](bipros-backup-2026-05-24.dump) → /tmp | 22 MB | Pre-wipe pg_dump (custom format) for rollback |
| [`2026-05-24-fresh-env-and-khasab-import-design.md`](2026-05-24-fresh-env-and-khasab-import-design.md) | 26 KB | Design spec (brainstorming output) |
| [`2026-05-24-fresh-env-and-khasab-import.md`](2026-05-24-fresh-env-and-khasab-import.md) | 49 KB | Implementation plan (20 tasks) |

## Intermediate artifacts

| File | Size | Purpose |
|---|---:|---|
| [`khasab-dpr-parsed.json`](khasab-dpr-parsed.json) | 1.5 MB | Normalized DPRs from Excel (3,431 records, +1y date-shift applied) |
| [`khasab-dpr-validation_report.md`](khasab-dpr-validation_report.md) | 3 KB | Parser validation report (skip reasons, unknown supervisors, activity codes) |
| [`ai-ground-truth.json`](ai-ground-truth.json) | 17 KB | 50 SQL-precomputed answers for AI validation |
| [`ai-results.json`](ai-results.json) | — | AI grading results (PASS/PARTIAL/FAIL) — pending grader completion |
| [`wipe-transactional.sql`](wipe-transactional.sql) | 18 KB | TRUNCATE script used for Phase 1 cleanup |

## Scripts (`scripts/`)

| Script | Purpose |
|---|---|
| [`parse_khasab.py`](scripts/parse_khasab.py) | Excel → JSON parser with +1y date shift |
| [`create_khasab_users.py`](scripts/create_khasab_users.py) | Create 16 users via `/v1/users` |
| [`create_project_team.py`](scripts/create_project_team.py) | Build PM → CM → ENGINEER → SUPERVISOR reports-to chain |
| [`create_wbs_and_activities.py`](scripts/create_wbs_and_activities.py) | Build 22-node WBS tree |
| [`create_activities.py`](scripts/create_activities.py) | Create 33 activities from Khasab activity codes |
| [`import_khasab_dprs.py`](scripts/import_khasab_dprs.py) | Bulk-import 3,431 DPRs in batches of 25 |
| [`fix_demo.py`](scripts/fix_demo.py) | **Post-review fix script** — unlock+update+relock activities, fix DPR rates, insert EVM, create BOQ/MCL/Norms |
| [`create_norms_only.py`](scripts/create_norms_only.py) | Productivity norms (66 = 33 manpower + 33 equipment) |
| [`ai_ground_truth.py`](scripts/ai_ground_truth.py) | Pre-compute 50 SQL ground-truth answers |
| [`ai_grade.py`](scripts/ai_grade.py) | Run AI through 50 questions + grade vs ground truth |
| [`build_html_report.py`](scripts/build_html_report.py) | Generate consolidated HTML report |
| [`export_dpr_csv_xlsx.py`](scripts/export_dpr_csv_xlsx.py) | Generate CSV + 4-sheet XLSX exports |

## Runtime state (`state/`)

ID registry produced during the run — useful for re-running specific steps.

| File | Purpose |
|---|---|
| [`project-id.txt`](state/project-id.txt) | KHASAB-2026 project UUID |
| [`users.json`](state/users.json) | 16 user spec (username, role, name) |
| [`user-ids.json`](state/user-ids.json) | username → UUID mapping |
| [`wbs-ids.json`](state/wbs-ids.json) | WBS code → UUID mapping (22 nodes) |
| [`activity-ids.json`](state/activity-ids.json) | Activity code → UUID mapping (33 activities) |

## Screenshots (`screenshots/`)

11 PNG captures from Playwright runs:

- **Pre-fix (Phase 2 + Phase 11):**
  - `2026-05-24-phase2-dashboard.png` — admin login + zero-state dashboard
  - `2026-05-24-phase2-projects-list.png` — projects list
  - `2026-05-24-phase11-dashboard.png` — executive dashboard
  - `2026-05-24-phase11-projects-list.png`
  - `2026-05-24-phase11-project-overview.png` — pre-fix project overview (showed 0%)
  - `2026-05-24-phase11-dpr-list.png` — DPR list page
  - `2026-05-24-phase11-wbs.png` — WBS tree (22 nodes)
  - `2026-05-24-phase11-dbs.png` — DBS supervisor tab

- **Post-fix:**
  - `2026-05-24-FIX-project-overview.png` — **shows 50% progress, ₹5 Cr budget, 3 of 33 completed**
  - `2026-05-24-FIX-activities.png` — shows durations, % complete, status, planned dates
  - `2026-05-24-FIX-capacity-utilization.png`

## Run statistics

| Metric | Value |
|---|---:|
| Wall-clock | ~2 hours (DPR import: 1.5h) |
| Users created | 16 (12 from spec + 4 from data) |
| WBS nodes | 22 |
| Activities | 33 (all locked, all linked to work_activity) |
| DPRs imported | **3,431** (Jan 591 + Feb 1,151 + Mar 1,689) |
| Manpower lines | 995 |
| Equipment lines | 1,500 |
| BOQ items | 20 |
| Material Consumption Logs | 40 |
| Productivity Norms | 66 (33 manpower + 33 equipment) |
| Total project cost | **₹1.26 Crore** (was ₹23K before rate fix) |
| Avg activity % complete | 67.7% |
| Activities marked COMPLETED | 3 |
| EVM BAC | ₹5 Crore |
| Findings (open) | 22 (4 carryover + 18 new) |
| AI grading | Running — partial: 11 PASS / 7 FAIL / 32 pending |

## To restore the pre-wipe state

```bash
PGPASSWORD=bipros_dev /Applications/Postgres.app/Contents/Versions/latest/bin/pg_restore \
  -h 127.0.0.1 -U bipros -d bipros --clean --if-exists \
  /tmp/bipros-backup-2026-05-24.dump
```

## To re-run the full setup

```bash
# 1. Cleanup (DESTRUCTIVE — backs up first)
PGPASSWORD=bipros_dev pg_dump -h 127.0.0.1 -U bipros -F c -f /tmp/bipros-rerun-backup.dump bipros
PGPASSWORD=bipros_dev psql -h 127.0.0.1 -U bipros -d bipros -f scripts/wipe-transactional.sql

# 2. Restart backend, then login as admin to get token
TOKEN=$(curl -sS -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')
echo "$TOKEN" > /tmp/admin-token.txt

# 3. Run each script in order
mkdir -p /tmp/khasab
python3 scripts/parse_khasab.py
python3 scripts/create_khasab_users.py
# (manually create project — see scripts/fix_demo.py for shape)
python3 scripts/create_project_team.py
python3 scripts/create_wbs_and_activities.py
python3 scripts/create_activities.py
python3 scripts/import_khasab_dprs.py all
python3 scripts/fix_demo.py
python3 scripts/create_norms_only.py
python3 scripts/ai_ground_truth.py
python3 scripts/ai_grade.py
python3 scripts/export_dpr_csv_xlsx.py
python3 scripts/build_html_report.py
```

## Source data

- Excel: `docs/ActualData/1. Daily Data-Khasab  Jan, Feb, Mar 2026.xlsx` (note double-space)
- Master sheet: `docs/ActualData/3. Supervisor-Engineer-CM-PM DBS (2).xlsx`
- Dates in source are 2025-01-24 → 2025-03-29; shifted +1 year to 2026 at parse time.
