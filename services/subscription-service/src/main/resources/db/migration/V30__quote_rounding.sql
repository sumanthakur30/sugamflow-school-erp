-- Stores how a quotation's final price was rounded. Existing quotes stay exact (NONE).
ALTER TABLE pricing_quote
    ADD COLUMN rounding_step VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE pricing_quote
    ADD CONSTRAINT ck_pricing_quote_rounding
    CHECK (rounding_step IN ('NONE', 'RUPEE', 'TEN', 'HUNDRED'));
