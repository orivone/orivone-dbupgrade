--dbupgrade formatted sql
-- ============================================================================
-- MySQL · 1.2.0 视图与条件 DDL（本版本里第一个执行的文件）
--
-- 这一版集中演示「靠内容而不是靠版本号判断要不要重跑」的几种写法。
-- ============================================================================

-- 视图没有可靠的存在性判断（CREATE OR REPLACE 每次都成功），
-- 只能靠 checksum 控制：内容不变就一直跳过，改一个列、调一下顺序就会重跑。
--changeset mysql:refresh-customer-order-view runOnChange:true
CREATE OR REPLACE VIEW v_demo_customer_order AS
SELECT c.id           AS customer_id,
       c.name         AS customer_name,
       c.phone        AS phone,
       c.email        AS email,
       o.id           AS order_id,
       o.amount       AS amount,
       o.pay_status   AS pay_status
FROM demo_customer c
LEFT JOIN demo_order o ON o.customer_id = c.id;
--rollback DROP VIEW IF EXISTS v_demo_customer_order;

-- sqlCheck 满足（有未支付订单）→ 真的执行
--changeset mysql:index-order-unpaid
--precondition sqlCheck "SELECT COUNT(*) FROM demo_order WHERE pay_status = 0" onFail:MARK_RAN
CREATE INDEX idx_demo_order_unpaid ON demo_order (pay_status);
--rollback DROP INDEX idx_demo_order_unpaid ON demo_order;

-- sqlCheck 不满足（没有 status=9 的客户）→ 按 MARK_RAN 记成「已执行」后跳过。
-- 记录状态仍是成功，备注里写着跳过原因，下次不再重试。
--changeset mysql:index-customer-vip
--precondition sqlCheck "SELECT COUNT(*) FROM demo_customer WHERE status = 9" onFail:MARK_RAN
CREATE INDEX idx_demo_customer_vip ON demo_customer (status);
--rollback DROP INDEX idx_demo_customer_vip ON demo_order;

-- indexExists 保护「依赖该索引的语句」：索引不在就跳过（说明 1.1.0 没跑全）
--changeset mysql:normalize-customer-email
--precondition indexExists demo_customer idx_demo_customer_email onFail:MARK_RAN
UPDATE demo_customer SET email = LOWER(email) WHERE email <> LOWER(email);
--rollback UPDATE demo_customer SET email = LOWER(email);

-- endDelimiter：把分隔符换成两个分号。正文里的单分号因此不会被当成语句边界，
-- 适合体里带分号的脚本；换完记得整段的分隔符都要跟着变。
--changeset mysql:recount-customer-order endDelimiter:;;
UPDATE demo_order SET pay_status = 0 WHERE pay_status IS NULL;;
UPDATE demo_customer SET status = 1 WHERE status IS NULL;;
--rollback UPDATE demo_order SET pay_status = 0 WHERE pay_status IS NULL;
--rollback UPDATE demo_customer SET status = 1 WHERE status IS NULL;

-- 普通 changeset 写的心跳：第一次落下时间后就不再变，
-- 和 1.1.0 里那条 runAlways 的一对比，就看出「跳过」与「重跑」的差别了。
--changeset mysql:heartbeat-advanced
INSERT INTO demo_upgrade_heartbeat (id, version, executed_at) VALUES (2, '1.2.0', CURRENT_TIMESTAMP);
--rollback DELETE FROM demo_upgrade_heartbeat WHERE id = 2;
