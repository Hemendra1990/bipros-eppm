CREATE OR REPLACE VIEW vw_dim_project_finance AS SELECT * FROM dim_project FINAL;
CREATE OR REPLACE VIEW vw_dim_wbs_finance AS SELECT * FROM dim_wbs FINAL;
CREATE OR REPLACE VIEW vw_dim_activity_finance AS SELECT * FROM dim_activity FINAL;
CREATE OR REPLACE VIEW vw_dim_resource_finance AS SELECT * FROM dim_resource FINAL;
CREATE OR REPLACE VIEW vw_dim_user_finance AS SELECT * FROM dim_user FINAL;

CREATE OR REPLACE VIEW vw_fact_evm_snapshots_finance AS SELECT * FROM fact_evm_snapshots FINAL;
CREATE OR REPLACE VIEW vw_fact_activity_progress_finance AS SELECT * FROM fact_activity_progress FINAL;
CREATE OR REPLACE VIEW vw_fact_dpr_lines_finance AS SELECT * FROM fact_dpr_lines FINAL;
CREATE OR REPLACE VIEW vw_fact_activity_expenses_finance AS SELECT * FROM fact_activity_expenses FINAL;
CREATE OR REPLACE VIEW vw_fact_resource_assignments_finance AS SELECT * FROM fact_resource_assignments FINAL;
CREATE OR REPLACE VIEW vw_fact_baseline_variance_finance AS SELECT * FROM fact_baseline_variance FINAL;
CREATE OR REPLACE VIEW vw_fact_risks_finance AS SELECT * FROM fact_risks FINAL;
CREATE OR REPLACE VIEW vw_fact_contracts_finance AS SELECT * FROM fact_contracts FINAL;
CREATE OR REPLACE VIEW vw_fact_resource_daily_logs_finance AS SELECT * FROM fact_resource_daily_logs FINAL;
CREATE OR REPLACE VIEW vw_fact_equipment_logs_finance AS SELECT * FROM fact_equipment_logs FINAL;
CREATE OR REPLACE VIEW vw_fact_labour_returns_finance AS SELECT * FROM fact_labour_returns FINAL;
