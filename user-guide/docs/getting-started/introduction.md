---
sidebar_position: 1
title: Introduction
description: Overview of Bipros EPPM — Enterprise Project Portfolio Management
---

# Introduction to Bipros EPPM

**Bipros EPPM** (Enterprise Project Portfolio Management) is a comprehensive web-based platform designed for planning, scheduling, tracking, and controlling large-scale infrastructure and construction projects. It provides tools comparable to Oracle Primavera P6 for managing every aspect of the project lifecycle — from initial planning through execution and closeout.

## Who Is This For?

Bipros EPPM is built for organizations that manage complex capital projects, including:

- **Government agencies** overseeing infrastructure programmes (roads, railways, power, water, ICT)
- **Construction companies** managing multiple projects simultaneously
- **Engineering firms** that need CPM scheduling and earned value tracking
- **Programme management offices (PMOs)** requiring portfolio-level visibility

## Key Capabilities

| Capability | Description |
|---|---|
| **Project Scheduling** | Create activities, define dependencies, and compute the critical path using the Critical Path Method (CPM) |
| **Work Breakdown Structure (WBS)** | Organize project scope into a hierarchical decomposition of deliverables |
| **Earned Value Management (EVM)** | Track Planned Value (PV), Earned Value (EV), and Actual Cost (AC) with automated CPI/SPI calculations |
| **Resource Management** | Assign labour, equipment, and material resources to activities with calendar-aware availability |
| **Contract & Cost Tracking** | Manage contracts, Running Account (RA) bills, and monitor budget versus actual expenditure |
| **Risk Analysis** | Maintain a risk register and run Monte Carlo simulations for probabilistic schedule forecasting |
| **Multi-Tier Dashboards** | Four dashboard levels (Executive, Programme, Operational, Field) tailored to each stakeholder role |
| **GIS Integration** | Visualize project progress on interactive maps with satellite imagery overlays |
| **Government Integration** | Connect to Indian government systems including PFMS, GeM, CPPP, GSTN, and PARIVESH |
| **Reports & Analytics** | Generate S-curves, resource histograms, cash flow reports, and use natural-language queries for project insights |

## System Architecture

Bipros EPPM is a client-server application with:

- **Frontend** — A modern web application built with Next.js and React, accessible from any browser
- **Backend** — A Java-based server using Spring Boot, organized as a modular monolith with domain-driven design
- **Database** — PostgreSQL for reliable, transactional data storage

## User Roles

The system supports five predefined roles with varying levels of access:

| Role | Access Level |
|---|---|
| **Admin** | Full system access — user management, global settings, integrations, all project data |
| **Project Manager** | Complete control over assigned projects — scheduling, cost, resources, contracts |
| **Scheduler** | Activity and schedule management — create/edit activities, run CPM, manage baselines |
| **Resource Manager** | Resource allocation and tracking — assign resources, manage calendars, track utilization |
| **Viewer** | Read-only access to all project data — dashboards, reports, and analytics |

## Next Steps

- [Navigate the Application](./navigation) — Learn the sidebar, header, and page layout
- [Quick Start Guide](./quick-start) — Create your first project in five minutes
