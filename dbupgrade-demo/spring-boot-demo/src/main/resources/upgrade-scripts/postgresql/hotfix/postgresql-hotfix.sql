--dbupgrade formatted sql
-- ============================================================================
-- PostgreSQL · 1.2.0 收尾补丁 —— 注意它不在 upgrade/ 目录下
--
-- 这个文件同时证明两件事：
--   1) 目录名不参与任何逻辑。它放在 hotfix/ 而不是 upgrade/，框架照样读得到，
--      因为它只认注册表里的完整路径。install/、upgrade/ 全是给人看的命名习惯。
--   2) 一个版本可以跨目录取文件：1.2.0 的第一个文件在 upgrade/，第二个在这里。
-- ============================================================================

--comment 下面这条是收尾补丁，只在心跳表存在时才写

-- tableExists 的典型用法：给「依赖某张表的收尾动作」加一道保险。
-- 表不在说明前置版本没跑全，与其报错不如标记跳过，等人来看备注。
--changeset postgresql:record-hotfix
--precondition tableExists demo_upgrade_heartbeat onFail:MARK_RAN
INSERT INTO demo_upgrade_heartbeat (id, version, executed_at) VALUES (3, '1.2.0-hotfix', CURRENT_TIMESTAMP);
--rollback DELETE FROM demo_upgrade_heartbeat WHERE id = 3;

-- CONTINUE：前置条件不满足也照常往下执行，判断交给 SQL 自己。
-- 这里没有 status=9 的客户，所以 UPDATE 影响 0 行 —— 不报错、也不标记跳过，
-- 和 MARK_RAN 的区别就在这：一个装作没发生，一个照跑。
--changeset postgresql:refresh-customer-status
--precondition sqlCheck "SELECT COUNT(*) FROM demo_customer WHERE status = 9" onFail:CONTINUE
UPDATE demo_customer SET status = 1 WHERE status = 9;
--rollback UPDATE demo_customer SET status = 1;

-- splitStatements:false 把整段当成一条语句下发。
-- 函数体本身包在 $$ 里，不关掉拆分的话，body 里的分号会把它切成好几段。
--changeset postgresql:refresh-heartbeat-proc splitStatements:false
CREATE OR REPLACE FUNCTION refresh_heartbeat() RETURNS void AS $$
BEGIN
    UPDATE demo_upgrade_heartbeat SET executed_at = CURRENT_TIMESTAMP WHERE id = 1;
END;
$$ LANGUAGE plpgsql
--rollback DROP FUNCTION refresh_heartbeat();
