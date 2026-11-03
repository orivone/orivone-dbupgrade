--dbupgrade formatted sql
-- ============================================================================
-- SQL Server · 1.0.0 首次安装基线（install = true）
-- 字符串列用 NVARCHAR；中文常量必须带 N 前缀，否则可能变成问号。
-- 没有 CREATE TABLE IF NOT EXISTS，建表由框架先探测再决定，这里写普通建表即可。
-- ============================================================================

--changeset sqlserver:create-customer
CREATE TABLE demo_customer (
    id     BIGINT       NOT NULL,
    name   NVARCHAR(64) NOT NULL,
    status SMALLINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
);
--rollback DROP TABLE demo_customer;

-- 种子数据用固定主键，配合版本记录天然只执行一次
--changeset sqlserver:seed-customer
INSERT INTO demo_customer (id, name, status) VALUES (1, N'张三', 1);
INSERT INTO demo_customer (id, name, status) VALUES (2, N'李四', 1);
--rollback DELETE FROM demo_customer WHERE id IN (1, 2);
