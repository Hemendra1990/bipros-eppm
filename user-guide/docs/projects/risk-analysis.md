---
sidebar_position: 18
title: Risk Analysis
description: Project-level risk register and Monte Carlo simulation
---

# Risk Analysis

The **Risk Analysis** module provides project-level risk management, including a risk register for tracking identified risks and a **Monte Carlo simulation** engine for probabilistic schedule forecasting.

## Accessing Risk Analysis

Navigate to a project, then select **Risk Analysis** from the **More** dropdown menu.

![Risk Analysis](/img/screenshots/31-project-risk-analysis.png)

## Risk Register

The risk register logs all identified project risks:

| Column | Description |
|---|---|
| **Risk ID** | Unique identifier for the risk |
| **Description** | What could go wrong — clear description of the risk event |
| **Category** | Risk classification (Technical, Schedule, Cost, Environmental, etc.) |
| **Probability** | Likelihood of occurrence (Low, Medium, High) |
| **Impact** | Severity if the risk materializes (Low, Medium, High, Critical) |
| **Risk Score** | Probability × Impact — used for prioritization |
| **Owner** | Person responsible for monitoring and mitigating this risk |
| **Mitigation Strategy** | Planned response to reduce probability or impact |
| **Status** | Open, Mitigated, Closed, or Realized |

## Monte Carlo Simulation

The Monte Carlo simulation uses random sampling to model schedule uncertainty and predict the probability of meeting deadlines.

### How It Works

1. The simulation takes each activity's duration and applies a probability distribution (optimistic, most likely, pessimistic)
2. It runs thousands of iterations (default: **10,000**), each time randomly sampling durations from these distributions
3. For each iteration, it recalculates the project finish date using CPM logic
4. The results show a **probability distribution** of possible project finish dates

### Running a Simulation

1. Click **Run Monte Carlo** (or **Run Simulation**)
2. Configure simulation parameters:

| Parameter | Default | Description |
|---|---|---|
| **Iterations** | 10,000 | Number of random simulation runs |
| **Distribution Type** | Triangular | Statistical distribution for duration uncertainty |
| **Confidence Level** | 80% | Target probability level for finish date prediction |

3. Click **Run**
4. Wait for the simulation to complete (may take a few seconds for large projects)

### Understanding Results

The simulation produces:

| Output | Description |
|---|---|
| **Probability Histogram** | Bar chart showing frequency of finish dates — taller bars = more likely outcomes |
| **Cumulative S-Curve** | Cumulative probability curve — find any confidence level on the Y-axis and read the corresponding date |
| **P50 Date** | 50% probability finish date — there is a 50/50 chance of finishing by this date |
| **P80 Date** | 80% probability finish date — 80% confidence of finishing by this date |
| **P90 Date** | 90% probability finish date — high confidence |
| **Baseline Duration** | The deterministic CPM finish date for comparison |

### Interpreting Results

- **Large spread** between P50 and P90 → High uncertainty in the schedule
- **P50 much later than baseline** → The deterministic schedule is optimistic
- **P90 close to baseline** → The schedule has adequate buffers

## Abbreviations

| Abbreviation | Full Form |
|---|---|
| CPM | Critical Path Method |
| P50 | 50th Percentile (50% probability) |
| P80 | 80th Percentile (80% probability) |
| P90 | 90th Percentile (90% probability) |
