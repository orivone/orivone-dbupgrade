--dbupgrade formatted sql
-- ============================================================================
-- Oracle · 1.1.0 增量版本
-- 加列的 Oracle 写法：ADD col type，不带 COLUMN 关键字（框架两种写法都能识别）。
-- 注意 Oracle 的 DDL 隐式提交，失败后只能靠幂等重跑补齐。
-- ============================================================================

--changeset oracle:add-customer-phone
ALTER TABLE demo_customer ADD phone VARCHAR2(32);
--rollback ALTER TABLE demo_customer DROP COLUMN phone;

--changeset oracle:backfill-customer-phone
UPDATE demo_customer SET phone = '000-0000' WHERE phone IS NULL;
--rollback UPDATE demo_customer SET phone = NULL;
