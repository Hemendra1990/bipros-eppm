# IOCL Panipat — Work Order 70143247

Structured extract of `WO IOCL PANIPAT WORK ORDER 70143247 DEs tech.pdf` (123 pp., SAP `YMPR_SPO` form). Source of truth for EPPM seed/test data. All values from the PDF; anything our schema needs that the PDF does not state is flagged in §10.

---

## 1. Header facts

| Field | Value |
|---|---|
| Purchasing Document | **PT-09 / 70143247** |
| Document / WO Date | **2024-07-19** (19.07.2024) |
| Buyer | **Indian Oil Corporation Ltd** (IOCL) |
| Plant / Site | **1121 — Panipat Terminal**, PIN 132140, India (ISO accredited) |
| Plant GSTIN | 06AAACI1681G1ZT |
| Vendor | **DE'S TECHNICO LIMITED**, vendor code **10108488** |
| Vendor address | 11, Bauria Industrial Estate, Chak-Kashi, Howrah 711307 |
| Vendor contact | Tel +91 98300 62020 · Email CALDTPL@GMAIL.COM |
| Subject | Civil and Mechanical Package for **Revamping of Bitumen Filling Plant** and **augmentation of facilities for bulk TT loading** at Panipat Bitumen Plant |
| Completion period | **11 Months** (from start) |
| Currency | INR |
| WO Net Value (excl. taxes) | **₹ 18,93,70,825.01** |
| WO Value incl. GST | **₹ 22,34,57,573.03** (GST 18 %) |
| Surcharge on Net | 8.25 % (applied per WO item) |
| EIC | **Angom Rajen Singh**, Dy. General Manager (Engineering), DSO · `ANGOMRAJEN@INDIANOIL.IN` |
| Site Engineer | **Indresh Kumar**, Asst. Manager (Engg.) |
| Signatory | Angom Rajen Singh, DGM (Engg.), Authorised Signatory |

---

## 2. EPS / OBS positioning

EPPM mapping the WO should sit under:

```
EPS
└── IOCL (Indian Oil Corporation Ltd)          code: IOCL
    └── Panipat Terminal                       code: PANIPAT
        └── Bitumen Plant                      code: PNP-BITUMEN
            └── Project: WO-70143247 (this WO) code: WO-70143247
```

OBS:

```
OBS
└── DSO (Direct Supply Office?) — Engineering org under EIC Angom Rajen Singh
    └── Site Engineering (Indresh Kumar)
```

