package com.bipros.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dev-only idempotent schema fixup runner.
 *
 * <p>Hibernate's {@code ddl-auto: update} mode (used in dev) reliably adds new columns and
 * tables but never alters CHECK constraints or column types when an entity changes. In prod
 * we rely on Liquibase to handle those mutations; in dev Liquibase is disabled by default so
 * "fast feedback when editing entities" works without an extra migration step.
 *
 * <p>This bean runs only when {@code spring.liquibase.enabled = false} (i.e. dev) and applies
 * a small list of known mutations idempotently on every boot. Each fixup is a no-op once the
 * schema is in the desired state, so it's safe to leave permanently — extending the list
 * costs nothing on healthy DBs.
 *
 * <p>Each entry mirrors a Liquibase changeset so prod and dev converge to the same shape:
 * <ul>
 *   <li>070 → {@code boq_items_status_check} CHECK includes {@code OVERRUN}</li>
 *   <li>071 → {@code ra_bill_items.description} is at least VARCHAR(500)</li>
 *   <li>108-4 → {@code daily_progress_reports.approval_status} backfilled to APPROVED for legacy rows</li>
 * </ul>
 *
 * <p>If a fixup fails (e.g. Postgres returns an unexpected error), it logs and continues —
 * we do not want a single fixup glitch to abort backend startup.
 */
