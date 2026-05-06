---
sidebar_position: 1
title: Formula Reference Sheet
description: Quick reference for all formulas used in Bipros EPPM
---

# Formula Reference Sheet

## Earned Value Management (EVM)

| Metric | Formula | Interpretation |
|---|---|---|
| **PV** | $PV = \sum (Budget_i \times Planned \%_i)$ | Value of work that should be done |
| **EV** | $EV = \sum (Budget_i \times Actual \%_i)$ | Value of work actually done |
| **AC** | $AC = \sum Actual Cost_i$ | Cost actually incurred |
| **CV** | $CV = EV - AC$ | >0 = under budget |
| **SV** | $SV = EV - PV$ | >0 = ahead of schedule |
| **CPI** | $CPI = EV / AC$ | >1.0 = under budget |
| **SPI** | $SPI = EV / PV$ | >1.0 = ahead of schedule |
| **BAC** | $BAC = \sum Budget_i$ | Total project budget |
| **EAC** | $EAC = BAC / CPI$ | Forecast total cost |
| **ETC** | $ETC = EAC - AC$ | Expected remaining cost |
| **VAC** | $VAC = BAC - EAC$ | Expected cost variance at completion |
| **TCPI** | $TCPI = (BAC - EV) / (BAC - AC)$ | Required future efficiency |

## CPM Scheduling

| Metric | Formula | Interpretation |
|---|---|---|
| **ES** | $ES_i = \max(EF_j + lag)$ | Earliest start |
| **EF** | $EF_i = ES_i + Duration_i$ | Earliest finish |
| **LS** | $LS_i = LF_i - Duration_i$ | Latest start |
| **LF** | $LF_i = \min(LS_k - lag)$ | Latest finish |
| **TF** | $TF_i = LS_i - ES_i$ | Total float (0 = critical) |
| **FF** | $FF_i = \min(ES_k) - EF_i - lag$ | Free float |

## PERT Estimation

| Metric | Formula |
|---|---|
| **Expected Duration** | $E = (O + 4M + P) / 6$ |
| **Standard Deviation** | $\sigma = (P - O) / 6$ |
| **Variance** | $\sigma^2 = [(P - O) / 6]^2$ |

## Risk

| Metric | Formula | Interpretation |
|---|---|---|
| **Risk Score** | $Score = Probability \times Impact$ | Higher = more severe |
| **EMV** | $EMV = Probability \times Impact Value$ | Expected monetary value |

## Schedule Compression

| Metric | Formula |
|---|---|
| **Crash Cost Slope** | $(Crash Cost - Normal Cost) / (Normal Duration - Crash Duration)$ |

## Resource Management

| Metric | Formula | Interpretation |
|---|---|---|
| **Capacity Utilisation** | $(Allocated / Available) \times 100\%$ | >100% = over-allocated |
| **Uniform Curve** | $U_i = Q / n$ | Even distribution |
| **Front-Loaded** | $U_i = Q \times 2(n-i+1) / [n(n+1)]$ | Higher at start |

## Cost Management

| Metric | Formula |
|---|---|
| **RA Bill Amount** | $\sum (Quantity_j \times Rate_j)$ |
| **Net Payable** | $Gross - TDS - Retention - LD - Advance$ |
| **Budget Change** | $Current = Original + Increases - Decreases$ |
