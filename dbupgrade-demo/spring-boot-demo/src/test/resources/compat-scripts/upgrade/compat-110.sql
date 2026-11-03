--dbupgrade formatted sql
-- ============================================================================
-- 兼容性测试用脚本 · 版本 1.1.0
-- 只做一件事：加一列。测试靠「这一列是否真的存在」判断 DDL 是否落地，
-- 而不是只看版本表 —— 版本表推进了但 DDL 没执行，是这类框架最危险的失败态。
-- ============================================================================

--changeset compat:add-remark
ALTER TABLE demo_item ADD COLUMN remark VARCHAR(255) DEFAULT NULL;
--rollback ALTER TABLE demo_item DROP COLUMN remark;
