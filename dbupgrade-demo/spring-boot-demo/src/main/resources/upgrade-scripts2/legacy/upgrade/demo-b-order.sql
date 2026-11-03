--dbupgrade formatted sql
-- ============================================================================
-- 版本 1.1.0 · 订单模块变更
-- 注意：本文件在注册表 filePaths 中排在 demo-a-user.sql 之前。
--      文件名字母序是 a 在前，但实际执行顺序由 Java 注册表决定 —— 这正是
--      “文件名不参与排序、不承担任何语义”的直观验证点。
-- ============================================================================

-- 加法类 DDL 不写 precondition，交给执行器的存在性判断（information_schema.STATISTICS/COLUMNS）：
-- 列已存在则自动跳过，重复执行不会报 duplicate column
--changeset lisi:add-pay-status
ALTER TABLE demo_order ADD COLUMN pay_status TINYINT NOT NULL DEFAULT 0
    COMMENT '支付状态：0 未支付 / 1 已支付' AFTER amount;
--rollback ALTER TABLE demo_order DROP COLUMN pay_status;

-- 支付状态回填：演示 runAlways —— 每次扫描到本版本都会执行一次，用于维护派生数据
--changeset lisi:backfill-pay-status runAlways:true
UPDATE demo_order SET pay_status = 0 WHERE pay_status IS NULL;
--rollback UPDATE demo_order SET pay_status = 0;

-- 前置条件（columnExists）的正确用法：它表示“列存在才继续执行”。
-- 本 changeset 依赖上一条加出来的列，列不存在说明前置变更没生效，此时跳过建索引而不是硬报错。
-- 反例提醒：若把 columnExists 用在 ADD COLUMN 上，语义会变成“列不存在就跳过建列”，正好写反。
-- 另外注意：注释行不要写成 “-- precondition xxx” 这种形式，会被解析器当成真正的前置条件指令。
--changeset lisi:index-order-pay-status
--precondition columnExists demo_order pay_status onFail:MARK_RAN
CREATE INDEX idx_order_pay_status ON demo_order (pay_status);
--rollback DROP INDEX idx_order_pay_status ON demo_order;

-- 演示 runAlways 的典型用途：每次升级打一条心跳，便于观察升级发生的时刻
--changeset lisi:heartbeat-1-1-0 runAlways:true
INSERT INTO demo_upgrade_heartbeat (version, executed_at) VALUES ('1.1.0', NOW());
--rollback DELETE FROM demo_upgrade_heartbeat WHERE version = '1.1.0';

-- 演示 ignoreLines 区间：被包裹的语句在解析阶段整段丢弃，不会执行
--changeset lisi:settle-legacy-flag
--ignoreLines:start
-- 历史遗留脚本，字段已废弃，保留行号供追溯，解析器会忽略这一段
ALTER TABLE demo_order ADD COLUMN legacy_flag TINYINT NOT NULL DEFAULT 0;
--ignoreLines:end
UPDATE demo_order SET amount = amount WHERE pay_status = 1;
--rollback UPDATE demo_order SET amount = amount;
