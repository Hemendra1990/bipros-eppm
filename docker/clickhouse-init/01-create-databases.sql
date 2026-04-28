-- Phase 0: ClickHouse foundation. The analytics database holds fact_*/dim_*/agg_*
-- tables in Phase 1. A read-only user is provisioned for the future LLM query
-- layer (Phase 2). No tables here yet.

CREATE DATABASE IF NOT EXISTS bipros_analytics;

-- Read-only role for the LLM tool dispatcher. SELECT-only on the analytics DB.
-- Password is dev-only; prod must override via CLICKHOUSE_READER_PASSWORD env.
CREATE USER IF NOT EXISTS bipros_reader IDENTIFIED WITH plaintext_password BY 'bipros_reader_dev';
CREATE ROLE IF NOT EXISTS analytics_reader;
GRANT SELECT ON bipros_analytics.* TO analytics_reader;
GRANT analytics_reader TO bipros_reader;
