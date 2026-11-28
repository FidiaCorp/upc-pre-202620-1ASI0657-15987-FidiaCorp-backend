-- property_db (MS-2 property-catalog-service). owner_id es un UUID lógico de identity_db: SIN FK física hacia accounts
-- (ADR-001 / PA-05); la existencia y el rol del propietario los garantiza el JWT emitido por identity-subscription-service.
CREATE TABLE properties (
    id                        UUID PRIMARY KEY,
    owner_id                  UUID NOT NULL,
    title                     VARCHAR(120) NOT NULL,
    address                   VARCHAR(250) NOT NULL,
    description               VARCHAR(2000) NOT NULL,
    price                     NUMERIC(18,2) NOT NULL CHECK (price > 0),
    currency                  VARCHAR(10) NOT NULL CHECK (currency IN ('PEN','USD')),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at                TIMESTAMP WITH TIME ZONE,
    version                   BIGINT
);
CREATE INDEX idx_properties_owner ON properties(owner_id);
CREATE INDEX idx_properties_active_created ON properties(deleted_at, created_at);

CREATE TABLE property_tags (
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    tag         VARCHAR(40) NOT NULL,
    CONSTRAINT uk_property_tag UNIQUE (property_id, tag)
);
