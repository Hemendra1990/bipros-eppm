-- Integration Module Tables (v1)
-- Public schema for integration configurations, logs, and government system records

-- Integration Configurations Table
CREATE TABLE IF NOT EXISTS public.integration_configs (
    id UUID NOT NULL PRIMARY KEY,
    system_code VARCHAR(50) NOT NULL UNIQUE,
    system_name VARCHAR(255) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    api_key VARCHAR(500),
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auth_type VARCHAR(50) NOT NULL,
    last_sync_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    config_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'ERROR')),
    CHECK (auth_type IN ('NONE', 'API_KEY', 'OAUTH2', 'JWT'))
);

CREATE INDEX idx_integration_configs_system_code ON public.integration_configs(system_code);
CREATE INDEX idx_integration_configs_status ON public.integration_configs(status);

-- Integration Logs Table
CREATE TABLE IF NOT EXISTS public.integration_logs (
    id UUID NOT NULL PRIMARY KEY,
    integration_config_id UUID NOT NULL,
    direction VARCHAR(50) NOT NULL,
    endpoint VARCHAR(500) NOT NULL,
    request_payload TEXT,
    response_payload TEXT,
    http_status INTEGER,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    CONSTRAINT fk_integration_log_config FOREIGN KEY (integration_config_id)
        REFERENCES public.integration_configs(id) ON DELETE CASCADE,
    CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CHECK (status IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'PENDING'))
);

CREATE INDEX idx_integration_logs_config_id ON public.integration_logs(integration_config_id);
CREATE INDEX idx_integration_logs_created_at ON public.integration_logs(created_at DESC);
CREATE INDEX idx_integration_logs_status ON public.integration_logs(status);

-- PFMS Fund Transfers Table
CREATE TABLE IF NOT EXISTS public.fund_transfers (
    id UUID NOT NULL PRIMARY KEY,
    project_id UUID NOT NULL,
    pfms_reference_number VARCHAR(100),
    sanction_order_number VARCHAR(100) NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL,
    purpose TEXT,
    beneficiary VARCHAR(255) NOT NULL,
    transfer_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL,
    pfms_status VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    CHECK (status IN ('INITIATED', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED'))
);

CREATE INDEX idx_fund_transfers_project_id ON public.fund_transfers(project_id);
CREATE INDEX idx_fund_transfers_sanction_order ON public.fund_transfers(sanction_order_number);
CREATE INDEX idx_fund_transfers_pfms_ref ON public.fund_transfers(pfms_reference_number);
CREATE INDEX idx_fund_transfers_status ON public.fund_transfers(status);

-- GeM Orders Table
CREATE TABLE IF NOT EXISTS public.gem_orders (
    id UUID NOT NULL PRIMARY KEY,
    project_id UUID NOT NULL,
    contract_id UUID,
    gem_order_number VARCHAR(100) NOT NULL UNIQUE,
    gem_catalogue_id VARCHAR(100),
    item_description TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    total_value NUMERIC(19, 2) NOT NULL,
    vendor_name VARCHAR(255) NOT NULL,
    vendor_gem_id VARCHAR(100) NOT NULL,
    order_date DATE NOT NULL,
    delivery_date DATE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    CHECK (status IN ('PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX idx_gem_orders_project_id ON public.gem_orders(project_id);
CREATE INDEX idx_gem_orders_order_number ON public.gem_orders(gem_order_number);
CREATE INDEX idx_gem_orders_status ON public.gem_orders(status);

-- CPPP Tenders Table
CREATE TABLE IF NOT EXISTS public.cppp_tenders (
    id UUID NOT NULL PRIMARY KEY,
    project_id UUID NOT NULL,
    tender_id UUID,
    cppp_tender_number VARCHAR(100) NOT NULL UNIQUE,
    nit_reference_number VARCHAR(100) NOT NULL,
    published_date DATE NOT NULL,
    bid_submission_deadline DATE NOT NULL,
    cppp_url VARCHAR(500),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    CHECK (status IN ('PUBLISHED', 'LIVE', 'CLOSED', 'WITHDRAWN'))
);

CREATE INDEX idx_cppp_tenders_project_id ON public.cppp_tenders(project_id);
CREATE INDEX idx_cppp_tenders_tender_number ON public.cppp_tenders(cppp_tender_number);
CREATE INDEX idx_cppp_tenders_status ON public.cppp_tenders(status);

-- GSTN Verifications Table
CREATE TABLE IF NOT EXISTS public.gstn_verifications (
    id UUID NOT NULL PRIMARY KEY,
    contractor_name VARCHAR(255) NOT NULL,
    gstin VARCHAR(15) NOT NULL UNIQUE,
    pan_number VARCHAR(20),
    legal_name VARCHAR(500),
    trade_name VARCHAR(500),
    gst_status VARCHAR(50) NOT NULL,
    last_verified_at TIMESTAMP,
    is_compliant BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    CHECK (gst_status IN ('ACTIVE', 'SUSPENDED', 'CANCELLED', 'UNKNOWN'))
);

CREATE INDEX idx_gstn_verifications_gstin ON public.gstn_verifications(gstin);
CREATE INDEX idx_gstn_verifications_gst_status ON public.gstn_verifications(gst_status);
CREATE INDEX idx_gstn_verifications_contractor ON public.gstn_verifications(contractor_name);

-- End Integration Module Tables
