--dbupgrade formatted sql
-- ============================================================================
-- SQL Server · 1.1.0 客户模块（本版本里第二个执行的文件）
--
-- 名字以 c 开头、字母序在前，实际却在订单模块之后执行 ——
-- 再一次说明顺序只看注册表的 filePaths 列表。
-- ============================================================================

--changeset sqlserver:add-customer-phone
ALTER TABLE demo_customer ADD phone NVARCHAR(32) NULL;
--rollback ALTER TABLE demo_customer DROP COLUMN phone;

-- 一个 changeset 里可以有多条语句：执行器拆开后逐条判重，
-- ALTER 看列、CREATE INDEX 看索引，两条互不牵连。
-- 注意判重的粒度是「语句」不是「changeset」：前半段已生效、后半段没生效时，
-- 下次只会补跑后半段。
--changeset sqlserver:add-customer-email
ALTER TABLE demo_customer ADD email NVARCHAR(128) NULL;
CREATE INDEX idx_demo_customer_email ON demo_customer (email);
--rollback DROP INDEX idx_demo_customer_email ON demo_order;
--rollback ALTER TABLE demo_customer DROP COLUMN email;

-- DML 不会被自动判重，幂等靠 WHERE 条件自己保证
--changeset sqlserver:backfill-customer-email
UPDATE demo_customer SET email = 'c' + CAST(id AS VARCHAR(20)) + '@example.com' WHERE email IS NULL;
--rollback UPDATE demo_customer SET email = NULL;
