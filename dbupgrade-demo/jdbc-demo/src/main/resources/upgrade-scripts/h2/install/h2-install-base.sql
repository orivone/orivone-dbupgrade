--dbupgrade formatted sql
-- ============================================================================
-- H2 · 1.0.0 首次安装基线（install = true）
-- 只在库上没有任何版本记录时执行，用来建立最小可用的表结构。
-- 内存库随 JVM 起落，所以每次运行都是「首次安装」，正好演示这条路径。
-- ============================================================================

--changeset h2:create-demo-customer
CREATE TABLE demo_customer (
    id         BIGINT      NOT NULL,
    name       VARCHAR(64) NOT NULL,
    status     SMALLINT    NOT NULL DEFAULT 1,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
--rollback DROP TABLE demo_customer;

--changeset h2:create-demo-order
CREATE TABLE demo_order (
    id          BIGINT        NOT NULL,
    customer_id BIGINT        NOT NULL,
    amount      DECIMAL(12,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
--rollback DROP TABLE demo_order;

-- 种子数据用固定主键，配合版本记录天然只执行一次。
-- 若手动清空 dbupgrade_record 后重跑，这里会因主键冲突失败 —— 这是预期行为，
-- 说明「重跑历史 changeset」本身就是危险操作，不该靠脚本自保。
--changeset h2:seed-demo-customer
INSERT INTO demo_customer (id, name, status) VALUES (1, '张三', 1);
INSERT INTO demo_customer (id, name, status) VALUES (2, '李四', 1);
--rollback DELETE FROM demo_customer WHERE id IN (1, 2);
