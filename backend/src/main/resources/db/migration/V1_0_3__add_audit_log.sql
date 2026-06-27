-- ===================================================
-- TAUT — Platform Daur Ulang Digital
-- Version: V1_0_3
-- Description: Audit log partitions and fraud detection
--              materialized view.
-- ===================================================

-- Additional audit log partitions for upcoming months
CREATE TABLE IF NOT EXISTS audit_log_2026_10 PARTITION OF audit_log
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS audit_log_2026_11 PARTITION OF audit_log
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS audit_log_2026_12 PARTITION OF audit_log
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- Fraud anomaly detection materialized view
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_fraud_anomaly AS
SELECT
    date_trunc('day', t.created_at) AS day,
    t.bank_sampah_id,
    t.operator_id,
    COUNT(t.id) AS tx_count,
    AVG(t.total_weight) AS avg_weight_grams,
    AVG(t.total_value) AS avg_value_satuan,
    STDDEV(t.total_weight) AS weight_stddev
FROM transactions t
WHERE t.status IN ('synced', 'confirmed')
  AND t.created_at > NOW() - INTERVAL '30 days'
GROUP BY 1, 2, 3
HAVING AVG(t.total_value) > (
    SELECT AVG(total_value) * 3 FROM transactions
    WHERE status IN ('synced', 'confirmed')
      AND created_at > NOW() - INTERVAL '30 days'
);

CREATE INDEX IF NOT EXISTS idx_mv_fraud_anomaly_day
    ON mv_daily_fraud_anomaly (day, bank_sampah_id);
