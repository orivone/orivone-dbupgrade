--dbupgrade formatted sql
-- ============================================================================
-- PostgreSQL · 1.1.0 增量版本
-- ============================================================================

--changeset postgresql:add-customer-phone
ALTER TABLE demo_customer ADD COLUMN phone VARCHAR(32);
--rollback ALTER TABLE demo_customer DROP COLUMN phone;

-- DML 不会自动判重，WHERE 条件本身就是幂等保证
--changeset postgresql:backfill-customer-phone
UPDATE demo_customer SET phone = '000-0000' WHERE phone IS NULL;
--rollback UPDATE demo_customer SET phone = NULL;
