--dbupgrade formatted sql
-- ============================================================================
-- 达梦 DM · 1.1.0 增量版本
-- 加列写法与 Oracle 一致：ADD col type，不带 COLUMN 关键字。
-- ============================================================================

--changeset dm:add-customer-phone
ALTER TABLE demo_customer ADD phone VARCHAR2(32);
--rollback ALTER TABLE demo_customer DROP COLUMN phone;

--changeset dm:backfill-customer-phone
UPDATE demo_customer SET phone = '000-0000' WHERE phone IS NULL;
--rollback UPDATE demo_customer SET phone = NULL;
