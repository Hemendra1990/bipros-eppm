CREATE OR REPLACE VIEW vw_dim_project_public AS
SELECT id, code, name, eps_node_id, status, planned_start_date, planned_finish_date,
       data_date, archived_at
FROM dim_project FINAL;

CREATE OR REPLACE VIEW vw_dim_wbs_public AS
SELECT id, code, name, project_id, parent_id, sort_order, wbs_level, wbs_status,
       phase, planned_start, planned_finish
FROM dim_wbs FINAL;

CREATE OR REPLACE VIEW vw_dim_activity_public AS
SELECT id, code, name, project_id, wbs_node_id, status, activity_type, is_critical
FROM dim_activity FINAL;

CREATE OR REPLACE VIEW vw_dim_resource_public AS
SELECT id, code, resource_type, resource_category, status
FROM dim_resource FINAL;

CREATE OR REPLACE VIEW vw_dim_user_public AS
SELECT id, username, full_name, enabled
FROM dim_user FINAL;

CREATE OR REPLACE VIEW vw_fact_evm_snapshots_public AS
SELECT id, project_id, wbs_node_id, activity_id, data_date
FROM fact_evm_snapshots FINAL;

CREATE OR REPLACE VIEW vw_fact_activity_progress_public AS
SELECT activity_id, snapshot_date, project_id, wbs_node_id, status,
       planned_start_date, planned_finish_date, actual_start_date, actual_finish_date,
       is_critical
FROM fact_activity_progress FINAL;

CREATE OR REPLACE VIEW vw_fact_dpr_lines_public AS
SELECT id, project_id, report_date, activity_name, wbs_node_id
FROM fact_dpr_lines FINAL;

CREATE OR REPLACE VIEW vw_fact_activity_expenses_public AS
SELECT id, activity_id, project_id, planned_start_date, planned_finish_date
FROM fact_activity_expenses FINAL;

CREATE OR REPLACE VIEW vw_fact_resource_assignments_public AS
SELECT id, activity_id, resource_id, project_id, planned_start_date, planned_finish_date
FROM fact_resource_assignments FINAL;

CREATE OR REPLACE VIEW vw_fact_baseline_variance_public AS
SELECT activity_id, baseline_id, snapshot_date, project_id,
       baseline_early_start, baseline_early_finish,
       current_planned_start, current_planned_finish
FROM fact_baseline_variance FINAL;

CREATE OR REPLACE VIEW vw_fact_risks_public AS
SELECT id, project_id, code, title, status, risk_type, rag, identified_date, due_date
FROM fact_risks FINAL;

CREATE OR REPLACE VIEW vw_fact_contracts_public AS
SELECT id, project_id, contract_number, contract_type, status, start_date, completion_date
FROM fact_contracts FINAL;

CREATE OR REPLACE VIEW vw_fact_resource_daily_logs_public AS
SELECT id, resource_id, log_date
FROM fact_resource_daily_logs FINAL;

CREATE OR REPLACE VIEW vw_fact_equipment_logs_public AS
SELECT id, resource_id, project_id, log_date, status
FROM fact_equipment_logs FINAL;

CREATE OR REPLACE VIEW vw_fact_labour_returns_public AS
SELECT id, project_id, return_date, skill_category, head_count, man_days, wbs_node_id
FROM fact_labour_returns FINAL;
