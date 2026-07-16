CREATE TABLE form_definition (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  VARCHAR(64),
    form_key         VARCHAR(128) NOT NULL,
    payload          JSONB        NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE NULLS NOT DISTINCT (organization_id, form_key)
);
CREATE INDEX idx_form_definition_org ON form_definition (organization_id);