-- Migration V10 uses the CLOB type, which is not available in PostgreSQL.
-- We define a custom type CLOB as synonym for TEXT.
CREATE DOMAIN CLOB AS TEXT;
