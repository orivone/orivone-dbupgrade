--dbupgrade formatted sql
-- ============================================================================
-- PostgreSQL · 1.0.0 首次安装基线（install = true）
-- 全部使用标准 SQL，类型与 MySQL 版一致（BIGINT / VARCHAR / SMALLINT）。
-- ============================================================================

--changeset postgresql:create-customer
CREATE TABLE demo_customer (
    id     BIGINT      NOT NULL,
    name   VARCHAR(64) NOT NULL,
    status SMALLINT    NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
);
--rollback DROP TABLE demo_customer;

-- 种子数据用固定主键，配合版本记录天然只执行一次
--changeset postgresql:seed-customer
INSERT INTO demo_customer (id, name, status) VALUES (1, '张三', 1);
INSERT INTO demo_customer (id, name, status) VALUES (2, '李四', 1);
--rollback DELETE FROM demo_customer WHERE id IN (1, 2);
