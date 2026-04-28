# Oman Seed Scripts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the partial `scripts/seed-oman-equipment.sh` (currently 26 norms) with a complete reproduction of the Oman workbook's "Plant utilization" and "Manpower utilization" sheets — all ResourceTypeDefs, all WorkActivities, and all per-(type × activity) productivity norms — using only existing API endpoints. Adds a Python helper that extracts rows directly from the source `.xlsx` so the data stays in sync with the spreadsheet.

**Architecture:** A small Python helper (using `openpyxl`, already on the project's interpreter) reads the workbook and emits CSV. Two bash scripts iterate the CSV and POST to `/v1/resource-types`, `/v1/work-activities`, and `/v1/productivity-norms`. Idempotent: existing rows are skipped by code/name match. No backend code changes — pure scripts on top of existing endpoints.

**Tech Stack:** Bash 3+, curl, jq, Python 3 + openpyxl. Target API: existing endpoints in `bipros-resource`.

**Spec:** `docs/superpowers/specs/2026-04-27-capacity-utilization-reports-design.md` (section 7).

**Out of scope (covered by separate plans):** Capacity Utilization extension (Plan 1); Daily-Deployment matrix (Plan 2); DPR matrix (Plan 3); boot-time `OmanRoadProjectSeeder.java` (explicitly excluded by spec § 7).

**Depends on:** none — uses existing endpoints only. Can be implemented in parallel with Plans 1–3.

**Source workbook:** `/Volumes/Learning/road_project_test/oman/2. Capacity_Utilization.xlsx`. The script must accept a `--workbook <path>` argument so it can be re-run when the workbook is updated.

---

## File-touch summary

**Created:**
- `scripts/lib/oman_workbook_to_csv.py` — Python helper, reads the xlsx, writes two CSVs
- `scripts/seed-oman-plant-norms.sh` — replaces `scripts/seed-oman-equipment.sh`
- `scripts/seed-oman-manpower-norms.sh` — new

**Removed:**
- `scripts/seed-oman-equipment.sh` — replaced by the two new scripts

**Modified:**
- None. No backend code or schema changes.

---

## Task 1: Python helper to extract workbook rows

**Files:**
- Create: `scripts/lib/oman_workbook_to_csv.py`

The Excel "Plant utilization" sheet is structured as: rows where column A (S.No.) contains an integer mark a new equipment category (column B = equipment name); subsequent rows where A is blank but B is non-blank are activities for that equipment with column C = unit (e.g. `Sqm/Day`), column D = output per day, column E = the natural unit (e.g. `Sqm`, `Cum`). The "Manpower utilization" sheet has the same structure but for manpower categories (Mason, Carpenter, Steel Fixer, Helper).

- [ ] **Step 1: Write the helper**

```python
#!/usr/bin/env python3
"""Extract Plant + Manpower utilization rows from the Oman capacity workbook to CSV.

Usage:
  python3 scripts/lib/oman_workbook_to_csv.py --workbook PATH --out-dir DIR

Emits:
  DIR/oman_plant_norms.csv   columns: equipment_type_code, equipment_type_name, activity_name, unit, output_per_day
  DIR/oman_manpower_norms.csv columns: category_code, category_name, activity_name, unit, output_per_man_per_day, crew_size, output_per_day
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path

try:
    from openpyxl import load_workbook
except ImportError:  # pragma: no cover
    sys.stderr.write("openpyxl is required. Install with `pip install openpyxl`.\n")
    sys.exit(2)


def slug(name: str) -> str:
    """Turn 'Bull Dozer' into 'BULL_DOZER' for use as a ResourceTypeDef code."""
    return re.sub(r"[^A-Z0-9]+", "_", name.upper()).strip("_")


def extract_norms(ws, *, is_manpower: bool):
    """Yield dicts. The same algorithm walks both Plant and Manpower sheets:
    rows with a numeric S.No. (column A) define the category; rows with blank A
    but populated B are activities under that category."""
    current_cat: str | None = None
    for row_idx in range(7, ws.max_row + 1):  # data starts at row 7 in both sheets
        sno = ws.cell(row=row_idx, column=1).value
        desc = ws.cell(row=row_idx, column=2).value
        unit_raw = ws.cell(row=row_idx, column=3).value     # e.g. "Sqm/Day"
        norm = ws.cell(row=row_idx, column=4).value          # output per day
        unit = ws.cell(row=row_idx, column=5).value          # e.g. "Sqm"

        if isinstance(sno, (int, float)) and desc:
            current_cat = str(desc).strip()
            continue

        if not desc or not current_cat:
            continue

        # Skip rows missing a productivity number — they're placeholders for activities
        # like "Camp Construction" the team logs but doesn't have a norm for yet.
        if norm is None or not isinstance(norm, (int, float)):
            continue

        record = {
            "category_name": current_cat,
            "activity_name": str(desc).strip(),
            "unit": (str(unit).strip() if unit else "").strip(),
            "output_per_day": float(norm),
        }
        if is_manpower:
            # Manpower sheet may also expose `output_per_man_per_day` and `crew_size` on
            # adjacent columns; the source workbook keeps them on the same row as `output_per_day`
            # (column D = per-man, column F = crew, column G = per-day). Guard for missing values.
            per_man = ws.cell(row=row_idx, column=4).value
            crew = ws.cell(row=row_idx, column=6).value if ws.max_column >= 6 else None
            per_day = ws.cell(row=row_idx, column=7).value if ws.max_column >= 7 else None
            if isinstance(per_day, (int, float)):
                record["output_per_day"] = float(per_day)
            record["output_per_man_per_day"] = float(per_man) if isinstance(per_man, (int, float)) else None
            record["crew_size"] = int(crew) if isinstance(crew, (int, float)) else None
        yield record


def write_plant_csv(rows, path):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["equipment_type_code", "equipment_type_name", "activity_name", "unit", "output_per_day"])
        w.writeheader()
        for r in rows:
            w.writerow({
                "equipment_type_code": slug(r["category_name"]),
                "equipment_type_name": r["category_name"],
                "activity_name": r["activity_name"],
                "unit": r["unit"],
                "output_per_day": r["output_per_day"],
            })


def write_manpower_csv(rows, path):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=[
            "category_code", "category_name", "activity_name", "unit",
            "output_per_man_per_day", "crew_size", "output_per_day"])
        w.writeheader()
        for r in rows:
            w.writerow({
                "category_code": slug(r["category_name"]),
                "category_name": r["category_name"],
                "activity_name": r["activity_name"],
                "unit": r["unit"],
                "output_per_man_per_day": r.get("output_per_man_per_day"),
                "crew_size": r.get("crew_size"),
                "output_per_day": r["output_per_day"],
            })


def main():
    ap = argparse.ArgumentParser(description="Extract Plant + Manpower norms from the Oman workbook.")
    ap.add_argument("--workbook", required=True, type=Path, help="Path to 2. Capacity_Utilization.xlsx")
    ap.add_argument("--out-dir", required=True, type=Path, help="Directory to write the two CSVs")
    args = ap.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)
    wb = load_workbook(args.workbook, data_only=True)

    plant_ws = wb["Plant utilization"]
    plant_rows = list(extract_norms(plant_ws, is_manpower=False))
    plant_path = args.out_dir / "oman_plant_norms.csv"
    write_plant_csv(plant_rows, plant_path)

    manpower_ws = wb["Manpower utilization"]
    manpower_rows = list(extract_norms(manpower_ws, is_manpower=True))
    manpower_path = args.out_dir / "oman_manpower_norms.csv"
    write_manpower_csv(manpower_rows, manpower_path)

    print(f"Wrote {len(plant_rows)} plant norms to {plant_path}")
    print(f"Wrote {len(manpower_rows)} manpower norms to {manpower_path}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Smoke-test the helper**

```bash
mkdir -p /tmp/oman-csv
python3 scripts/lib/oman_workbook_to_csv.py --workbook "/Volumes/Learning/road_project_test/oman/2. Capacity_Utilization.xlsx" --out-dir /tmp/oman-csv
head -5 /tmp/oman-csv/oman_plant_norms.csv
head -5 /tmp/oman-csv/oman_manpower_norms.csv
wc -l /tmp/oman-csv/*.csv
```
Expected: ~135 plant rows + ~100 manpower rows (per the spec). Both CSVs have header + at least 50 rows.

- [ ] **Step 3: Commit**

```bash
git add scripts/lib/oman_workbook_to_csv.py
git commit -m "feat(scripts): add Python helper to extract Oman workbook into CSVs for seeding"
```

---

## Task 2: Plant norms seed script

**Files:**
- Create: `scripts/seed-oman-plant-norms.sh`
- Delete: `scripts/seed-oman-equipment.sh` (after the new script is verified)

The script:
1. Logs in as admin (same pattern as `seed-icpms-data.sh` etc.)
2. Generates the plant CSV via the Python helper
3. For each row: upsert ResourceTypeDef (`POST /v1/resource-types`), upsert WorkActivity (`POST /v1/work-activities`), then create the productivity norm (`POST /v1/productivity-norms` with `normType=EQUIPMENT`).
4. Idempotent: skips existing rows by code/name lookup before posting.

- [ ] **Step 1: Write the script**

```bash
#!/usr/bin/env bash
#
# Seed Oman road-project equipment + per-(type × activity) productivity norms — full
# reproduction of the Plant utilization sheet from the Oman capacity workbook.
#
# Usage:
#   ./scripts/seed-oman-plant-norms.sh [--workbook PATH]
#
# Defaults to the workbook at /Volumes/Learning/road_project_test/oman/. Override with
# --workbook /path/to/2.\ Capacity_Utilization.xlsx
#
# Requires: bash, curl, jq, python3 + openpyxl. Backend running on :8080. Idempotent.

set -euo pipefail

WORKBOOK="/Volumes/Learning/road_project_test/oman/2. Capacity_Utilization.xlsx"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --workbook) WORKBOOK="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

API="${API:-http://localhost:8080}"
USER="${USER_NAME:-admin}"
PASS="${PASSWORD:-admin123}"

if [[ ! -f "$WORKBOOK" ]]; then
  echo "Workbook not found: $WORKBOOK" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "→ extracting workbook to CSV"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
python3 "$SCRIPT_DIR/lib/oman_workbook_to_csv.py" --workbook "$WORKBOOK" --out-dir "$TMP_DIR"
PLANT_CSV="$TMP_DIR/oman_plant_norms.csv"

echo "→ logging in as $USER"
TOKEN=$(curl -s -X POST "$API/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | jq -r '.data.accessToken')
[[ -z "$TOKEN" || "$TOKEN" == "null" ]] && { echo "❌ login failed" >&2; exit 1; }
H_AUTH=(-H "Authorization: Bearer $TOKEN")
H_JSON=(-H "Content-Type: application/json")

# Caches: avoid re-querying the API for every row.
declare -A TYPE_ID_CACHE
declare -A WA_ID_CACHE

resolve_type_id() {
  local code=$1 name=$2
  if [[ -n "${TYPE_ID_CACHE[$code]:-}" ]]; then
    echo "${TYPE_ID_CACHE[$code]}"
    return
  fi
  local existing
  existing=$(curl -s "${H_AUTH[@]}" "$API/v1/resource-types" \
    | jq -r --arg c "$code" '.data[] | select(.code == $c) | .id')
  if [[ -z "$existing" || "$existing" == "null" ]]; then
    existing=$(curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/resource-types" \
      -d "{\"code\":\"$code\",\"name\":\"$name\",\"baseCategory\":\"NONLABOR\",\"active\":true}" \
      | jq -r '.data.id')
  fi
  TYPE_ID_CACHE[$code]=$existing
  echo "$existing"
}

resolve_wa_id() {
  local name=$1 unit=$2
  local key="$name"
  if [[ -n "${WA_ID_CACHE[$key]:-}" ]]; then
    echo "${WA_ID_CACHE[$key]}"
    return
  fi
  local existing
  existing=$(curl -s "${H_AUTH[@]}" "$API/v1/work-activities" \
    | jq -r --arg n "$name" '.data[] | select((.name | ascii_downcase) == ($n | ascii_downcase)) | .id')
  if [[ -z "$existing" || "$existing" == "null" ]]; then
    existing=$(curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/work-activities" \
      -d "{\"name\":\"$name\",\"defaultUnit\":\"$unit\",\"active\":true}" \
      | jq -r '.data.id')
  fi
  WA_ID_CACHE[$key]=$existing
  echo "$existing"
}

upsert_norm() {
  local wa_id=$1 type_id=$2 unit=$3 output=$4
  local existing
  existing=$(curl -s "${H_AUTH[@]}" "$API/v1/productivity-norms?workActivityId=$wa_id" \
    | jq -r --arg t "$type_id" '.data[] | select(.resourceTypeDefId == $t and (.resourceId == null or .resourceId == "")) | .id' | head -1)
  if [[ -n "$existing" && "$existing" != "null" ]]; then
    echo "  ✓ existing $existing"
    return
  fi
  curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/productivity-norms" \
    -d "{\"normType\":\"EQUIPMENT\",\"workActivityId\":\"$wa_id\",\"resourceTypeDefId\":\"$type_id\",\"unit\":\"$unit\",\"outputPerDay\":$output}" \
    | jq -r '.data.id // .error.message'
}

echo "→ seeding plant equipment + activities + norms"
# Skip the CSV header.
tail -n +2 "$PLANT_CSV" | while IFS=, read -r type_code type_name activity_name unit output; do
  # Trim possible quoting around fields with commas.
  type_code=${type_code//\"/}
  type_name=${type_name//\"/}
  activity_name=${activity_name//\"/}
  unit=${unit//\"/}
  output=${output//\"/}
  type_id=$(resolve_type_id "$type_code" "$type_name")
  wa_id=$(resolve_wa_id "$activity_name" "$unit")
  if [[ -z "$type_id" || "$type_id" == "null" || -z "$wa_id" || "$wa_id" == "null" ]]; then
    echo "  ⚠ skipping $type_name × $activity_name (failed to resolve type or activity)"
    continue
  fi
  echo "→ $type_name × $activity_name = $output $unit"
  upsert_norm "$wa_id" "$type_id" "$unit" "$output"
done

echo "✅ Oman plant norms seed complete"
```

- [ ] **Step 2: Make executable + smoke-run**

```bash
chmod +x scripts/seed-oman-plant-norms.sh
# Backend must be running:
./scripts/seed-oman-plant-norms.sh
# Verify:
curl -s -H "Authorization: Bearer $(curl -s -X POST http://localhost:8080/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')" \
  "http://localhost:8080/v1/productivity-norms?normType=EQUIPMENT" | jq '.data | length'
```
Expected: norm count grows by ~135 (or stays the same on a re-run — idempotent).

- [ ] **Step 3: Delete the old `seed-oman-equipment.sh`**

```bash
git rm scripts/seed-oman-equipment.sh
```

- [ ] **Step 4: Commit**

```bash
git add scripts/seed-oman-plant-norms.sh
git commit -m "feat(scripts): full Oman plant norms seed (replaces partial seed-oman-equipment.sh)"
```

---

## Task 3: Manpower norms seed script

**Files:**
- Create: `scripts/seed-oman-manpower-norms.sh`

Same structure as Task 2 but for the manpower sheet. The norm body uses `normType=MANPOWER` and includes `outputPerManPerDay` + `crewSize` when the workbook had them.

- [ ] **Step 1: Write the script**

```bash
#!/usr/bin/env bash
#
# Seed Oman manpower categories + per-(category × activity) productivity norms — full
# reproduction of the Manpower utilization sheet.
#
# Usage:
#   ./scripts/seed-oman-manpower-norms.sh [--workbook PATH]
#
# Requires: bash, curl, jq, python3 + openpyxl. Backend running on :8080. Idempotent.

set -euo pipefail

WORKBOOK="/Volumes/Learning/road_project_test/oman/2. Capacity_Utilization.xlsx"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --workbook) WORKBOOK="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

API="${API:-http://localhost:8080}"
USER="${USER_NAME:-admin}"
PASS="${PASSWORD:-admin123}"

if [[ ! -f "$WORKBOOK" ]]; then
  echo "Workbook not found: $WORKBOOK" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "→ extracting workbook to CSV"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
python3 "$SCRIPT_DIR/lib/oman_workbook_to_csv.py" --workbook "$WORKBOOK" --out-dir "$TMP_DIR"
MAN_CSV="$TMP_DIR/oman_manpower_norms.csv"

echo "→ logging in as $USER"
TOKEN=$(curl -s -X POST "$API/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | jq -r '.data.accessToken')
[[ -z "$TOKEN" || "$TOKEN" == "null" ]] && { echo "❌ login failed" >&2; exit 1; }
H_AUTH=(-H "Authorization: Bearer $TOKEN")
H_JSON=(-H "Content-Type: application/json")

declare -A TYPE_ID_CACHE
declare -A WA_ID_CACHE

resolve_type_id() {
  local code=$1 name=$2
  if [[ -n "${TYPE_ID_CACHE[$code]:-}" ]]; then
    echo "${TYPE_ID_CACHE[$code]}"
    return
  fi
  local existing
  existing=$(curl -s "${H_AUTH[@]}" "$API/v1/resource-types" \
    | jq -r --arg c "$code" '.data[] | select(.code == $c) | .id')
  if [[ -z "$existing" || "$existing" == "null" ]]; then
    existing=$(curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/resource-types" \
      -d "{\"code\":\"$code\",\"name\":\"$name\",\"baseCategory\":\"LABOR\",\"active\":true}" \
      | jq -r '.data.id')
  fi
  TYPE_ID_CACHE[$code]=$existing
  echo "$existing"
}

resolve_wa_id() {
  local name=$1 unit=$2
  local key="$name"
  if [[ -n "${WA_ID_CACHE[$key]:-}" ]]; then
    echo "${WA_ID_CACHE[$key]}"
    return
  fi
  local existing
  existing=$(curl -s "${H_AUTH[@]}" "$API/v1/work-activities" \
    | jq -r --arg n "$name" '.data[] | select((.name | ascii_downcase) == ($n | ascii_downcase)) | .id')
  if [[ -z "$existing" || "$existing" == "null" ]]; then
    existing=$(curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/work-activities" \
      -d "{\"name\":\"$name\",\"defaultUnit\":\"$unit\",\"active\":true}" \
      | jq -r '.data.id')
  fi
  WA_ID_CACHE[$key]=$existing
  echo "$existing"
}

upsert_norm() {
  local wa_id=$1 type_id=$2 unit=$3 per_man=$4 crew=$5 output=$6
  local existing
  existing=$(curl -s "${H_AUTH[@]}" "$API/v1/productivity-norms?workActivityId=$wa_id" \
    | jq -r --arg t "$type_id" '.data[] | select(.resourceTypeDefId == $t and (.resourceId == null or .resourceId == "")) | .id' | head -1)
  if [[ -n "$existing" && "$existing" != "null" ]]; then
    echo "  ✓ existing $existing"
    return
  fi
  # Build JSON body with conditional optional fields.
  local body="{\"normType\":\"MANPOWER\",\"workActivityId\":\"$wa_id\",\"resourceTypeDefId\":\"$type_id\",\"unit\":\"$unit\",\"outputPerDay\":$output"
  if [[ -n "$per_man" && "$per_man" != "" ]]; then body+=",\"outputPerManPerDay\":$per_man"; fi
  if [[ -n "$crew" && "$crew" != "" ]]; then body+=",\"crewSize\":$crew"; fi
  body+="}"
  curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/productivity-norms" \
    -d "$body" | jq -r '.data.id // .error.message'
}

echo "→ seeding manpower categories + activities + norms"
tail -n +2 "$MAN_CSV" | while IFS=, read -r cat_code cat_name activity_name unit per_man crew output; do
  cat_code=${cat_code//\"/}
  cat_name=${cat_name//\"/}
  activity_name=${activity_name//\"/}
  unit=${unit//\"/}
  per_man=${per_man//\"/}
  crew=${crew//\"/}
  output=${output//\"/}
  type_id=$(resolve_type_id "$cat_code" "$cat_name")
  wa_id=$(resolve_wa_id "$activity_name" "$unit")
  if [[ -z "$type_id" || "$type_id" == "null" || -z "$wa_id" || "$wa_id" == "null" ]]; then
    echo "  ⚠ skipping $cat_name × $activity_name (failed to resolve type or activity)"
    continue
  fi
  echo "→ $cat_name × $activity_name = $output $unit (perMan=$per_man, crew=$crew)"
  upsert_norm "$wa_id" "$type_id" "$unit" "$per_man" "$crew" "$output"
done

echo "✅ Oman manpower norms seed complete"
```

- [ ] **Step 2: Make executable + smoke-run**

```bash
chmod +x scripts/seed-oman-manpower-norms.sh
./scripts/seed-oman-manpower-norms.sh
# Verify:
curl -s -H "Authorization: Bearer $(curl -s -X POST http://localhost:8080/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')" \
  "http://localhost:8080/v1/productivity-norms?normType=MANPOWER" | jq '.data | length'
```
Expected: norm count = ~100. Re-running should be a no-op.

- [ ] **Step 3: Commit**

```bash
git add scripts/seed-oman-manpower-norms.sh
git commit -m "feat(scripts): full Oman manpower norms seed"
```

---

## Task 4: Document the scripts in CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add to the seed-data section**

Find the "Seeding demo data" section (currently lists `seed-demo-data.sh`, `seed-icpms-data.sh`, etc.) and append:
```bash
./scripts/seed-oman-plant-norms.sh    # Equipment-side norms from the Oman capacity workbook
./scripts/seed-oman-manpower-norms.sh # Manpower-side norms from the Oman capacity workbook
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: list Oman plant + manpower seed scripts in CLAUDE.md"
```

---

## Task 5: Final verification

- [ ] **Step 1: Re-run both scripts (idempotency check)**

```bash
./scripts/seed-oman-plant-norms.sh
./scripts/seed-oman-manpower-norms.sh
```
Expected: every line says `✓ existing` (no new norms posted on second run).

- [ ] **Step 2: Verify norm counts match the workbook**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')
echo "EQUIPMENT norms:" && curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/v1/productivity-norms?normType=EQUIPMENT" | jq '.data | length'
echo "MANPOWER norms:" && curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/v1/productivity-norms?normType=MANPOWER" | jq '.data | length'
```
Expected: equipment ≈ 135, manpower ≈ 100. (Numbers approximate — depends on how many workbook rows have a productivity number; rows with "no norm" placeholders are skipped by the helper.)

- [ ] **Step 3: Smoke-test the capacity utilization report (depends on Plan 1)**

If Plan 1 is merged, the seeded norms should now be visible in the report. With a project + a few daily outputs:
```bash
PROJECT_ID=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/projects | jq -r '.data[0].id')
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/v1/reports/capacity-utilization?projectId=$PROJECT_ID&normType=EQUIPMENT" | jq '.data.rows | length'
```
Expected: a positive count when daily outputs exist for matching activities; zero otherwise.

---

## Self-Review Notes

**Spec coverage** (against `docs/superpowers/specs/2026-04-27-capacity-utilization-reports-design.md` § 7):
- Replace partial seed → Tasks 2 + 3
- Python helper extracts workbook → Task 1
- Two parallel scripts (Plant / Manpower) → Tasks 2 + 3
- Boot-time Oman seeder explicitly OUT of scope ✅ (no `OmanRoadProjectSeeder.java` created)
- Documentation → Task 4

**Type / contract consistency:**
- Endpoints called: `POST /v1/auth/login`, `POST /v1/resource-types`, `POST /v1/work-activities`, `POST /v1/productivity-norms`, `GET /v1/productivity-norms?workActivityId=...`. All exist in the current backend.
- ResourceTypeDef `baseCategory`: `NONLABOR` for plant, `LABOR` for manpower (matches `ResourceType` enum).
- `workActivityId` on the norm body matches the field name in `CreateProductivityNormRequest`.
- After Plan 1 is merged, `CreateProductivityNormRequest` gains a `projectId` field; the script omits it → the resulting norms are global (project_id IS NULL), which is the intended behavior.
- Idempotency via existence check before POST keeps re-runs safe.

**Boundaries:**
- Source workbook stays where it is (`/Volumes/Learning/road_project_test/oman/`). The script accepts `--workbook` so a different copy can be used.
- No backend code changes. No schema changes.
- No commitment to the workbook being checked into the repo.
