CREATE OR REPLACE VIEW vw_dim_project_internal AS
SELECT id, code, name, description, eps_node_id, obs_node_id, status,
       planned_start_date, planned_finish_date, data_date, must_finish_by_date,
       priority, category, morth_code, from_chainage_m, to_chainage_m,
       from_location, to_location, total_length_km, active_baseline_id,
       owner_id, archived_at, created_at, updated_at
FROM dim_project FINAL;

CREATE OR REPLACE VIEW vw_dim_wbs_internal AS
SELECT id, code, name, project_id, parent_id, obs_node_id, sort_order, wbs_level,
       wbs_type, phase, wbs_status, asset_class, responsible_organisation_id,
       planned_start, planned_finish, gis_polygon_id, chainage_from_m, chainage_to_m,
       summary_duration, summary_percent_complete, created_at, updated_at
FROM dim_wbs FINAL;

CREATE OR REPLACE VIEW vw_dim_activity_internal AS
SELECT * FROM dim_activity FINAL;

CREATE OR REPLACE VIEW vw_dim_resource_internal AS
SELECT id, code, name, resource_type, resource_category, cost_category, unit,
       parent_id, calendar_id, title, max_units_per_day, default_units_per_time,
       pool_max_available, status, sort_order, capacity_spec, make_model,
       quantity_available, ownership_type, standard_output_per_day,
       fuel_litres_per_hour, standard_output_unit, responsible_contractor_id,
       responsible_contractor_name, wbs_assignment_id, created_at, updated_at
FROM dim_resource FINAL;

CREATE OR REPLACE VIEW vw_dim_user_internal AS
SELECT id, username, full_name, first_name, last_name, designation,
       primary_icpms_role, department, organisation_id, presence_status,
       enabled, account_locked, created_at, updated_at
FROM dim_user FINAL;

CREATE OR REPLACE VIEW vw_fact_evm_snapshots_internal AS
SELECT id, project_id, wbs_node_id, activity_id, financial_period_id, data_date,
       schedule_performance_index, cost_performance_index, to_complete_performance_index,
       performance_percent_complete, evm_technique, etc_method, created_at, updated_at
FROM fact_evm_snapshots FINAL;

CREATE OR REPLACE VIEW vw_fact_activity_progress_internal AS
SELECT * FROM fact_activity_progress FINAL;

CREATE OR REPLACE VIEW vw_fact_dpr_lines_internal AS
SELECT * FROM fact_dpr_lines FINAL;

CREATE OR REPLACE VIEW vw_fact_activity_expenses_internal AS
SELECT id, activity_id, project_id, name, description, expense_category,
       percent_complete, planned_start_date, planned_finish_date,
       actual_start_date, actual_finish_date, currency, created_at, updated_at
FROM fact_activity_expenses FINAL;

CREATE OR REPLACE VIEW vw_fact_resource_assignments_internal AS
SELECT id, activity_id, resource_id, project_id, role_id, resource_curve_id,
       planned_units, actual_units, remaining_units, at_completion_units,
       planned_start_date, planned_finish_date, actual_start_date, actual_finish_date,
       created_at, updated_at
FROM fact_resource_assignments FINAL;

CREATE OR REPLACE VIEW vw_fact_baseline_variance_internal AS
SELECT activity_id, baseline_id, snapshot_date, project_id,
       baseline_early_start, baseline_early_finish, baseline_late_start, baseline_late_finish,
       baseline_percent_complete, current_planned_start, current_planned_finish,
       current_actual_start, current_actual_finish, current_percent_complete,
       start_variance_days, finish_variance_days, duration_variance,
       percent_complete_variance, updated_at
FROM fact_baseline_variance FINAL;

CREATE OR REPLACE VIEW vw_fact_risks_internal AS
SELECT id, project_id, code, title, description, category_id, status, risk_type,
       probability, impact, impact_cost, impact_schedule, risk_score, residual_risk_score,
       rag, trend, owner_id, identified_by_id, identified_date, due_date,
       schedule_impact_days, exposure_start_date, exposure_finish_date,
       response_type, post_response_probability, post_response_impact_cost,
       post_response_impact_schedule, post_response_risk_score, created_at, updated_at
FROM fact_risks FINAL;

CREATE OR REPLACE VIEW vw_fact_contracts_internal AS
SELECT id, project_id, tender_id, contract_number, loa_number, contractor_name,
       contractor_code, loa_date, start_date, completion_date, revised_completion_date,
       ntp_date, actual_completion_date, dlp_months, status, contract_type,
       description, currency, billing_cycle, wbs_package_code, package_description,
       physical_progress_ai, vo_numbers_issued, bg_expiry, kpi_refreshed_at,
       created_at, updated_at
FROM fact_contracts FINAL;

CREATE OR REPLACE VIEW vw_fact_resource_daily_logs_internal AS
SELECT id, resource_id, log_date, planned_units, actual_units, utilisation_percent,
       wbs_package_code, remarks, created_at, updated_at
FROM fact_resource_daily_logs FINAL;

CREATE OR REPLACE VIEW vw_fact_equipment_logs_internal AS
SELECT id, resource_id, project_id, log_date, deployment_site, operating_hours,
       idle_hours, breakdown_hours, fuel_consumed, operator_name, status, remarks,
       created_at, updated_at
FROM fact_equipment_logs FINAL;

CREATE OR REPLACE VIEW vw_fact_labour_returns_internal AS
SELECT id, project_id, contractor_name, return_date, skill_category, head_count,
       man_days, wbs_node_id, site_location, remarks, created_at, updated_at
FROM fact_labour_returns FINAL;
