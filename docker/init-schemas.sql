-- Create application user (runs as postgres superuser)
DO $$
BEGIN
  CREATE USER bipros WITH PASSWORD 'bipros_dev' CREATEDB;
EXCEPTION WHEN duplicate_object THEN
  RAISE NOTICE 'User bipros already exists';
END
$$;

-- Create database owned by bipros (if not using POSTGRES_DB)
GRANT ALL PRIVILEGES ON DATABASE bipros TO bipros;

-- PostGIS extension — required by the gis module's wbs_polygons.polygon column.
-- The postgis/postgis image ships the binaries but the extension still has to be
-- enabled per database; without this Hibernate fails with "Unknown type geometry"
-- when the seeder writes WBS polygons. Must run before schema creation so any
-- future schema-scoped postgis usage resolves cleanly.
CREATE EXTENSION IF NOT EXISTS postgis;

-- Initialize PostgreSQL schemas per bounded context
CREATE SCHEMA IF NOT EXISTS project AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS activity AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS scheduling AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS resource AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS cost AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS evm AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS baseline AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS udf AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS risk AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS portfolio AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS contract AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS document AUTHORIZATION bipros;
CREATE SCHEMA IF NOT EXISTS gis AUTHORIZATION bipros;
-- analytics: BYOK LLM provider configs + assistant audit log (added Phase 0)
CREATE SCHEMA IF NOT EXISTS analytics AUTHORIZATION bipros;

-- Grant public schema access
GRANT ALL ON SCHEMA public TO bipros;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO bipros;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO bipros;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO bipros;