Contractor (DE'S TECHNICO LIMITED) — modelled either as an OBS node at the vendor side, or as the Contract record (see §8).

---

## 3. WBS hierarchy

Top-level WBS is the 5 SAP "WORK ORDER ITEM" groupings. Counts are BOQ line items under each.

| WBS L1 | Code | Name | BOQ items | Base (₹) | Net incl. 8.25 % surcharge (₹) |
|---|---|---|---:|---:|---:|
| 1 | **00010** | TT bulk loading — **Civil works** | ~294 | 5,39,01,448.47 | 5,83,48,317.97 |
| 2 | **00020** | **Part-B — Mechanical work** (pipeline laying, welding, hydro-testing) | ~160 | 3,35,96,848.00 | 3,63,68,587.96 |
| 3 | **00030** | **Part-C (yellow)** — Valves / CS pipe supply (product, steam, IA, SW, FW) | ~187 | 3,77,51,940.00 | 4,08,66,475.05 |
| 4 | **00040** | **Part-D (green)** — Steam heat tracing / condensate / steam & condensate manifolds | ~32 | 4,51,93,170.00 | 4,89,21,606.53 |
| 5 | **00050** | **Part-E (blue)** — **Dismantling** of existing piping & pumps | ~6 | 44,95,000.00 | 48,65,837.50 |
| **Total** | | | **~679** | **17,49,38,406.47** | **18,93,70,825.01** |

**WBS L2 — major sub-groups inside each L1** (inferred from BOQ descriptions):

- **00010 Civil** — site clearing · earthwork · concrete (PCC/RCC) · formwork · reinforcement · masonry · plaster · flooring · waterproofing · roofing · doors/windows · sanitary/plumbing · painting · boundary wall/fencing · drains · miscellaneous
- **00020 Mechanical pipeline** — bitumen piping (above-ground laying & welding 1″ and above; 3-run welding; DP testing; hydrotest at 1.5× design pressure for min 4 hrs) · fittings installation (elbows, bends, tees, reducers) · supports
- **00030 Valves & CS pipe supply** — CS valves (ball, globe, check) for product/steam/IA/SW/FW lines · CS pipes 6.35 mm BE ERW/LSAW sized 100–200 NB · seamless pipes · flanges & gaskets
- **00040 Steam/condensate tracing** — steam heat tracing system (50 NB and smaller) · steam manifolds (40 NB × 20 NB, 4-way/8-way/12-way, up to 20 kg/cm²) · condensate collection manifolds (Forbes Marshall or equivalent) · Y-type strainers, piston valves, steam traps
- **00050 Dismantling** — dismantling of existing piping system (~45,000 m) with wastage cap 20 % · dismantling of existing bitumen loading pumps (Twin Screw, 117 m³/hr @ 95 MWC)

---

## 4. Activities

The PDF is a BOQ/service-PO — it does **not** list discrete scheduled activities, durations, or predecessor/successor relationships. The seed script must **synthesise** activities from the WBS structure above, applying sensible defaults. Approach:

- **One activity per WBS L2 group** (≈25 activities total).
- **Durations** derived from the 11-month completion period, distributed by section value weight:
  - Civil (31 %): ~14 weeks
  - Mechanical pipeline (19 %): ~9 weeks
  - Valves/CS pipe supply (22 %): ~10 weeks (can overlap with pipeline)
  - Steam tracing (26 %): ~12 weeks
  - Dismantling (2 %): ~3 weeks (runs first, enables other work)
- **Relationships** inferred:
  - Dismantling (00050) → precedes all other sections (FS, lag 0)
  - Civil (00010) → precedes pipeline laying (00020) (FS, where applicable)
  - Valve/pipe supply (00030) → precedes installation (00020, 00040) (FS)
  - Steam tracing (00040) → after mechanical (00020) (FS)
- **Calendars** — standard 6-day work week (IOCL refinery convention), 8 hrs/day.
- Start date assumed = **2024-08-01** (~ 2 weeks after LOA/WO date).
- Planned finish = start + 11 months = **2025-06-30**.

These assumptions are encoded in the seed script; user can override via env vars.

---

## 5. BOQ items (679 lines)

**Structure of each line** (SAP YMPR_SPO format):
```
O.Lev   Item No     Quantity   Unit   Rate          Amount
Sr.No   Item Description (multi-line, may include specs, note1, note2, ...)
```

**Sample from 00010 Civil:**
```
00010 MPLCN00200 10      1,040.000   M2      5.50       5,720.00
      Clearing grass and removal of the rubbish outside site premises ...
00070 MPLCN01800 70      1,710.000   M3    188.66     322,608.60
      Filling available excavated earth (excluding rock) in trenches ...
```

**Sample from 00030 Part-C valves:**
```
01630  1620.     10.000   EA   33,478.00   334,780.00
       100 NB Ball VALVE API 6D ASTM A216 WCB, ASME B16.10 CL 150 Flanged RF ...
```

**Full parsed BOQ:** extracted to `scripts/iocl-panipat-boq.tsv` (tab-separated: `wo_item`, `line_no`, `sap_code`, `sr_no`, `qty`, `unit`, `rate`, `amount`, `description`). The seed script iterates this TSV to `POST /v1/projects/{id}/expenses` + `POST /v1/cost-accounts`.

**Cost-account mapping:** one cost account per WBS L2 group; BOQ items attach to the account matching their L2 bucket.

---

## 6. Resources

The PDF does not enumerate resources — only the work scope. **Resources to seed** (synthesised from BOQ descriptions, based on IOCL-standard civil/mechanical construction crews):

> EPPM `ResourceType` enum: **`LABOR`**, **`NONLABOR`** (equipment), **`MATERIAL`**. (No `LABOUR`/`EQUIPMENT` values.)

### Labour — `LABOR` (with indicative rates — ₹/hr, to be confirmed against actual market)
| Code | Name | Type | Max units/day | Hourly rate (₹) | Overtime (₹) |
|---|---|---|---:|---:|---:|
| L-SKL-MASON | Skilled mason | LABOR | 8 | 125 | 187 |
| L-SKL-WELDER | Certified pipe welder (3G/6G) | LABOR | 8 | 250 | 375 |
| L-SKL-FITTER | Pipe fitter | LABOR | 8 | 200 | 300 |
| L-SKL-ELEC | Electrician | LABOR | 8 | 175 | 262 |
| L-UNSKL | Unskilled helper | LABOR | 8 | 60 | 90 |
| L-SUPV | Site supervisor | LABOR | 8 | 400 | 600 |

### Equipment — `NONLABOR`
| Code | Name | Type | Rate (₹/day) |
|---|---|---|---:|
| E-EXC-20T | Hydraulic excavator 20 T | NONLABOR | 12,000 |
| E-CRANE-25T | Mobile crane 25 T | NONLABOR | 15,000 |
| E-WELD-DC400 | DC welding set 400 A | NONLABOR | 800 |
| E-HYDRO-PUMP | Hydrotest pump (up to 50 kg/cm²) | NONLABOR | 2,500 |
| E-COMP-185 | Air compressor 185 CFM | NONLABOR | 3,500 |
| E-SCAFF-SET | Double scaffolding set | NONLABOR | — (per BOQ) |

### Material (free-issued by IOCL — modelled as 0-cost material resources so they appear on Gantt for procurement tracking)
| Code | Name | Type |
|---|---|---|
| M-CS-PIPE | CS pipe (all sizes) — free issue by IOCL | MATERIAL |
| M-BITUMEN | Bitumen (for loading pump commissioning) | MATERIAL |
| M-INSUL | Pipe insulation + cladding | MATERIAL |

---

## 7. Calendar rules

Not explicitly stated in the PDF. **Defaults for seed**:

- **Work week:** Mon–Sat WORKING (6-day, IOCL convention for refinery construction), Sunday NON-WORKING.
- **Work hours:** 08:00–12:00 + 13:00–17:00 = 8 hrs/day standard; OT after 17:00.
- **Holidays (India statutory 2024-25, likely observed):** Republic Day (Jan 26), Independence Day (Aug 15), Gandhi Jayanti (Oct 2), Diwali, Holi, Christmas, plus Panipat region observances (to be confirmed with site).
- **Project calendar** created under the standard GLOBAL calendar as parent, so resource calendars inherit.

---

## 8. Contract terms

Extractable from PDF header; full T&C not included in this BOQ document (would be in main contract/GCC).

| Field | Value | Source |
|---|---|---|
| Contract No. | 70143247 | Header |
| Tender | PT-09 | Header |
| Contract value (pre-tax) | ₹ 18,93,70,825.01 | Footer "WORK ORDER NET VALUE" |
| Contract value (incl. 18 % GST) | ₹ 22,34,57,573.03 | Header |
| Currency | INR | Explicit |
| Contract type | **Unit-rate (measured)** — payments against measured quantity × BOQ rate; EPPM `ContractType` = `ITEM_RATE_FIDIC_RED` (closest match in enum) | BOQ structure |
| Start date | — (assumed 2024-08-01; actual LOA date not in PDF) | **Gap** |
| Completion date | 11 months from start = assumed 2025-06-30 | Derived from "Completion period: 11 Months" |
| LD rate | — Not stated in BOQ doc | **Gap** (check main contract) |
| DLP (Defect Liability Period) | — Not stated | **Gap** |
| Retention / PBG / advance | — Not stated | **Gap** |
| Billing portal | IndianOil eVIDIT (`https://apps.indianoil.in/vim`) | Footer |

EPPM Contract entity should be populated with what we have and leave the gaps explicitly `null`.

---

## 9. Risks / site constraints

From BOQ notes & dismantling clause:

1. **Dismantling scheduling risk** — dismantling cannot happen in one go; IOCL hands over facilities progressively (operations can't stop entirely). Schedule must be phased with IOCL sign-off per phase.
2. **Wastage cap risk** — dismantling wastage > 20 % is penalised; tight material handling needed.
3. **Insulation disposal compliance** — old insulation must go to a recycling agency with certificate submitted to IOCL.
4. **Free-issue material handling** — IOCL supplies CS pipes; vendor shifts to site "anywhere within depot" at no extra cost (handling risk).
5. **Hydrotest pressure** — 1.5× design pressure for min 4 hrs on all piping; DP (dye penetration) testing on joints per approved QAP. Any joint failure re-does entire section.
6. **IOCL clearance/permission risk** — every dismantling step needs clearance from IOCL operations, maintenance, safety teams (3 sign-offs, any can delay).
7. **Material identification risk** — dismantled pipes/fittings must retain material ID marks for re-use at same location; damage = re-procurement at vendor cost.
8. **Scope exclusions** — no commissioning support, testing services, or operator training listed (check with EIC if required).
9. **Weather / monsoon** — August start in North India enters monsoon (Jul–Sep); civil earthworks affected. Schedule civil early work before monsoon.
10. **Concurrent operations** — refinery is live (ISO-accredited running plant); hot work permits needed for all welding.

---

## 10. Gaps (fields EPPM requires but the PDF doesn't state)

| Field | EPPM location | Seed default |
|---|---|---|
| LOA / WO notification date | `contract.loa_date` | 2024-07-19 (same as WO date) |
| Actual mobilisation / project start date | `project.plannedStartDate` | 2024-08-01 |
| Activity-level durations & dependencies | `activities`, `relationships` | Synthesised in §4 |
| LD rate | `contract.ldRate` | null — UDF placeholder |
| DLP months | `contract.dlpMonths` | null — UDF placeholder |
| Retention %, PBG %, mobilisation advance % | UDF fields on Contract | null placeholders |
| Resource identities, named individuals | `resources` | Synthesised in §6 |
| Resource rates (hourly/daily) | `resource_rates` | Indicative values in §6 |
| Holiday calendar specifics | `calendar_exceptions` | India statutory list in §7 |
| Risk register entries | `risks` | Populated from §9 |
| Earned value baseline | `baselines` | Created as PRIMARY after CPM schedule runs |

Handled in seed by: creating UDFs for contract terms (`ld_rate`, `dlp_months`, `retention_pct`, `pbg_pct`, `mob_advance_pct`, `loa_date`) with null/placeholder values so the user can fill from the actual contract document later.

---

## Appendix A — Signatories & contacts

- **IOCL Authorised Signatory:** Angom Rajen Singh, DGM (Engineering)
- **EIC:** Angom Rajen Singh — `ANGOMRAJEN@INDIANOIL.IN`
- **Site Engineer:** Indresh Kumar, Asst. Manager (Engg.)
- **Vendor MD / Contact:** DE'S TECHNICO LIMITED — `CALDTPL@GMAIL.COM`, +91 98300 62020

## Appendix B — Reference files

- PDF source: `~/Downloads/WO IOCL PANIPAT WORK ORDER 70143247 DEs tech.pdf`
- Extracted plaintext (dev/test only; not committed): `/tmp/iocl_wo.txt`
- BOQ TSV: `scripts/iocl-panipat-boq.tsv` (generated by `scripts/seed-iocl-panipat-wo.sh`)
