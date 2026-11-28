-- US09: certificado registral SUNARP (DEMO) sellado con SHA-256 sobre propertyId|registryNumber|claimedOwner|status|verifiedAt.
ALTER TABLE properties ADD COLUMN registry_number VARCHAR(50);
ALTER TABLE properties ADD COLUMN registry_status VARCHAR(30) DEFAULT 'PENDING_VERIFICATION';
ALTER TABLE properties ADD COLUMN registry_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE properties ADD COLUMN registry_office VARCHAR(100);
ALTER TABLE properties ADD COLUMN registry_notes VARCHAR(1000);
ALTER TABLE properties ADD COLUMN registry_owner_name VARCHAR(200);
ALTER TABLE properties ADD COLUMN registry_certificate_hash VARCHAR(64);
