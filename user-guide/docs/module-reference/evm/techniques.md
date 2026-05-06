---
sidebar_position: 3
title: EVM — Measurement Techniques
description: How EV is calculated using different percent-complete techniques
---

# EVM — Measurement Techniques

Bipros EPPM supports multiple techniques for calculating **Earned Value (EV)**. The technique is selected per activity based on the nature of the work.

---

## Technique Comparison

| Technique | When to Use | Formula | Best For |
|---|---|---|---|
| **0/100** | Full credit only at completion | $EV = 0$ until 100% complete | Short activities, deliverables |
| **50/50** | 50% credit at start, 50% at finish | $EV = 0.5 \times Budget$ at start, full at finish | Medium activities |
| **Percent Complete** | Proportional to physical progress | $EV = Budget \times Actual \%$ | Long activities with measurable progress |
| **Level of Effort (LOE)** | Spread evenly over duration | $EV = PV$ for the period | Support activities, management |

---

## 0/100 Technique

**Concept:** No earned value is recognised until the activity is 100% complete.

$$EV = \begin{cases} 0 & \text{if } Actual \% < 100\% \\ Budget & \text{if } Actual \% = 100\% \end{cases}$$

**Example:**
- Budget = ₹5,00,000
- Actual % = 75%

$$EV = ₹0$$

**Best For:**
- Short-duration activities (< 2 weeks)
- Activities with clear completion criteria
- Deliverables that provide no partial value

**Advantages:**
- Simple to apply
- Conservative — no premature revenue recognition

**Disadvantages:**
- Can show severe schedule variance for long activities
- Not suitable for activities with incremental value

---

## 50/50 Technique

**Concept:** 50% of the budget is earned when the activity starts, and the remaining 50% when it finishes.

$$EV = \begin{cases} 0 & \text{Not started} \\ 0.5 \times Budget & \text{In progress} \\ Budget & \text{Complete} \end{cases}$$

**Example:**
- Budget = ₹10,00,000
- Status: In progress (30% physical complete)

$$EV = 0.5 \times 10,00,000 = ₹5,00,000$$

**Best For:**
- Medium-duration activities (2–4 weeks)
- Activities where start and finish are significant milestones

**Advantages:**
- Better than 0/100 for medium activities
- Reduces schedule variance volatility

**Disadvantages:**
- Can overstate or understate true progress
- Not suitable for activities with non-linear progress

---

## Percent Complete Technique

**Concept:** EV is proportional to the physical completion percentage.

$$EV = Budget \times Actual \% Complete$$

**Example:**
- Budget = ₹20,00,000
- Actual % = 35%

$$EV = 20,00,000 \times 0.35 = ₹7,00,000$$

**Best For:**
- Long-duration activities (> 1 month)
- Activities with measurable physical progress (e.g., concrete poured, earthwork excavated)

**Advantages:**
- Most accurate representation of progress
- Matches physical reality

**Disadvantages:**
- Requires reliable progress measurement
- Subject to subjective estimation errors

### Measuring Percent Complete

Methods for determining actual % complete:

| Method | Description | Example |
|---|---|---|
| **Units Complete** | Quantity completed / Total quantity | 500 cum poured / 2000 cum total = 25% |
| **Milestone Weights** | Pre-defined weights at milestones | Foundation = 30%, Walls = 50%, Finish = 20% |
| **Effort Hours** | Hours spent / Total estimated hours | 200 hours / 800 hours = 25% |
| **Opinion** | Expert judgement | Project manager's estimate |

---

## Level of Effort (LOE)

**Concept:** EV is earned equal to PV for the period. LOE activities are assumed to progress exactly as planned.

$$EV_{LOE} = PV_{LOE}$$

**Example:**
- LOE Budget = ₹12,00,000 over 12 months
- Monthly PV = ₹1,00,000
- At Month 3: $EV = 3 \times 1,00,000 = ₹3,00,000$

**Best For:**
- Project management activities
- Supervision and coordination
- Support functions that occur uniformly over time

**Advantages:**
- No progress measurement required
- Eliminates artificial schedule variance

**Disadvantages:**
- Can mask true performance issues
- Should not be used for direct production work

---

## Technique Selection Guide

```
Is the activity < 2 weeks?
├── Yes → 0/100
└── No → Is the activity 2–4 weeks?
    ├── Yes → 50/50
    └── No → Can progress be measured objectively?
        ├── Yes → Percent Complete
        └── No → Level of Effort (if support activity)
                  or Percent Complete with milestone weights
```

---

## Rollup Rules

When activities with different techniques roll up to WBS nodes:

1. **EV is summed** at each level regardless of technique
2. **PV is summed** at each level
3. **AC is summed** at each level
4. **Derived metrics (CPI, SPI)** are recalculated at each level using the rolled-up values

This ensures that mixed techniques do not distort higher-level metrics.

---

## Configuration

System Administrators can set default techniques per:
- **Activity Type** — Task Dependent, Resource Dependent, Milestone
- **WBS Category** — Foundation, Structure, Finishes, etc.
- **Project Template** — Pre-configured for new projects

Individual activities can override the default technique in their settings.
