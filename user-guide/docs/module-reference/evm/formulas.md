---
sidebar_position: 2
title: EVM — Complete Formula Reference
description: All EVM formulas with derivations, examples, and interpretations
---

# EVM — Complete Formula Reference

This page documents every formula used in Bipros EPPM's Earned Value Management calculations.

---

## Fundamental Values

### Planned Value (PV)

$$PV = \sum_{i=1}^{n} (\text{Budget}_i \times \text{Planned \% Complete}_i)$$

| Variable | Definition |
|---|---|
| $Budget_i$ | Budget allocated to activity $i$ |
| $Planned \% Complete_i$ | Planned completion percentage of activity $i$ as of the status date |

**Example:**
- Activity A: Budget = ₹10,00,000, Planned % = 50%
- Activity B: Budget = ₹5,00,000, Planned % = 100%

$$PV = (10,00,000 \times 0.50) + (5,00,000 \times 1.00) = 5,00,000 + 5,00,000 = ₹10,00,000$$

**Interpretation:** PV represents the value of work that *should have been* completed by the status date.

---

### Earned Value (EV)

$$EV = \sum_{i=1}^{n} (\text{Budget}_i \times \text{Actual \% Complete}_i)$$

| Variable | Definition |
|---|---|
| $Actual \% Complete_i$ | Actual physical completion percentage of activity $i$ |

**Example:**
- Activity A: Budget = ₹10,00,000, Actual % = 40%
- Activity B: Budget = ₹5,00,000, Actual % = 100%

$$EV = (10,00,000 \times 0.40) + (5,00,000 \times 1.00) = 4,00,000 + 5,00,000 = ₹9,00,000$$

**Interpretation:** EV represents the value of work that *has actually been* completed.

---

### Actual Cost (AC)

$$AC = \sum_{i=1}^{n} \text{Actual Cost}_i$$

**Example:**
- Activity A actual cost = ₹4,50,000
- Activity B actual cost = ₹5,20,000

$$AC = 4,50,000 + 5,20,000 = ₹9,70,000$$

**Interpretation:** AC is the total cost actually incurred for the work performed.

---

## Variance Indicators

### Cost Variance (CV)

$$CV = EV - AC$$

**Example:**
- $EV = ₹9,00,000$
- $AC = ₹9,70,000$

$$CV = 9,00,000 - 9,70,000 = -₹70,000$$

| CV Value | Interpretation |
|---|---|
| $CV > 0$ | Under budget (favourable) |
| $CV = 0$ | On budget |
| $CV < 0$ | Over budget (unfavourable) |

---

### Schedule Variance (SV)

$$SV = EV - PV$$

**Example:**
- $EV = ₹9,00,000$
- $PV = ₹10,00,000$

$$SV = 9,00,000 - 10,00,000 = -₹1,00,000$$

| SV Value | Interpretation |
|---|---|
| $SV > 0$ | Ahead of schedule (favourable) |
| $SV = 0$ | On schedule |
| $SV < 0$ | Behind schedule (unfavourable) |

---

## Performance Indices

### Cost Performance Index (CPI)

$$CPI = \frac{EV}{AC}$$

**Example:**
- $EV = ₹9,00,000$
- $AC = ₹9,70,000$

$$CPI = \frac{9,00,000}{9,70,000} = 0.93$$

| CPI Value | Interpretation |
|---|---|
| $CPI > 1.0$ | Under budget (efficient) |
| $CPI = 1.0$ | On budget |
| $CPI < 1.0$ | Over budget (inefficient) |

---

### Schedule Performance Index (SPI)

$$SPI = \frac{EV}{PV}$$

**Example:**
- $EV = ₹9,00,000$
- $PV = ₹10,00,000$

$$SPI = \frac{9,00,000}{10,00,000} = 0.90$$

| SPI Value | Interpretation |
|---|---|
| $SPI > 1.0$ | Ahead of schedule |
| $SPI = 1.0$ | On schedule |
| $SPI < 1.0$ | Behind schedule |

---

## Forecasting Metrics

### Budget at Completion (BAC)

$$BAC = \sum_{i=1}^{n} \text{Budget}_i$$

**Example:**
- Total project budget = ₹50,00,000

$$BAC = ₹50,00,000$$

**Interpretation:** BAC is the total authorised budget for the entire project.

---

### Estimate at Completion (EAC)

Bipros EPPM supports multiple EAC methods:

#### Method 1: EAC = BAC / CPI (Most Common)

$$EAC = \frac{BAC}{CPI}$$

Assumption: Current cost performance will continue.

**Example:**
- $BAC = ₹50,00,000$
- $CPI = 0.93$

$$EAC = \frac{50,00,000}{0.93} = ₹53,76,344$$

#### Method 2: EAC = AC + (BAC - EV)

$$EAC = AC + (BAC - EV)$$

