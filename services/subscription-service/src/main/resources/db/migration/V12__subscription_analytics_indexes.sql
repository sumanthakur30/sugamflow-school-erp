-- Phase 10: analytics query indexes (additive). No new business tables.

CREATE INDEX IF NOT EXISTS idx_sub_invoice_status_created
    ON subscription_invoice (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sub_payment_status_created
    ON subscription_payment (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_usage_counter_limit_used
    ON usage_counter (limit_code, used_value DESC);
