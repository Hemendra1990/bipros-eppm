#!/usr/bin/env python3
"""
Seed 50 demo permits against the running EPPM backend, exercising the workflow
so the dashboard tiles (Active / Pending Review / Expiring Today / Closed This
Month / Status Breakdown) all populate.

Targets the IOCL Panipat project (code WO70143247). Each permit goes through
real REST calls — create → submit → approve → issue → start → close/revoke —
so lifecycle events, approvals, PPE checks, and gas tests are all populated.

Usage:
    python3 scripts/seed-permits-iocl.py

Env vars:
    BASE          backend URL          (default http://localhost:8080)
    USER_NAME     login                (default admin)
    USER_PASS     password             (default admin123)
    PROJECT_CODE  target project code  (default WO70143247)
"""
import json
import os
import random
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

BASE = os.environ.get("BASE", "http://localhost:8080")
USER = os.environ.get("USER_NAME", "admin")
PASS = os.environ.get("USER_PASS", "admin123")
PROJECT_CODE = os.environ.get("PROJECT_CODE", "WO70143247")

random.seed(42)
NOW = datetime.now(timezone.utc).replace(microsecond=0)
TODAY_END = NOW.replace(hour=23, minute=59, second=0)

# ──────────────────────────────────────────────────────────────────────────
# HTTP helpers
# ──────────────────────────────────────────────────────────────────────────

def call(method, path, token=None, body=None, soft=False):
    """Call the API. Returns response.data on 2xx, None on error if soft=True."""
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            payload = r.read().decode()
            if not payload:
                return None
            parsed = json.loads(payload)
            return parsed.get("data") if isinstance(parsed, dict) else parsed
    except urllib.error.HTTPError as e:
        body_txt = e.read().decode()[:500]
        msg = f"{method} {path} → HTTP {e.code} : {body_txt}"
        if soft:
            print(f"  ⚠ {msg}", file=sys.stderr)
            return None
        raise SystemExit(f"FATAL: {msg}")


def login():
    print(f"[0] Login as {USER} → {BASE}")
    data = call("POST", "/v1/auth/login", body={"username": USER, "password": PASS})
    return data["accessToken"]


def find_project(token):
    page = call("GET", "/v1/projects?size=200", token=token)
    for p in page["content"]:
        if p["code"] == PROJECT_CODE:
            return p["id"]
    raise SystemExit(f"FATAL: project {PROJECT_CODE} not found — seed it first via scripts/seed-iocl-panipat-wo.sh")


def load_permit_types(token):
    """Map every type code → {id, ppe_ids[]}."""
    types = {}
    seen = set()
    packs = call("GET", "/v1/permit-packs", token=token)
    for pack in packs:
        for t in call("GET", f"/v1/permit-packs/{pack['code']}/types", token=token):
            if t["code"] in seen:
                continue
            seen.add(t["code"])
            ppe = call("GET", f"/v1/permit-types/{t['id']}/ppe-items", token=token)
            types[t["code"]] = {"id": t["id"], "ppeIds": [p["id"] for p in ppe]}
    return types


# ──────────────────────────────────────────────────────────────────────────
# Demo data pools
# ──────────────────────────────────────────────────────────────────────────

WORKERS = [
    ("Rajesh Kumar", "IND-1029384756", "Indian", "Welder"),
    ("Mohammed Ali", "OMA-2938475610", "Omani", "Fitter"),
    ("Suresh Reddy", "IND-3847561029", "Indian", "Helper"),
    ("Anil Verma", "IND-4756102938", "Indian", "Mason"),
    ("Vikram Singh", "IND-5610293847", "Indian", "Crane Operator"),
    ("Karthik Iyer", "IND-6102938475", "Indian", "Electrician"),
    ("Ramesh Patel", "IND-7029384756", "Indian", "Rigger"),
    ("Hassan Al-Balushi", "OMA-8475610293", "Omani", "Supervisor Trainee"),
    ("Prakash Joshi", "IND-9384756102", "Indian", "Scaffolder"),
    ("Mahesh Yadav", "IND-1837465019", "Indian", "Welder"),
    ("Sajeev Nair", "IND-2746501938", "Indian", "Pipe Fitter"),
    ("Salim Al-Hinai", "OMA-3655019283", "Omani", "Foreman"),
    ("Bhupinder Pal", "IND-4564018273", "Indian", "Helper"),
    ("Govind Rao", "IND-5473961827", "Indian", "Steel Erector"),
    ("Faisal Mohammed", "OMA-6382851728", "Omani", "Surveyor"),
]

