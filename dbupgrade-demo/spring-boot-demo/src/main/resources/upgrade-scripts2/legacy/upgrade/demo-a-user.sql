--dbupgrade formatted sql
-- ============================================================================
-- 版本 1.1.0 · 用户模块变更
-- 注意：本文件在注册表 filePaths 中排在 demo-b-order.sql 之后。
--      文件名字母序是 a 在前，实际执行顺序却由 Java 注册表决定 —— 文件名不参与排序。
-- ============================================================================

-- 加法类 DDL 交由执行器判重：列已存在则跳过，不会报 duplicate column
--changeset zhangsan:add-phone
ALTER TABLE demo_user ADD COLUMN phone VARCHAR(32) DEFAULT NULL COMMENT '手机号' AFTER status;
--rollback ALTER TABLE demo_user DROP COLUMN phone;

-- 一个 changeset 内可以有多条语句，执行器会按分隔符拆分后逐条执行。
-- 两条 DDL 分别独立判重：ALTER 看列、CREATE INDEX 看索引，互不干扰。
--changeset zhangsan:add-email
ALTER TABLE demo_user ADD COLUMN email VARCHAR(128) DEFAULT NULL COMMENT '邮箱' AFTER phone;
CREATE INDEX idx_user_email ON demo_user (email);
--rollback DROP INDEX idx_user_email ON demo_user;
--rollback ALTER TABLE demo_user DROP COLUMN email;

-- DML 不会被自动判重，WHERE 条件本身就是幂等保证
--changeset zhangsan:backfill-email
UPDATE demo_user SET email = CONCAT('user', id, '@example.com') WHERE email IS NULL;
--rollback UPDATE demo_user SET email = NULL;
