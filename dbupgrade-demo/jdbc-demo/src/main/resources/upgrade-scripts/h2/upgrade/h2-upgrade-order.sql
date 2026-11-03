--dbupgrade formatted sql
-- ============================================================================
-- H2 · 1.1.0 订单模块变更（注册表里排在 h2-upgrade-customer.sql 之前）
-- 文件名按字母序是 customer 在前，实际却由 order 先跑 —— 顺序只看注册表里的 filePaths，
-- 文件名不承担任何语义，也不会被解析出版本号。
-- ============================================================================

--changeset h2:add-order-pay-status
-- 加法类 DDL 由执行器判断：列已存在就跳过这条语句，不会抛 duplicate column
ALTER TABLE demo_order ADD COLUMN pay_status SMALLINT NOT NULL DEFAULT 0;
--rollback ALTER TABLE demo_order DROP COLUMN pay_status;

--changeset h2:index-order-customer
CREATE INDEX idx_demo_order_customer ON demo_order (customer_id);
--rollback DROP INDEX idx_demo_order_customer;
