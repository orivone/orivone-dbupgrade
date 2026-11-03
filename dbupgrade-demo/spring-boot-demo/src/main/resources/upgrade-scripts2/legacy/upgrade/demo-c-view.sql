--dbupgrade formatted sql
-- ============================================================================
-- 版本 1.2.0 · 视图刷新与条件索引
-- 演示两个高级能力：runOnChange 内容变化才重跑、sqlCheck 条件 DDL
-- ============================================================================

-- runOnChange：checksum 不一致时才重新执行。
-- 想观察效果：修改下面的列顺序或增删一个字段，重启 demo，本 changeset 会重新执行；
-- 不修改则一直跳过（视图类对象无“存在性”可判断，只能靠 checksum 控制）。
--changeset wangwu:refresh-user-order-view runOnChange:true
CREATE OR REPLACE VIEW v_demo_user_order AS
SELECT u.id         AS user_id,
       u.name       AS user_name,
       u.phone      AS phone,
       u.email      AS email,
       o.id         AS order_id,
       o.amount     AS amount,
       o.pay_status AS pay_status
FROM demo_user u
LEFT JOIN demo_order o ON o.user_id = u.id;
--rollback DROP VIEW IF EXISTS v_demo_user_order;

-- 条件 DDL：sqlCheck 结果第一列非 0 才执行；为 0 时按 onFail:MARK_RAN 标记为已执行后跳过
--changeset wangwu:index-unpaid-order
--precondition sqlCheck "SELECT COUNT(*) FROM demo_order WHERE pay_status = 0" onFail:MARK_RAN
CREATE INDEX idx_order_unpaid ON demo_order (pay_status);
--rollback DROP INDEX idx_order_unpaid ON demo_order;

--changeset wangwu:heartbeat-1-2-0
INSERT INTO demo_upgrade_heartbeat (version, executed_at) VALUES ('1.2.0', NOW());
--rollback DELETE FROM demo_upgrade_heartbeat WHERE version = '1.2.0';
