--dbupgrade formatted sql
-- ============================================================================
-- H2 · 1.1.0 增量版本
-- 加法类 DDL 交给执行器做存在性判断：列已存在则跳过，不会抛 duplicate column。
-- 一个 changeset 内可以写多条语句，执行器拆分后逐条执行、逐条判重。
-- ============================================================================

--changeset h2:add-customer-phone
ALTER TABLE demo_customer ADD COLUMN phone VARCHAR(32) DEFAULT NULL;
--rollback ALTER TABLE demo_customer DROP COLUMN phone;

--changeset h2:add-customer-email
ALTER TABLE demo_customer ADD COLUMN email VARCHAR(128) DEFAULT NULL;
CREATE INDEX idx_demo_customer_email ON demo_customer (email);
--rollback DROP INDEX idx_demo_customer_email;
--rollback ALTER TABLE demo_customer DROP COLUMN email;

-- DML 不会自动判重，WHERE 条件本身就是幂等保证
--changeset h2:backfill-customer-phone
UPDATE demo_customer SET phone = '000-0000' WHERE phone IS NULL;
--rollback UPDATE demo_customer SET phone = NULL;
