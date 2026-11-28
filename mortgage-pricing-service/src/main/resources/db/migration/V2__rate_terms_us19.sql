-- US19: tasa ingresada (TEA | TNA) y capitalización; annual_interest_rate y tea guardan siempre la TEA efectiva.
ALTER TABLE quotations ADD COLUMN rate_type VARCHAR(3) NOT NULL DEFAULT 'TEA' CHECK (rate_type IN ('TEA','TNA'));
ALTER TABLE quotations ADD COLUMN input_rate NUMERIC(38,24);
ALTER TABLE quotations ADD COLUMN compounding_periods_per_year INTEGER CHECK (compounding_periods_per_year BETWEEN 1 AND 365);