LOCATIONS = [
    "Bitumen Plant – Bay 1",
    "TT Loading Gantry – East",
    "Tank Farm Area B",
    "Pump House #2",
    "Skid F-04 platform",
    "OWC Tank top",
    "Pipe rack PR-12",
    "Filling station Bay 3",
    "Sub-station SS-2",
    "Drain pit DP-7",
    "Steam tracing bridge",
    "MOV-101 valve pit",
]

CHAINAGES = [
    "GA-01/A-2", "GA-02/B-4", "GA-03/C-1", "PR-12/L-3",
    "TF-B/T-7", "PH-2/P-9", "SS-2/E-5", "DP-7/N-2", "MOV-101",
]


# ──────────────────────────────────────────────────────────────────────────
# Recipes — what 50 permits should look like
# ──────────────────────────────────────────────────────────────────────────
# Status flow reminder (from PermitPackSeeder default flow + state machine):
#   submit                 → PENDING_SITE_ENGINEER  (always)
#   approve step 1 (FRMN)  → PENDING_SITE_ENGINEER  (next role)
#   approve step 2 (SE)    → PENDING_HSE  or  AWAITING_GAS_TEST  (gas-required types)
#   gas test PASS          → PENDING_HSE
#   approve step 3 (HSE)   → APPROVED  or  PENDING_PM  (HIGH + needsPmStep types)
#   approve step 4 (PM)    → APPROVED
#   issue                  → ISSUED      (sets validFrom/To, generates QR)
#   start                  → IN_PROGRESS
#   close                  → CLOSED
#   suspend                → SUSPENDED
#   revoke                 → REVOKED
#   reject any step        → REJECTED

# Each recipe: dict with keys
#   type, risk, status, supervisor, task, start_h, dur_h, expires_today
RECIPES = []

def add(count, **kw):
    for i in range(count):
        r = dict(kw)
        r["task"] = kw["task"].format(n=len(RECIPES) + 1)
        RECIPES.append(r)

# DRAFT (2)
add(2, type="CIVIL_WORKS", risk="LOW", status="DRAFT",
    supervisor="Indresh Kumar", task="[DEMO] Concrete pouring at TT loading bay #{n}",
    start_h=4, dur_h=8)

# PENDING_SITE_ENGINEER (8 total)
add(3, type="CIVIL_WORKS", risk="LOW", status="PENDING_SITE_ENGINEER",
    supervisor="Sandeep Patel", task="[DEMO] Site preparation for skid #{n}",
    start_h=2, dur_h=24)
add(2, type="ELECTRICAL", risk="MEDIUM", status="PENDING_SITE_ENGINEER",
    supervisor="Kiran Babu", task="[DEMO] Cable tray erection segment #{n}",
    start_h=6, dur_h=24)
add(3, type="TRAFFIC_MGMT", risk="LOW", status="PENDING_SITE_ENGINEER",
    supervisor="Ravi Kumar", task="[DEMO] Lane closure for site mobilisation #{n}",
    start_h=8, dur_h=48)

# PENDING_HSE (5)
add(3, type="EXCAVATION", risk="MEDIUM", status="PENDING_HSE",
    supervisor="Anand Krishnan", task="[DEMO] Foundation excavation – pumphouse #{n}",
    start_h=12, dur_h=48)
add(2, type="ELECTRICAL", risk="MEDIUM", status="PENDING_HSE",
    supervisor="Suresh Iyer", task="[DEMO] Switchgear bay #{n} cabling",
    start_h=18, dur_h=72)

# AWAITING_GAS_TEST (2 — HOT_WORK has gasTestRequired=true)
add(2, type="HOT_WORK", risk="HIGH", status="AWAITING_GAS_TEST",
    supervisor="Mohammed Khalid", task="[DEMO] Stick weld on tank shell – patch #{n}",
    start_h=24, dur_h=12)

# PENDING_PM (3 — HIGH risk + needsPmStep types)
add(2, type="LIFTING", risk="HIGH", status="PENDING_PM",
    supervisor="Ramesh Chand", task="[DEMO] 50T mobile crane tandem lift, skid #{n}",
    start_h=36, dur_h=8)
