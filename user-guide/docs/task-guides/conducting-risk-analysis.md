---
sidebar_position: 8
title: Conducting Risk Analysis
description: How to identify, assess, and analyse project risks using qualitative and quantitative methods
---

# Conducting Risk Analysis

This guide covers the complete risk management workflow: identifying risks, assessing probability and impact, running Monte Carlo simulations, and developing mitigation plans.

## Prerequisites

- Project has a baseline schedule
- Risk categories and scoring matrix are configured
- You have `RISK_UPDATE` permission (Risk Manager or Project Manager)

## Step 1: Identify Risks

1. Navigate to your project
2. Click the **Risk** tab
3. Click **New Risk**
4. Enter risk details:

| Field | Description |
|---|---|
| **Risk ID** | Auto-generated or manual (e.g., `R-001`) |
| **Risk Name** | Short descriptive name |
| **Description** | Detailed explanation of the risk event |
| **Category** | e.g., Technical, Financial, External, Organisational |
| **Trigger** | Event or condition that indicates the risk is occurring |
| **Impact Activities** | Which schedule activities are affected |

## Step 2: Qualitative Assessment

Rate each risk on the **Probability × Impact** matrix:

### Probability Scale

| Rating | Probability Range | Description |
|---|---|---|
| 1 — Very Low | < 10% | Highly unlikely |
| 2 — Low | 10–30% | Unlikely but possible |
| 3 — Medium | 30–50% | Possible |
| 4 — High | 50–70% | Likely |
| 5 — Very High | > 70% | Highly likely |

### Impact Scale (Schedule)

| Rating | Impact | Description |
|---|---|---|
| 1 — Very Low | < 1 week delay | Negligible impact |
| 2 — Low | 1–2 weeks delay | Minor impact |
| 3 — Medium | 2–4 weeks delay | Moderate impact |
| 4 — High | 1–2 months delay | Significant impact |
| 5 — Very High | > 2 months delay | Critical impact |

### Risk Score Calculation

$$\text{Risk Score} = \text{Probability Rating} \times \text{Impact Rating}$$

| Score | Priority | Action Required |
|---|---|---|
| 1–4 | Low | Monitor periodically |
| 5–9 | Medium | Develop mitigation plan |
| 10–16 | High | Immediate action required |
| 17–25 | Critical | Escalate to senior management |

## Step 3: Assign Risk Response

For each risk, select a response strategy:

| Strategy | When to Use |
|---|---|
| **Avoid** | Eliminate the risk by changing the plan |
| **Mitigate** | Reduce probability or impact |
| **Transfer** | Shift impact to a third party (e.g., insurance) |
| **Accept** | Acknowledge and monitor (for low scores) |

Document the **Mitigation Plan**:
- Specific actions to take
- Responsible person
- Target date
- Budget allocation (if any)

## Step 4: Quantitative Analysis (Monte Carlo)

For high-priority risks or complex projects, run a Monte Carlo simulation:

### Step 4a: Define Distributions

For each risk-affected activity, define:
- **Optimistic Duration** ($O$) — Best-case scenario
- **Most Likely Duration** ($M$) — Normal scenario
- **Pessimistic Duration** ($P$) — Worst-case scenario

### Step 4b: Run Simulation

1. Go to **Risk > Monte Carlo Simulation**
2. Select number of iterations (recommended: 1,000–10,000)
3. Click **Run Simulation**

### Step 4c: Interpret Results

The simulation produces:

| Metric | Description |
|---|---|
| **P50** | 50% probability the project finishes by this date |
| **P80** | 80% probability the project finishes by this date |
| **P90** | 90% probability the project finishes by this date |

### PERT Estimate

$$\text{Expected Duration} = \frac{O + 4M + P}{6}$$

$$\text{Standard Deviation} = \frac{P - O}{6}$$

## Step 5: Monitor and Update

1. Review the **Risk Register** regularly (weekly recommended)
2. Update risk status:
   - **Active** — Risk is present and being monitored
   - **Triggered** — Risk event has occurred
   - **Mitigated** — Risk has been successfully addressed
   - **Closed** — Risk is no longer applicable

## Expected Outcome

- Complete risk register with qualitative scores
- Monte Carlo results showing project finish date distribution
- Mitigation plans for all high-priority risks
- Regular risk monitoring process established

## Related Documentation

- [Risk Overview](../risk/overview)
- [Risk Analysis](../projects/risk-analysis)
