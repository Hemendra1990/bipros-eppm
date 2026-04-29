-- BNK reports/00-currency-omr.sql — Upsert Omani Rial (OMR) currency.
-- The Oman Barka–Nakhal road project (code 6155) is denominated in OMR.
-- The currency uses 3 decimal places (1 OMR = 1000 baisa) — distinct from
-- the more common 2-decimal currencies used elsewhere in the seed bundle.
-- Runs first (00- prefix) so subsequent project_id-keyed inserts can rely
-- on the project being created with currency='OMR' resolving to a known
-- currency master row.

INSERT INTO public.currencies (
    id, created_at, updated_at, version,
    code, name, symbol, exchange_rate, is_base_currency, decimal_places
) VALUES (
    gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
    'OMR', 'Omani Rial', 'OMR', 1.000000, FALSE, 3
)
ON CONFLICT (code) DO UPDATE
   SET name           = EXCLUDED.name,
       symbol         = EXCLUDED.symbol,
       decimal_places = EXCLUDED.decimal_places,
       updated_at     = CURRENT_TIMESTAMP;
