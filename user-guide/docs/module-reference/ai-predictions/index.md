---
sidebar_position: 1
title: AI & Predictions — Deep Dive
description: Technical reference for AI-powered insights, schedule health, and predictive analytics
---

# AI & Predictions — Deep Dive

:::tip Looking for the conversational assistant?
This page covers **predictive analytics** (schedule health scoring, cost forecasting, trend analysis). For the **chat-first AI assistant**, document-aware WBS / activity generation, MDX-narrated insight panels, agentic tools and cached insights, see **[AI Assistant — Deep Dive](../ai-assistant/)**.
:::

## Overview

The AI module provides predictive analytics including schedule health scoring, risk predictions, cost forecasting, and capacity utilisation insights.

## Actors & Roles

| Actor | Role |
|---|---|
| **Project Manager** | Reviews predictions, takes preventive action |
| **Executive** | Views high-level predictive dashboards |
| **System** | Auto-generates predictions based on project data |

## Use Cases

### UC-AI-01: View Schedule Health Score

| Attribute | Value |
|---|---|
| **ID** | UC-AI-01 |
| **Name** | View Schedule Health Score |
| **Actor** | Project Manager |
| **Precondition** | Project has schedule and baseline |
| **Trigger** | User opens Schedule Health dashboard |

**Main Flow:**
1. System analyses schedule metrics (float, critical path, logic density)
2. System calculates health score (0–100)
3. System displays score with colour coding
4. System lists specific issues and recommendations

**Postcondition:** Health score is displayed |

### UC-AI-02: View Cost Prediction

| Attribute | Value |
|---|---|
| **ID** | UC-AI-02 |
| **Name** | View Cost Prediction |
| **Actor** | Cost Engineer |
| **Precondition** | EVM data exists |
| **Trigger** | User opens Predictions tab |

**Main Flow:**
1. System retrieves current EVM metrics
2. System applies trend analysis
3. System forecasts final project cost
4. System shows confidence interval

**Postcondition:** Cost prediction is displayed |

## Schedule Health Scoring

The schedule health score is derived from multiple factors:

| Factor | Weight | Description |
|---|---|---|
| **Critical Path Length** | 20% | Longer critical paths = lower score |
| **Float Distribution** | 20% | Uneven float = lower score |
| **Logic Density** | 15% | Too many/few dependencies = lower score |
| **Constraint Usage** | 15% | Hard constraints = lower score |
| **Milestone Alignment** | 15% | Missed milestones = lower score |
| **Resource Loading** | 15% | Over-allocation = lower score |

$$\text{Health Score} = \sum_{i=1}^{n} (\text{Factor Score}_i \times \text{Weight}_i)$$

| Score | Rating | Action |
|---|---|---|
| 80–100 | Healthy | Monitor |
| 60–79 | At Risk | Review schedule |
| 40–59 | Unhealthy | Restructure schedule |
| 0–39 | Critical | Immediate intervention |

## Prediction Models

### Cost Forecast

Uses current CPI and SPI to forecast final cost:

$$\text{Predicted EAC} = f(CPI_{trend}, SPI_{trend}, \text{Remaining Work})$$

### Schedule Forecast

Uses current SPI and critical path analysis:

$$\text{Predicted Finish} = \text{Baseline Finish} + \text{Delay Projection}$$

## Related Modules

- [Predictions](../../projects/predictions)
- [Schedule Health](../../projects/schedule-health)
- [EVM](../evm/)
- [Risk](../../risk/overview)
