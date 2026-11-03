--dbupgrade formatted sql
-- ============================================================================
-- H2 · 1.2.0 条件 DDL
-- 演示 precondition sqlCheck：查询结果第一列非 0 才执行，为 0 时按 onFail:MARK_RAN
-- 记成「已执行」后跳过，不算失败。
--
-- 基线没有插入任何未支付订单，所以这里预期「跳过并标记」，
-- 记录表里会多出一条 status=MARK_RAN 的记录。
-- 想看到它真正执行，先插一条 pay_status=0 的订单再跑即可。
-- ============================================================================

--changeset h2:index-unpaid-order
--precondition sqlCheck "SELECT COUNT(*) FROM demo_order WHERE pay_status = 0" onFail:MARK_RAN
CREATE INDEX idx_demo_order_unpaid ON demo_order (pay_status);
--rollback DROP INDEX idx_demo_order_unpaid;
