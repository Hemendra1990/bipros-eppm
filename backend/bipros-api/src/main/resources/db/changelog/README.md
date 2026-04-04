# Liquibase Database Migration Files

This directory contains all Liquibase changelog files for the Bipros EPPM project database schema.

## File Structure

### Master Changelog
- **db.changelog-master.yaml** - Master changelog that includes all module changelogs in the correct order

### Schema Creation
- **001-create-schemas.yaml** - Creates all required PostgreSQL schemas

### Module Changelogs (in order)

1. **002-security-tables.yaml** - User and role management tables
   - `users` - User accounts with authentication
   - `roles` - Role definitions
   - `user_roles` - User-role assignments

2. **003-admin-tables.yaml** - Administrative configuration tables
   - `currencies` - Currency definitions
   - `units_of_measure` - Unit of measure definitions
   - `global_settings` - Global system settings

3. **004-project-tables.yaml** - Project structure tables
   - `eps_nodes` - Enterprise Project Structure nodes
   - `obs_nodes` - Organizational Breakdown Structure nodes
   - `projects` - Project master data
   - `project_codes` - Project code hierarchies
   - `wbs_nodes` - Work Breakdown Structure nodes

4. **005-calendar-tables.yaml** - Calendar and work schedule tables
   - `calendars` - Project and resource calendars
   - `calendar_exceptions` - Calendar holidays and exceptions
   - `calendar_work_weeks` - Weekly work schedule patterns

5. **006-activity-tables.yaml** - Activity and task management tables
   - `activities` - Project activities/tasks with schedule data
   - `activity_relationships` - Task dependencies and links
   - `activity_code_assignments` - Activity code assignments

6. **007-scheduling-tables.yaml** - Schedule calculation results
   - `schedule_results` - Project schedule calculation results
   - `schedule_activity_results` - Individual activity scheduling results

7. **008-resource-tables.yaml** - Resource management tables
   - `resources` - Resource definitions (labor, material, equipment)
   - `resource_rates` - Resource pricing by type and date
   - `resource_curves` - Resource loading curves
   - `resource_assignments` - Activity-resource assignments with costs

8. **009-udf-tables.yaml** - User-defined fields
   - `user_defined_fields` - Custom field definitions
   - `udf_values` - Custom field values for entities

9. **010-risk-tables.yaml** - Risk management tables
   - `risks` - Risk register entries
   - `risk_responses` - Risk response actions and mitigation

10. **011-reporting-tables.yaml** - Reporting infrastructure
    - `report_definitions` - Report template definitions
    - `report_executions` - Report execution history

11. **012-import-export-tables.yaml** - Data import/export
    - `import_export_jobs` - Import/export job tracking
    - `import_export_logs` - Detailed operation logs

## Base Entity Columns

All tables include the following audit columns (inherited from BaseEntity):
- `id` (UUID) - Primary key
- `created_at` (TIMESTAMP) - Record creation timestamp
- `updated_at` (TIMESTAMP) - Last update timestamp
- `created_by` (VARCHAR) - User who created the record
- `updated_by` (VARCHAR) - User who last updated the record
- `version` (BIGINT) - Optimistic locking version

## Column Type Mapping

| Java Type | SQL Type | Notes |
|-----------|----------|-------|
| UUID | UUID | Primary keys and foreign keys |
| String | VARCHAR(n) | Length varies by field |
| String (long) | TEXT | For descriptions and large text |
| LocalDate | DATE | Calendar dates |
| Instant | TIMESTAMP | Audit timestamps |
| Double | DOUBLE | Numeric values |
| BigDecimal | DECIMAL(19,4) | Monetary values with 4 decimal places |
| boolean | BOOLEAN | Flags and boolean values |
| Enum | VARCHAR(n) | Enumerated values as strings |

## Using These Migrations

1. Enable Liquibase in `application.yml`:
   ```yaml
   spring:
     liquibase:
       enabled: true
       change-log: classpath:db/changelog/db.changelog-master.yaml
   ```

2. Disable JPA auto DDL:
   ```yaml
   jpa:
     hibernate:
       ddl-auto: validate
   ```

3. On application startup, Liquibase will:
   - Create all schemas
   - Create all tables in dependency order
   - Add all indexes and constraints
   - Track migration history in `databasechangelog` table

## Migration Execution Order

Migrations execute in the order they are included in the master changelog:
1. Schemas are created first
2. Security/User tables
3. Admin configuration tables
4. Project structure tables
5. Calendar/scheduling tables
6. Activity/task tables
7. Schedule results
8. Resource tables with foreign keys to activities and projects
9. Custom fields (UDF)
10. Risk management
11. Reporting infrastructure
12. Import/Export infrastructure

This order ensures foreign key constraints can be satisfied.

## Database Schema Layout

```
public schema:
  - users
  - roles
  - user_roles
  - currencies
  - units_of_measure
  - global_settings
  - report_definitions
  - report_executions
  - import_export_jobs
  - import_export_logs

project schema:
  - eps_nodes
  - obs_nodes
  - projects
  - project_codes
  - wbs_nodes

scheduling schema:
  - calendars
  - calendar_exceptions
  - calendar_work_weeks
  - schedule_results
  - schedule_activity_results

activity schema:
  - activities
  - activity_relationships
  - activity_code_assignments

resource schema:
  - resources
  - resource_rates
  - resource_curves
  - resource_assignments

udf schema:
  - user_defined_fields
  - udf_values

risk schema:
  - risks
  - risk_responses
```

## Notes

- All migrations are idempotent (safe to run multiple times)
- UUID is used for all primary keys
- Indexes are created for frequently queried columns
- Foreign key constraints ensure referential integrity
- Unique constraints prevent duplicate data where required
- All changes are captured in the `databasechangelog` table for audit trail
