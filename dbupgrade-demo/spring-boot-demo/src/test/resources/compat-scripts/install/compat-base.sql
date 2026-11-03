--dbupgrade formatted sql
-- ============================================================================
-- 兼容性测试用脚本 · 版本 1.0.0（install 基线）
-- 只用标准 SQL：H2 / MySQL 都能跑，测试固定跑在 H2 内存库上。
-- 本文件不参与 demo 打包，只服务 BootCompatibilityTest。
-- ============================================================================

--changeset compat:create-demo-item
CREATE TABLE demo_item (
    id   BIGINT      NOT NULL,
    name VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
);
--rollback DROP TABLE demo_item;

--changeset compat:seed-demo-item
INSERT INTO demo_item (id, name) VALUES (1, 'alpha');
--rollback DELETE FROM demo_item WHERE id = 1;
