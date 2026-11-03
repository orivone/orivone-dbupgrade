--dbupgrade formatted sql
-- ============================================================================
-- SQL Server · 1.1.0 增量版本
-- 加列用 ADD col type NULL；列存在性由框架执行前判断，重复跑不会报错。
-- ============================================================================

--changeset sqlserver:add-customer-phone
ALTER TABLE demo_customer ADD phone NVARCHAR(32) NULL;
--rollback ALTER TABLE demo_customer DROP COLUMN phone;

--changeset sqlserver:backfill-customer-phone
UPDATE demo_customer SET phone = N'000-0000' WHERE phone IS NULL;
--rollback UPDATE demo_customer SET phone = NULL;
