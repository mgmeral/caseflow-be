-- V30: Customer color support
-- Adds an optional hex color code to customers for UI badge/avatar display.
-- Stored in canonical uppercase #RRGGBB form; normalized by the service layer.

ALTER TABLE customers
    ADD COLUMN color_hex VARCHAR(7);
