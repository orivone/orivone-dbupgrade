--dbupgrade formatted sql
-- ============================================================================
-- MySQL 5.7+ · 1.0.0 首次安装基线（install = true）
-- 只在库中还没有任何版本记录时执行：建表 + 两行种子数据。
-- MySQL 的 DDL 特有写法：ENGINE / CHARSET / TINYINT，所以这份脚本不能给别的库用。
-- ============================================================================

--changeset mysql:create-customer
CREATE TABLE demo_customer (
    id     BIGINT      NOT NULL,
    name   VARCHAR(64) NOT NULL,
    status TINYINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE demo_customer;

-- 种子数据用固定主键，配合版本记录天然只执行一次
--changeset mysql:seed-customer
INSERT INTO demo_customer (id, name, status) VALUES (1, '张三', 1);
INSERT INTO demo_customer (id, name, status) VALUES (2, '李四', 1);
--rollback DELETE FROM demo_customer WHERE id IN (1, 2);
