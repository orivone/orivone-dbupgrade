--dbupgrade formatted sql
-- ============================================================================
-- MySQL 5.7+ · 1.1.0 增量版本
-- 加列是加法类 DDL，框架执行前会先判断列是否存在，重复跑不会报 duplicate column。
-- ============================================================================

--changeset mysql:add-customer-phone
ALTER TABLE demo_customer ADD COLUMN phone VARCHAR(32) DEFAULT NULL;
--rollback ALTER TABLE demo_customer DROP COLUMN phone;

-- DML 不会自动判重，WHERE 条件本身就是幂等保证
--changeset mysql:backfill-customer-phone
UPDATE demo_customer SET phone = '000-0000' WHERE phone IS NULL;
--rollback UPDATE demo_customer SET phone = NULL;
