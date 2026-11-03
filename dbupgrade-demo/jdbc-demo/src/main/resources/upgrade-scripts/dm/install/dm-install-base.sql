--dbupgrade formatted sql
-- ============================================================================
-- 达梦 DM · 1.0.0 首次安装基线（install = true）
-- 语法兼容 Oracle，沿用 NUMBER / VARCHAR2，只有连接串与驱动不同。
-- ============================================================================

--changeset dm:create-customer
CREATE TABLE demo_customer (
    id     NUMBER(19)   NOT NULL,
    name   VARCHAR2(64) NOT NULL,
    status NUMBER(3)    DEFAULT 1 NOT NULL,
    PRIMARY KEY (id)
);
--rollback DROP TABLE demo_customer;

-- 种子数据用固定主键，配合版本记录天然只执行一次
--changeset dm:seed-customer
INSERT INTO demo_customer (id, name, status) VALUES (1, '张三', 1);
INSERT INTO demo_customer (id, name, status) VALUES (2, '李四', 1);
--rollback DELETE FROM demo_customer WHERE id IN (1, 2);
