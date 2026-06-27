-- ===================================================
-- TAUT — Platform Daur Ulang Digital
-- Version: V1_0_2
-- Description: Additional price reference infrastructure
--              (price_references table already in V1_0_0)
--              This migration ensures TimescaleDB hypertable
--              and additional indexes exist.
-- ===================================================

-- TimescaleDB hypertable for price_references
-- Uncomment when TimescaleDB extension is available:
-- SELECT create_hypertable('price_references', 'effective_from', chunk_time_interval => INTERVAL '30 days');

-- Additional price query index
CREATE INDEX IF NOT EXISTS idx_price_lookup_active
    ON price_references (category_id, region_id, effective_from DESC)
    WHERE is_current = true;