Assumption: Future work will be performed at budgeted rates (past variances are anomalies).

**Example:**
- $AC = ₹9,70,000$
- $BAC = ₹50,00,000$
- $EV = ₹9,00,000$

$$EAC = 9,70,000 + (50,00,000 - 9,00,000) = ₹50,70,000$$

#### Method 3: EAC = AC + ETC

$$EAC = AC + ETC$$

Where ETC is an independently derived estimate.

---

### Estimate to Complete (ETC)

#### Method 1: ETC = (BAC - EV) / CPI

$$ETC = \frac{BAC - EV}{CPI}$$

Assumption: Current cost performance continues.

**Example:**
- $BAC = ₹50,00,000$
- $EV = ₹9,00,000$
- $CPI = 0.93$

$$ETC = \frac{50,00,000 - 9,00,000}{0.93} = \frac{41,00,000}{0.93} = ₹44,08,602$$

#### Method 2: ETC = BAC - EV

$$ETC = BAC - EV$$

Assumption: Future work at budgeted rates.

**Example:**

$$ETC = 50,00,000 - 9,00,000 = ₹41,00,000$$

---

### Variance at Completion (VAC)

$$VAC = BAC - EAC$$

**Example (using Method 1 EAC):**
- $BAC = ₹50,00,000$
- $EAC = ₹53,76,344$

$$VAC = 50,00,000 - 53,76,344 = -₹3,76,344$$

| VAC Value | Interpretation |
|---|---|
| $VAC > 0$ | Expected under-run |
| $VAC = 0$ | Expected on-budget completion |
| $VAC < 0$ | Expected over-run |

---

### To-Complete Performance Index (TCPI)

TCPI measures the cost performance required for the remaining work to meet a financial goal.

#### TCPI based on BAC

$$TCPI_{BAC} = \frac{BAC - EV}{BAC - AC}$$

**Example:**
- $BAC = ₹50,00,000$
- $EV = ₹9,00,000$
- $AC = ₹9,70,000$

$$TCPI_{BAC} = \frac{50,00,000 - 9,00,000}{50,00,000 - 9,70,000} = \frac{41,00,000}{40,30,000} = 1.02$$

**Interpretation:** A TCPI of 1.02 means the remaining work must be performed at 102% of the budgeted cost efficiency to finish on budget.

#### TCPI based on EAC

$$TCPI_{EAC} = \frac{BAC - EV}{EAC - AC}$$

**Example:**
- $EAC = ₹53,76,344$

$$TCPI_{EAC} = \frac{41,00,000}{53,76,344 - 9,70,000} = \frac{41,00,000}{44,06,344} = 0.93$$

**Interpretation:** A TCPI of 0.93 means the remaining work can be performed at 93% of budgeted efficiency and still meet the EAC forecast.

| TCPI Value | Interpretation |
|---|---|
| $TCPI > 1.0$ | Harder to achieve than original plan |
| $TCPI = 1.0$ | Must perform exactly as originally planned |
| $TCPI < 1.0$ | Easier to achieve than original plan |

---

## Complete Example

**Project Data:**
- BAC = ₹50,00,000
- Status Date: End of Month 3
- Activity A (₹10L budget): Planned 50%, Actual 40%, Actual Cost ₹4.5L
- Activity B (₹5L budget): Planned 100%, Actual 100%, Actual Cost ₹5.2L
- Activity C (₹35L budget): Planned 20%, Actual 15%, Actual Cost ₹6.0L

**Calculations:**

$$PV = (10 \times 0.50) + (5 \times 1.00) + (35 \times 0.20) = 5 + 5 + 7 = ₹17,00,000$$

$$EV = (10 \times 0.40) + (5 \times 1.00) + (35 \times 0.15) = 4 + 5 + 5.25 = ₹14,25,000$$

$$AC = 4.5 + 5.2 + 6.0 = ₹15,70,000$$

$$CV = 14,25,000 - 15,70,000 = -₹1,45,000 \text{ (Over budget)}$$

$$SV = 14,25,000 - 17,00,000 = -₹2,75,000 \text{ (Behind schedule)}$$

$$CPI = \frac{14,25,000}{15,70,000} = 0.91 \text{ (Over budget)}$$

$$SPI = \frac{14,25,000}{17,00,000} = 0.84 \text{ (Behind schedule)}$$

$$EAC = \frac{50,00,000}{0.91} = ₹54,94,505$$

$$ETC = 54,94,505 - 15,70,000 = ₹39,24,505$$

$$VAC = 50,00,000 - 54,94,505 = -₹4,94,505 \text{ (Expected over-run)}$$

$$TCPI_{BAC} = \frac{50,00,000 - 14,25,000}{50,00,000 - 15,70,000} = \frac{35,75,000}{34,30,000} = 1.04$$
