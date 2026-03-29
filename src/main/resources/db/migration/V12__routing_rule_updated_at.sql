-- Audit timestamp for routing rule updates
ALTER TABLE customer_email_routing_rules
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