add(1, type="WORKING_AT_HEIGHTS", risk="HIGH", status="PENDING_PM",
    supervisor="Vinod Sharma", task="[DEMO] Insulation cladding at +18m, tank #{n}",
    start_h=48, dur_h=8)

# APPROVED (4 — all approves done, not yet issued)
add(2, type="CIVIL_WORKS", risk="LOW", status="APPROVED",
    supervisor="Rajesh Kumar", task="[DEMO] Drain line laying segment #{n}",
    start_h=30, dur_h=24)
add(1, type="ELECTRICAL", risk="MEDIUM", status="APPROVED",
    supervisor="Suresh Iyer", task="[DEMO] Power-on testing panel #{n}",
    start_h=20, dur_h=16)
add(1, type="EXCAVATION", risk="MEDIUM", status="APPROVED",
    supervisor="Anand Krishnan", task="[DEMO] Trench cut for fire-water line #{n}",
    start_h=15, dur_h=24)

# ISSUED (7 — 2 of these expire today)
add(3, type="CIVIL_WORKS", risk="LOW", status="ISSUED",
    supervisor="Sandeep Patel", task="[DEMO] Block masonry for shed wall #{n}",
    start_h=-2, dur_h=24)
add(2, type="EXCAVATION", risk="MEDIUM", status="ISSUED",
    supervisor="Anand Krishnan", task="[DEMO] Cable trench section #{n}",
    start_h=-4, dur_h=12)
add(2, type="ELECTRICAL", risk="MEDIUM", status="ISSUED",
    supervisor="Kiran Babu", task="[DEMO] LV panel hookup #{n}",
    start_h=-6, dur_h=12, expires_today=True)

# IN_PROGRESS (5 — 1 expires today)
add(3, type="HOT_WORK", risk="HIGH", status="IN_PROGRESS",
    supervisor="Mohammed Khalid", task="[DEMO] Welding seam on tank #{n} bottom plate",
    start_h=-12, dur_h=24)
add(1, type="LIFTING", risk="HIGH", status="IN_PROGRESS",
    supervisor="Ramesh Chand", task="[DEMO] Crane lift – heat exchanger #{n}",
    start_h=-3, dur_h=8)
add(1, type="TRAFFIC_MGMT", risk="LOW", status="IN_PROGRESS",
    supervisor="Ravi Kumar", task="[DEMO] Traffic deviation gate #{n}",
    start_h=-8, dur_h=12, expires_today=True)

# SUSPENDED (1) — WORKING_AT_HEIGHTS caps at 12h max duration
add(1, type="WORKING_AT_HEIGHTS", risk="HIGH", status="SUSPENDED",
    supervisor="Vinod Sharma", task="[DEMO] Roof sheet replacement bay #{n} – paused for wind",
    start_h=-6, dur_h=12)

# CLOSED (8 — closedAt set when API called, falls in current month)
add(3, type="HOT_WORK", risk="HIGH", status="CLOSED",
    supervisor="Mohammed Khalid", task="[DEMO] Pipe weld at flange #{n}",
    start_h=-72, dur_h=8)
add(2, type="CIVIL_WORKS", risk="LOW", status="CLOSED",
    supervisor="Sandeep Patel", task="[DEMO] PCC laid for plinth #{n}",
    start_h=-96, dur_h=16)
add(2, type="EXCAVATION", risk="MEDIUM", status="CLOSED",
    supervisor="Anand Krishnan", task="[DEMO] Trench backfill section #{n}",
    start_h=-60, dur_h=24)
add(1, type="ELECTRICAL", risk="MEDIUM", status="CLOSED",
    supervisor="Kiran Babu", task="[DEMO] Distribution box install #{n}",
    start_h=-48, dur_h=12)

# REJECTED (3)
add(2, type="EXCAVATION", risk="MEDIUM", status="REJECTED",
    supervisor="Suresh Iyer", task="[DEMO] Trench abandoned – soil instability bay #{n}",
    start_h=-2, dur_h=24)
add(1, type="HOT_WORK", risk="HIGH", status="REJECTED",
    supervisor="Mohammed Khalid", task="[DEMO] Cancelled – inadequate JSA #{n}",
    start_h=-1, dur_h=8)