@Configuration
@ConditionalOnProperty(name = "spring.liquibase.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DevSchemaFixupRunner {

  private final JdbcTemplate jdbcTemplate;

  @Bean
  public ApplicationRunner devSchemaFixups() {
    return args -> {
      log.info("[DevSchemaFixupRunner] running idempotent schema fixups (dev profile only)");
      ensureBoqStatusCheckIncludesOverrun();
      ensureProjectTeamRoleCheckIncludesNewSeats();
      ensureDprSideCheckIncludesNewSides();
      ensureRaBillItemDescriptionIsAtLeast500();
      ensureBoqItemDescriptionIsAtLeast2000();
      ensureActivityCodeIsAtLeast120();
      backfillDprApprovalStatus();
      backfillDprIssueClosedAt();
      backfillActivityBoqLinks();
      ensureDprBoqItemFk();
      ensureDprBoqOperationFk();
      repairGateAStoredVariance();
      backfillBoqActualCost();
      log.info("[DevSchemaFixupRunner] complete");
    };
  }

  /**
   * Fixup 070 — drop and recreate {@code boq_items_status_check} so it includes {@code OVERRUN}.
   * Only acts if the current constraint definition does not already mention {@code OVERRUN}.
   */
  private void ensureBoqStatusCheckIncludesOverrun() {
    try {
      String def = jdbcTemplate.queryForObject(
          """
          select pg_get_constraintdef(oid)
          from pg_constraint
          where conname = 'boq_items_status_check'
            and conrelid = 'project.boq_items'::regclass
          """,
          String.class);
      if (def == null) {
        log.warn("[DevSchemaFixupRunner] boq_items_status_check not found — skipping (table may not exist yet)");
        return;
      }
      if (def.contains("OVERRUN")) {
        log.debug("[DevSchemaFixupRunner] boq_items_status_check already includes OVERRUN — no-op");
        return;
      }
      jdbcTemplate.execute("ALTER TABLE project.boq_items DROP CONSTRAINT boq_items_status_check");
      jdbcTemplate.execute(
          "ALTER TABLE project.boq_items ADD CONSTRAINT boq_items_status_check "
              + "CHECK (status IN ('PENDING','ACTIVE','COMPLETED','OVERRUN','ON_HOLD'))");
      log.info("[DevSchemaFixupRunner] fixup 070 applied: boq_items_status_check now allows OVERRUN");
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 070 (boq_items_status_check) failed — continuing", e);
    }
  }

  /**
   * Fixup 122 — drop and recreate {@code project_team_role_check} so it admits the four seats
   * added for the client requirements workbook (PROJECT_CONTROL, QUALITY_ENGINEER, STORE_KEEPER,
   * DESIGN_COORDINATOR). Postgres does not widen a derived CHECK constraint under
   * {@code ddl-auto: update}, so without this a dev database rejects every insert of a new seat.
   * Only acts if the current definition does not already mention PROJECT_CONTROL.
   */
  private void ensureProjectTeamRoleCheckIncludesNewSeats() {
    try {
      String def = jdbcTemplate.queryForObject(
          """
          select pg_get_constraintdef(oid)
          from pg_constraint
          where conname = 'project_team_role_check'
            and conrelid = 'project.project_team'::regclass
          """,
          String.class);
      if (def == null) {
        log.warn("[DevSchemaFixupRunner] project_team_role_check not found — skipping (table may not exist yet)");
        return;
      }
      if (def.contains("PROJECT_CONTROL")) {
        log.debug("[DevSchemaFixupRunner] project_team_role_check already includes the new seats — no-op");
        return;
      }
      jdbcTemplate.execute("ALTER TABLE project.project_team DROP CONSTRAINT project_team_role_check");
      jdbcTemplate.execute(
          "ALTER TABLE project.project_team ADD CONSTRAINT project_team_role_check "
              + "CHECK (role IN ('PM','CONSTRUCTION_MANAGER','SITE_MANAGER','ENGINEER','SUPERVISOR',"
              + "'QS','SAFETY','PROJECT_CONTROL','QUALITY_ENGINEER','STORE_KEEPER','DESIGN_COORDINATOR'))");
      log.info("[DevSchemaFixupRunner] fixup 122 applied: project_team_role_check now allows the four new seats");
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 122 (project_team_role_check) failed — continuing", e);
    }
  }

  /**
   * Fixup — drop and recreate {@code daily_progress_reports_side_check} so it admits the six
   * corridor-element sides added from the client workbook (MEDIAN_LHS/RHS, MCW_LHS/RHS,
   * CDROAD_LHS/RHS). Long-lived dev databases carry a stale Hibernate-era constraint listing
   * only {@code LHS/RHS/BOTH} — it predates even CENTER, so saving a "Center" DPR already
   * failed in dev. Prod is unaffected (Liquibase 072 created the column with no constraint).
   * Only acts if the current definition does not already mention CDROAD_RHS.
   */
  private void ensureDprSideCheckIncludesNewSides() {
    try {
      String def = jdbcTemplate.queryForObject(
          """
          select pg_get_constraintdef(oid)
          from pg_constraint
          where conname = 'daily_progress_reports_side_check'
            and conrelid = 'project.daily_progress_reports'::regclass
          """,
          String.class);
      if (def == null) {
        log.debug("[DevSchemaFixupRunner] daily_progress_reports_side_check not found — no-op");
        return;
      }
      if (def.contains("CDROAD_RHS")) {
        log.debug("[DevSchemaFixupRunner] daily_progress_reports_side_check already current — no-op");
        return;
      }
      jdbcTemplate.execute(
          "ALTER TABLE project.daily_progress_reports DROP CONSTRAINT daily_progress_reports_side_check");
      jdbcTemplate.execute(
          "ALTER TABLE project.daily_progress_reports ADD CONSTRAINT daily_progress_reports_side_check "
              + "CHECK (side IN ('LHS','RHS','CENTER','MEDIAN_LHS','MEDIAN_RHS',"
              + "'MCW_LHS','MCW_RHS','CDROAD_LHS','CDROAD_RHS'))");
      log.info("[DevSchemaFixupRunner] fixup applied: daily_progress_reports_side_check now allows all Side values");
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      log.debug("[DevSchemaFixupRunner] daily_progress_reports_side_check absent — no-op (prod-parity schema)");
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup (daily_progress_reports_side_check) failed — continuing", e);
    }
  }

  /**
   * Fixup 071 — widen {@code ra_bill_items.description} to at least VARCHAR(500). Civil-works
   * BOQ descriptions routinely exceed the default Hibernate VARCHAR(255).
   */
  private void ensureRaBillItemDescriptionIsAtLeast500() {
    try {
      Integer currentLength = jdbcTemplate.queryForObject(
          """
          select character_maximum_length
          from information_schema.columns
          where table_schema = 'cost'
            and table_name = 'ra_bill_items'
            and column_name = 'description'
          """,
          Integer.class);
      if (currentLength == null) {
        log.warn("[DevSchemaFixupRunner] cost.ra_bill_items.description not found — skipping");
        return;
      }
      if (currentLength >= 500) {
        log.debug("[DevSchemaFixupRunner] ra_bill_items.description already >= 500 chars — no-op");
        return;
      }
      jdbcTemplate.execute(
          "ALTER TABLE cost.ra_bill_items ALTER COLUMN description TYPE VARCHAR(500)");
      log.info("[DevSchemaFixupRunner] fixup 071 applied: ra_bill_items.description widened from {} to 500",
          currentLength);
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 071 (ra_bill_items.description) failed — continuing", e);
    }
  }

  /**
   * Fixup 124 — widen {@code project.boq_items.description} to at least VARCHAR(2000). The
   * client's BOQ has descriptions up to ~760 chars. Mirrors Liquibase changeset 124 (prod);
   * ddl-auto:update never alters an existing column's type, so dev DBs need this at startup
   * (same reason fixup 071 exists for ra_bill_items).
   */
  private void ensureBoqItemDescriptionIsAtLeast2000() {
    try {
      Integer currentLength = jdbcTemplate.queryForObject(
          """
          select character_maximum_length
          from information_schema.columns
          where table_schema = 'project'
            and table_name = 'boq_items'
            and column_name = 'description'
          """,
          Integer.class);
      if (currentLength == null) {
        log.warn("[DevSchemaFixupRunner] project.boq_items.description not found — skipping");
        return;
      }
      if (currentLength >= 2000) {
        log.debug("[DevSchemaFixupRunner] boq_items.description already >= 2000 chars — no-op");
        return;
      }
      jdbcTemplate.execute(
          "ALTER TABLE project.boq_items ALTER COLUMN description TYPE VARCHAR(2000)");
      log.info("[DevSchemaFixupRunner] fixup 124 applied: boq_items.description widened from {} to 2000",
          currentLength);
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 124 (boq_items.description) failed — continuing", e);
    }
  }

  /**
   * Fixup 126 — widen {@code activity.activities.code} to at least VARCHAR(120). Dotted-path
   * activity codes (hierarchy design D11) overflow the original 20. Mirrors Liquibase
   * changeset 126 (prod); ddl-auto:update never alters an existing column's type.
   */
  private void ensureActivityCodeIsAtLeast120() {
    try {
      Integer currentLength = jdbcTemplate.queryForObject(
          """
          select character_maximum_length
          from information_schema.columns
          where table_schema = 'activity'
            and table_name = 'activities'
            and column_name = 'code'
          """,
          Integer.class);
      if (currentLength == null) {
        log.warn("[DevSchemaFixupRunner] activity.activities.code not found — skipping");
        return;
      }
      if (currentLength >= 120) {
        log.debug("[DevSchemaFixupRunner] activities.code already >= 120 chars — no-op");
        return;
      }
      jdbcTemplate.execute(
          "ALTER TABLE activity.activities ALTER COLUMN code TYPE VARCHAR(120)");
      log.info("[DevSchemaFixupRunner] fixup 126 applied: activities.code widened from {} to 120",
          currentLength);
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 126 (activities.code) failed — continuing", e);
    }
  }

  /**
   * Fixup 108-4 — backfill {@code daily_progress_reports.approval_status} to APPROVED for any
   * rows that are NULL or not yet APPROVED. Mirrors the Liquibase changeset 108-4 that runs in
   * prod; dev databases (ddl-auto: update, Liquibase disabled) need this applied at startup.
   * The UPDATE is idempotent — a no-op once all rows are APPROVED.
   */
  private void backfillDprApprovalStatus() {
    try {
      int updated = jdbcTemplate.update(
          """
          UPDATE project.daily_progress_reports
             SET approval_status = 'APPROVED',
                 submitted_at = COALESCE(submitted_at, created_at),
                 approved_at  = COALESCE(approved_at, updated_at)
           WHERE approval_status IS NULL OR approval_status <> 'APPROVED'
          """);
      if (updated > 0) {
        log.info("[DevSchemaFixupRunner] fixup 108-4 applied: backfilled {} DPR rows to APPROVED", updated);
      } else {
        log.debug("[DevSchemaFixupRunner] fixup 108-4: all DPR rows already APPROVED — no-op");
      }
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 108-4 (dpr approval_status backfill) failed — continuing", e);
    }
  }

  /**
   * Fixup 128 — backfill {@code activity.activities.boq_item_id} from each activity's own DPR
   * history (BOQ-link design L12). Only activities whose approved-DPR history names exactly
   * ONE distinct BOQ line are linked; mixed histories are counted and left for manual
   * resolution — never guessed. plannedQty is deliberately NOT set here: the sole-activity
   * default applies at link time in ActivityService, and the leaf-% change (Task 10) treats a
   * null plannedQty as "use the line quantity" — today's behaviour, so nothing moves.
   * Idempotent via the {@code boq_item_id IS NULL} guard. Mirrors Liquibase changeset 128.
   */
  private void backfillActivityBoqLinks() {
    try {
      int updated = jdbcTemplate.update(
          """
          UPDATE activity.activities a
             SET boq_item_id = s.only_item
            FROM (SELECT d.activity_id, MIN(d.boq_item_id::text)::uuid AS only_item
                    FROM project.daily_progress_reports d
                   WHERE d.boq_item_id IS NOT NULL
                     AND d.approval_status = 'APPROVED'
                   GROUP BY d.activity_id
                  HAVING COUNT(DISTINCT d.boq_item_id) = 1) s
           WHERE s.activity_id = a.id
             AND a.boq_item_id IS NULL
             AND NOT EXISTS (SELECT 1 FROM activity.activities c
                              WHERE c.parent_activity_id = a.id)
          """);
      Integer conflicts = jdbcTemplate.queryForObject(
          """
          SELECT count(*) FROM (
            SELECT d.activity_id
              FROM project.daily_progress_reports d
             WHERE d.boq_item_id IS NOT NULL AND d.approval_status = 'APPROVED'
             GROUP BY d.activity_id
            HAVING COUNT(DISTINCT d.boq_item_id) > 1) t
          """,
          Integer.class);
      if (updated > 0 || (conflicts != null && conflicts > 0)) {
        log.info("[DevSchemaFixupRunner] fixup 128 applied: linked {} activities to their BOQ line; "
                + "{} activities have MIXED BOQ history — left unlinked, resolve manually",
            updated, conflicts == null ? 0 : conflicts);
      } else {
        log.debug("[DevSchemaFixupRunner] fixup 128: no unlinked activities with BOQ history — no-op");
      }
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 128 (activity BOQ-link backfill) failed — continuing", e);
    }
  }

  /**
   * Fixup 130 — mirror of Liquibase changeset 130 (B5, Stage 4): FK
   * {@code daily_progress_reports.boq_item_id → boq_items(id) ON DELETE RESTRICT} so a BOQ line
   * referenced by DPRs cannot be deleted out from under them. Same orphan guard as prod: if
   * orphaned references exist the FK is skipped (logged) — the app-side guard in
   * {@code BoqService.rejectIfReferenced} still protects the delete path.
   */
  private void ensureDprBoqItemFk() {
    try {
      Integer exists = jdbcTemplate.queryForObject(
          """
          SELECT count(*) FROM pg_constraint
          WHERE conname = 'fk_dpr_boq_item'
            AND conrelid = 'project.daily_progress_reports'::regclass
          """,
          Integer.class);
      if (exists != null && exists > 0) {
        log.debug("[DevSchemaFixupRunner] fixup 130: fk_dpr_boq_item already present — no-op");
        return;
      }
      Integer orphans = jdbcTemplate.queryForObject(
          """
          SELECT count(*) FROM project.daily_progress_reports d
          WHERE d.boq_item_id IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM project.boq_items b WHERE b.id = d.boq_item_id)
          """,
          Integer.class);
      if (orphans != null && orphans > 0) {
        log.warn("[DevSchemaFixupRunner] fixup 130 skipped: {} DPR row(s) reference a deleted "
            + "BOQ item — clean them up before the FK can be added", orphans);
        return;
      }
      jdbcTemplate.execute(
          "ALTER TABLE project.daily_progress_reports ADD CONSTRAINT fk_dpr_boq_item "
              + "FOREIGN KEY (boq_item_id) REFERENCES project.boq_items(id) ON DELETE RESTRICT");
      log.info("[DevSchemaFixupRunner] fixup 130 applied: fk_dpr_boq_item added");
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 130 (fk_dpr_boq_item) failed — continuing", e);
    }
  }

  /**
   * Fixup 131 — mirror of Liquibase changeset 131: FK
   * {@code daily_progress_reports.boq_operation_id → boq_operations(id) ON DELETE RESTRICT}.
   * Closes the unsplit-vs-DPR-save race at the database so a dangling operation pointer (which
   * the income predicates would silently exclude) cannot survive.
   */
  private void ensureDprBoqOperationFk() {
    try {
      Integer exists = jdbcTemplate.queryForObject(
          """
          SELECT count(*) FROM pg_constraint
          WHERE conname = 'fk_dpr_boq_operation'
            AND conrelid = 'project.daily_progress_reports'::regclass
          """,
          Integer.class);
      if (exists != null && exists > 0) {
        log.debug("[DevSchemaFixupRunner] fixup 131: fk_dpr_boq_operation already present — no-op");
        return;
      }
      Integer orphans = jdbcTemplate.queryForObject(
          """
          SELECT count(*) FROM project.daily_progress_reports d
          WHERE d.boq_operation_id IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM project.boq_operations o WHERE o.id = d.boq_operation_id)
          """,
          Integer.class);
      if (orphans != null && orphans > 0) {
        log.warn("[DevSchemaFixupRunner] fixup 131 skipped: {} DPR row(s) reference a deleted "
            + "BOQ operation — clean them up before the FK can be added", orphans);
        return;
      }
      jdbcTemplate.execute(
          "ALTER TABLE project.daily_progress_reports ADD CONSTRAINT fk_dpr_boq_operation "
              + "FOREIGN KEY (boq_operation_id) REFERENCES project.boq_operations(id) ON DELETE RESTRICT");
      log.info("[DevSchemaFixupRunner] fixup 131 applied: fk_dpr_boq_operation added");
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 131 (fk_dpr_boq_operation) failed — continuing", e);
    }
  }

  /**
   * Fixup 132 — mirror of Liquibase changeset 132: Gate A (calc-log entry 8) capped the
   * earned-budget basis of {@code cost_variance}, but it is a STORED column only recomputed on
   * the line's next write. Re-derive it once for the rows the cap moves (over-executed unsplit
   * lines). Idempotent: re-running writes the same values.
   */
  private void repairGateAStoredVariance() {
    try {
      int updated = jdbcTemplate.update(
          """
          UPDATE project.boq_items SET
            cost_variance = ROUND(COALESCE(actual_amount,0)
              - (LEAST(COALESCE(qty_executed_to_date,0), boq_qty) * COALESCE(budgeted_rate,0)), 2),
            cost_variance_percent = CASE
              WHEN LEAST(COALESCE(qty_executed_to_date,0), boq_qty) * COALESCE(budgeted_rate,0) = 0 THEN NULL
              ELSE ROUND((COALESCE(actual_amount,0)
                - (LEAST(COALESCE(qty_executed_to_date,0), boq_qty) * COALESCE(budgeted_rate,0)))
                / (LEAST(COALESCE(qty_executed_to_date,0), boq_qty) * COALESCE(budgeted_rate,0)), 6)
            END
          WHERE boq_qty IS NOT NULL AND boq_qty > 0
            AND COALESCE(qty_executed_to_date,0) > boq_qty
            AND earned_fraction IS NULL
            AND cost_variance IS DISTINCT FROM ROUND(COALESCE(actual_amount,0)
              - (LEAST(COALESCE(qty_executed_to_date,0), boq_qty) * COALESCE(budgeted_rate,0)), 2)
          """);
      if (updated > 0) {
        log.info("[DevSchemaFixupRunner] fixup 132 applied: Gate A stored variance repaired on {} row(s)", updated);
      } else {
        log.debug("[DevSchemaFixupRunner] fixup 132: stored variances already on the capped basis — no-op");
      }
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 132 (Gate A stored variance) failed — continuing", e);
    }
  }

  /**
   * Fixup 141 — mirror of Liquibase changeset 141: seed {@code boq_items.actual_cost} for lines
   * that already have approved DPRs, and re-derive the columns that depend on it.
   *
   * <p>{@code actual_amount} used to be reconstructed as {@code qty_executed_to_date × actual_rate},
   * so a split line whose spend sat entirely on a non-measurement operation stored zero cost while
   * its earned budget was credited in full. The cost is now carried in its own column; this brings
   * existing rows onto that basis without waiting for their next DPR event.
   *
   * <p>Scope guards: only rows with at least one APPROVED DPR (a seeded line with no DPR history
   * keeps its workbook {@code qty × rate} amount), never {@code manual_override} rows (same rule
   * {@code BoqActualRateRecalcListener} applies), and only where {@code actual_cost IS NULL} — so
   * re-running is a no-op and it never overwrites a live roll-up.
   *
   * <p>The five cost legs and the Gate A earned-budget basis are copied verbatim from
   * {@code BoqActualCostQuery.sumActualCost} and {@code BoqCalculator.earnedBudget}; they must stay
   * identical or the BOQ tab disagrees with itself.
   */
  private void backfillBoqActualCost() {
    try {
      int updated = jdbcTemplate.update(
          """
          WITH c AS (
            SELECT b.id,
              ROUND(COALESCE((SELECT SUM(u.contrib) FROM (
                  SELECT m.line_cost AS contrib FROM project.dpr_manpower m
                    JOIN project.daily_progress_reports d ON m.dpr_id = d.id
                    WHERE d.boq_item_id = b.id AND d.approval_status = 'APPROVED'
                  UNION ALL
                  SELECT e.line_cost FROM project.dpr_equipment e
                    JOIN project.daily_progress_reports d ON e.dpr_id = d.id
                    WHERE d.boq_item_id = b.id AND d.approval_status = 'APPROVED'
                  UNION ALL
                  SELECT mt.line_cost FROM project.dpr_material mt
                    JOIN project.daily_progress_reports d ON mt.dpr_id = d.id
                    WHERE d.boq_item_id = b.id AND d.approval_status = 'APPROVED'
                  UNION ALL
                  SELECT COALESCE(mcl.line_cost,0) FROM resource.material_consumption_logs mcl
                    WHERE mcl.line_cost IS NOT NULL AND mcl.activity_id IN (
                      SELECT DISTINCT d2.activity_id FROM project.daily_progress_reports d2
                      WHERE d2.boq_item_id = b.id AND d2.activity_id IS NOT NULL
                        AND d2.approval_status = 'APPROVED')
                  UNION ALL
                  SELECT (sc.quantity * COALESCE(a.rate_per_unit,0)) FROM project.dpr_sub_contractor sc
                    JOIN project.daily_progress_reports d ON sc.dpr_id = d.id
                    LEFT JOIN resource.activity_sub_contractor_assignments a
                      ON a.id = sc.activity_sub_contractor_assignment_id
                    WHERE d.boq_item_id = b.id AND d.approval_status = 'APPROVED'
                ) u), 0), 2) AS cost,
              CASE
                WHEN b.earned_fraction IS NOT NULL
                  THEN b.earned_fraction * COALESCE(b.boq_qty,0) * COALESCE(b.budgeted_rate,0)
                WHEN COALESCE(b.boq_qty,0) = 0
                  THEN COALESCE(b.qty_executed_to_date,0) * COALESCE(b.budgeted_rate,0)
                ELSE LEAST(COALESCE(b.qty_executed_to_date,0), b.boq_qty) * COALESCE(b.budgeted_rate,0)
              END AS earned
            FROM project.boq_items b
            WHERE b.actual_cost IS NULL
              AND COALESCE(b.manual_override, FALSE) = FALSE
              AND EXISTS (SELECT 1 FROM project.daily_progress_reports d3
                          WHERE d3.boq_item_id = b.id AND d3.approval_status = 'APPROVED')
          )
          UPDATE project.boq_items t SET
            actual_cost = c.cost,
            actual_amount = c.cost,
            actual_rate = CASE WHEN COALESCE(t.qty_executed_to_date,0) = 0 THEN NULL
                               ELSE ROUND(c.cost / t.qty_executed_to_date, 4) END,
            cost_variance = ROUND(c.cost - c.earned, 2),
            cost_variance_percent = CASE WHEN c.earned = 0 THEN NULL
                                         ELSE ROUND((c.cost - c.earned) / c.earned, 6) END
          FROM c WHERE t.id = c.id
          """);
      if (updated > 0) {
        log.info("[DevSchemaFixupRunner] fixup 141 applied: actual_cost seeded on {} BOQ line(s)", updated);
      } else {
        log.debug("[DevSchemaFixupRunner] fixup 141: every BOQ line already carries actual_cost — no-op");
      }
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 141 (BOQ actual_cost backfill) failed — continuing", e);
    }
  }

  /**
   * Fixup 125 — backfill {@code dpr_issues.closed_at} from the status-history audit trail.
   * Mirrors Liquibase changeset 125 (prod): for issues currently CLOSED with no stamp, use
   * the LATEST transition into CLOSED — matching the service semantics (reopen clears,
   * re-close re-stamps). Issues closed by a path that bypassed the service have no history
   * row and stay null. Idempotent via the {@code closed_at IS NULL} guard.
   */
  private void backfillDprIssueClosedAt() {
    try {
      int updated = jdbcTemplate.update(
          """
          UPDATE project.dpr_issues i
             SET closed_at = h.last_closed
            FROM (SELECT issue_id, MAX(created_at) AS last_closed
                    FROM project.dpr_issue_status_history
                   WHERE to_status = 'CLOSED'
                   GROUP BY issue_id) h
           WHERE h.issue_id = i.id
             AND i.status = 'CLOSED'
             AND i.closed_at IS NULL
          """);
      if (updated > 0) {
        log.info("[DevSchemaFixupRunner] fixup 125 applied: backfilled closed_at on {} issues", updated);
      } else {
        log.debug("[DevSchemaFixupRunner] fixup 125: no CLOSED issues missing closed_at — no-op");
      }
    } catch (Exception e) {
      log.warn("[DevSchemaFixupRunner] fixup 125 (dpr_issues.closed_at backfill) failed — continuing", e);
    }
  }
}
