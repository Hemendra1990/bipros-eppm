-- Create all required schemas for Bipros EPPM
CREATE SCHEMA IF NOT EXISTS project;
CREATE SCHEMA IF NOT EXISTS activity;
CREATE SCHEMA IF NOT EXISTS scheduling;
CREATE SCHEMA IF NOT EXISTS resource;
CREATE SCHEMA IF NOT EXISTS cost;
CREATE SCHEMA IF NOT EXISTS evm;
CREATE SCHEMA IF NOT EXISTS baseline;
CREATE SCHEMA IF NOT EXISTS udf;
CREATE SCHEMA IF NOT EXISTS risk;
CREATE SCHEMA IF NOT EXISTS portfolio;
CREATE SCHEMA IF NOT EXISTS document;
CREATE SCHEMA IF NOT EXISTS contract;
CREATE SCHEMA IF NOT EXISTS gis;
-- PostGIS extension must exist before Hibernate creates the wbs_polygons
-- geometry column. Works on the postgis/postgis:17-3.5 image; fails loudly if
-- run against vanilla postgres:17 (image mismatch — fix docker-compose.yml).
CREATE EXTENSION IF NOT EXISTS postgis;
