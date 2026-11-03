--dbupgrade formatted sql
-- ============================================================================
-- MySQL · 1.1.0 订单模块（本版本里第一个执行的文件）
--
-- 注意排序：文件名是 mysql-upgrade-order.sql，字母序排在
-- mysql-upgrade-customer.sql 之后，注册表却把它放在 1.1.0 的列表首位。
-- 实际执行顺序就是本文件在前 —— 文件名不参与排序，也不解析版本号。
-- ============================================================================

-- 加法类 DDL 交给执行器判断：列已存在就跳过这条语句，不会抛 duplicate column
--changeset mysql:add-order-pay-status
ALTER TABLE demo_order ADD COLUMN pay_status TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态：0 未支付 / 1 已支付' AFTER amount;
--rollback ALTER TABLE demo_order DROP COLUMN pay_status;

-- 建索引同样被识别：索引在就跳过，与上面那条互不牵连
--changeset mysql:index-order-customer
CREATE INDEX idx_demo_order_customer ON demo_order (customer_id);
--rollback DROP INDEX idx_demo_order_customer ON demo_order;

-- runAlways：已成功过也照样执行，同一版本被重新执行时再跑一遍。
-- 典型用途是刷新派生数据、补上空值 —— 这类语句自己必须幂等。
--changeset mysql:backfill-order-pay-status runAlways:true
UPDATE demo_order SET pay_status = 0 WHERE pay_status IS NULL;
--rollback UPDATE demo_order SET pay_status = 0;

-- 把 2 号订单标成已支付，给 1.2.0 的 sqlCheck 留下「有未支付订单」的差异化数据
--changeset mysql:mark-order-paid
UPDATE demo_order SET pay_status = 1 WHERE id = 2;
--rollback UPDATE demo_order SET pay_status = 0 WHERE id = 2;

-- columnExists 的语义是「列存在才继续」，只能用在依赖既有列的语句上。
-- 反例：拿它去保护 ADD COLUMN，就变成「列不存在就跳过建列」，永远加不上。
-- 这条条件这里一定成立（上一句刚加的列），所以索引会真的建出来。
--changeset mysql:index-order-pay-status
--precondition columnExists demo_order pay_status onFail:MARK_RAN
CREATE INDEX idx_demo_order_pay_status ON demo_order (pay_status);
--rollback DROP INDEX idx_demo_order_pay_status ON demo_order;

-- runAlways 的第二种用法：每次执行都刷新同一行心跳。
-- 行数始终是 1，executed_at 每次都会变 —— 这是「确实又跑了一遍」的直接证据。
--changeset mysql:heartbeat-order runAlways:true
DELETE FROM demo_upgrade_heartbeat WHERE id = 1;
INSERT INTO demo_upgrade_heartbeat (id, version, executed_at) VALUES (1, '1.1.0', CURRENT_TIMESTAMP);
--rollback DELETE FROM demo_upgrade_heartbeat WHERE id = 1;

-- ignoreLines 区间在解析阶段整段丢弃：不执行、也不进 checksum。
-- 适合保留历史语句的痕迹，又不想真的跑它。
--changeset mysql:settle-legacy-flag
--ignoreLines:start
ALTER TABLE demo_order ADD COLUMN legacy_flag TINYINT NOT NULL DEFAULT 0;
--ignoreLines:end
UPDATE demo_order SET amount = amount WHERE pay_status = 1;
--rollback UPDATE demo_order SET amount = amount;