# REVOKED (2)
add(1, type="LIFTING", risk="HIGH", status="REVOKED",
    supervisor="Ramesh Chand", task="[DEMO] Crane lift cancelled – wind gusts #{n}",
    start_h=-4, dur_h=6)
add(1, type="WORKING_AT_HEIGHTS", risk="HIGH", status="REVOKED",
    supervisor="Vinod Sharma", task="[DEMO] Scaffold non-compliant – revoked #{n}",
    start_h=-3, dur_h=12)

assert len(RECIPES) == 50, f"expected 50 recipes, got {len(RECIPES)}"


# ──────────────────────────────────────────────────────────────────────────
# Workflow driver
# ──────────────────────────────────────────────────────────────────────────

def isoz(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def build_create_body(recipe, type_meta, idx):
    if recipe.get("expires_today"):
        end_at = TODAY_END
        start_at = end_at - timedelta(hours=recipe["dur_h"])
    else:
        start_at = NOW + timedelta(hours=recipe["start_h"])
        end_at = start_at + timedelta(hours=recipe["dur_h"])
    # Pick 2-3 workers, deterministically per index
    rng = random.Random(idx)
    n_workers = rng.choice([2, 2, 3])
    chosen = rng.sample(WORKERS, n_workers)
    workers = []
    for i, (name, civil, nat, trade) in enumerate(chosen):
        workers.append({
            "fullName": name,
            "civilId": civil,
            "nationality": nat,
            "trade": trade,
            "roleOnPermit": "PRINCIPAL" if i == 0 else "HELPER",
        })
    shift = "NIGHT" if recipe["type"] == "NIGHT_SHIFT" else rng.choice(["DAY", "DAY", "DAY", "NIGHT"])
    return {
        "permitTypeTemplateId": type_meta["id"],
        "riskLevel": recipe["risk"],
        "supervisorName": recipe["supervisor"],
        "locationZone": rng.choice(LOCATIONS),
        "chainageMarker": rng.choice(CHAINAGES),
        "startAt": isoz(start_at),
        "endAt": isoz(end_at),
        "shift": shift,
        "taskDescription": recipe["task"],
        "workers": workers,
        "confirmedPpeItemIds": type_meta["ppeIds"],
        "declarationAccepted": True,
    }


def approve_until(token, project_id, permit_id, target_status):
    """Drive a permit forward through approvals until it reaches target_status (or APPROVED).
    Records gas test PASS if blocked at AWAITING_GAS_TEST. Returns final detail dict.
    """
    for _ in range(8):  # safety bound
        detail = call("GET", f"/v1/permits/{permit_id}", token=token)
        status = detail["status"]
        if status == target_status:
            return detail
        if status in ("APPROVED", "ISSUED", "IN_PROGRESS", "CLOSED", "REJECTED", "REVOKED"):
            return detail
        if status == "DRAFT":
            call("POST", f"/v1/projects/{project_id}/permits/{permit_id}/submit", token=token)
            continue
        if status == "AWAITING_GAS_TEST":
            call("POST", f"/v1/permits/{permit_id}/gas-tests", token=token, body={
                "lelPct": 0.0, "o2Pct": 20.9, "h2sPpm": 0, "coPpm": 0,
                "result": "PASS", "instrumentSerial": "MX6-IBRID-00421",
            })
            continue
        # PENDING_* → approve current step
        step = detail.get("currentApprovalStep") or 1
        # find a PENDING approval to act on
        pending_steps = [a["stepNo"] for a in detail.get("approvals", [])
                         if a.get("status") == "PENDING"]
        if not pending_steps:
            return detail
        step = pending_steps[0]
        call("POST", f"/v1/permits/{permit_id}/approvals/{step}/approve", token=token,
             body={"remarks": "Reviewed and approved."})
    return call("GET", f"/v1/permits/{permit_id}", token=token)


def drive_to(token, project_id, permit_id, target):
    """Drive a freshly-created DRAFT permit to the target terminal status."""
    if target == "DRAFT":
        return
    if target == "PENDING_SITE_ENGINEER":
        call("POST", f"/v1/projects/{project_id}/permits/{permit_id}/submit", token=token)
        return
    if target in ("PENDING_HSE", "AWAITING_GAS_TEST", "PENDING_PM", "APPROVED",
                  "ISSUED", "IN_PROGRESS", "SUSPENDED", "CLOSED", "REVOKED"):
        # Submit, then walk approvals to either target or APPROVED.
        call("POST", f"/v1/projects/{project_id}/permits/{permit_id}/submit", token=token)
        approve_until(token, project_id, permit_id, target)
        if target in ("PENDING_HSE", "AWAITING_GAS_TEST", "PENDING_PM"):
            return  # approve_until stopped at the right place
        # Past APPROVED → issue
        if target == "APPROVED":
            return
        call("POST", f"/v1/permits/{permit_id}/issue", token=token)
        if target == "ISSUED":
            return
        if target in ("IN_PROGRESS", "SUSPENDED", "CLOSED", "REVOKED"):
            if target != "REVOKED":
                call("POST", f"/v1/permits/{permit_id}/start", token=token)
            if target == "IN_PROGRESS":
                return
            if target == "SUSPENDED":
                call("POST", f"/v1/permits/{permit_id}/suspend", token=token,
                     body={"reason": "Wind speed exceeded threshold during morning shift."})
                return
            if target == "CLOSED":
                call("POST", f"/v1/permits/{permit_id}/close", token=token,
                     body={"remarks": "Work completed; site cleared and inspected."})
                return
            if target == "REVOKED":
                call("POST", f"/v1/permits/{permit_id}/revoke", token=token,
                     body={"reason": "Conditions changed materially; permit cancelled."})
                return
    if target == "REJECTED":
        call("POST", f"/v1/projects/{project_id}/permits/{permit_id}/submit", token=token)
        call("POST", f"/v1/permits/{permit_id}/approvals/1/reject", token=token,
             body={"reason": "Method statement insufficient — please revise."})
        return


def existing_demo_tasks(token, project_id):
    """Return the set of task descriptions for permits already on this project that
    look like demo permits (start with [DEMO]). Used to skip recipes on re-run."""
    seen = set()
    page = 0
    while True:
        resp = call("GET", f"/v1/permits?projectId={project_id}&page={page}&size=100",
                    token=token, soft=True)
        if not resp:
            break
        for p in resp.get("content", []):
            # PermitSummary exposes the field as `workDescription` (not taskDescription).
            task = (p.get("workDescription") or p.get("taskDescription") or "")
            if task.startswith("[DEMO]"):
                seen.add(task)
        if resp.get("last", True):
            break
        page += 1
    return seen


def main():
    token = login()
    project_id = find_project(token)
    print(f"[1] Project {PROJECT_CODE} → {project_id}")
    types = load_permit_types(token)
    print(f"[2] Loaded {len(types)} permit type templates")

    already = existing_demo_tasks(token, project_id)
    if already:
        print(f"[3] {len(already)} demo permits already exist on this project — they will be skipped")

    by_status = {}
    skipped = 0
    for idx, recipe in enumerate(RECIPES, start=1):
        type_meta = types.get(recipe["type"])
        if not type_meta:
            print(f"  ⚠ unknown type {recipe['type']}, skipping recipe {idx}", file=sys.stderr)
            continue
        if recipe["task"] in already:
            skipped += 1
            print(f"  [{idx:2d}/50] (skip) already present: {recipe['task'][:60]}")
            continue
        body = build_create_body(recipe, type_meta, idx)
        created = call("POST", f"/v1/projects/{project_id}/permits", token=token, body=body)
        permit_id = created["id"]
        permit_code = created["permitCode"]
        try:
            drive_to(token, project_id, permit_id, recipe["status"])
        except SystemExit:
            raise
        except Exception as e:
            print(f"  ⚠ {permit_code} drive_to({recipe['status']}) failed: {e}", file=sys.stderr)
        final = call("GET", f"/v1/permits/{permit_id}", token=token, soft=True)
        actual = final["status"] if final else "?"
        by_status[actual] = by_status.get(actual, 0) + 1
        marker = "✓" if actual == recipe["status"] else "≠"
        print(f"  [{idx:2d}/50] {permit_code}  {recipe['type']:<20} {recipe['risk']:<6} → {actual:<22} {marker}")

    print()
    print("Status breakdown (this run):")
    for status, count in sorted(by_status.items(), key=lambda kv: -kv[1]):
        print(f"  {status:<24} {count}")
    print()
    print(f"Done. Created {sum(by_status.values())} new permits, skipped {skipped} already-present.")


if __name__ == "__main__":
    main()
