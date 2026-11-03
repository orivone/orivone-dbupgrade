--dbupgrade formatted sql
-- ============================================================================
-- 通用 · 1.3.0 增量版本
--
-- 版本号从 1.0.0 直接跳到 1.3.0：框架只按数字比较大小，不要求版本连续。
-- 中间那些号留给真实业务去占，demo 不占位。
--
-- 注意这条升级线的版本号与业务分组（1.0.0 / 1.1.0 / 1.2.0）互不相干：
-- 两个分组各有自己的水位，一个走到 1.3.0、另一个走到 1.2.0 是正常的。
-- ============================================================================

--changeset orivone:add-sys-config-remark
ALTER TABLE sys_config ADD remark VARCHAR(255);
--rollback ALTER TABLE sys_config DROP COLUMN remark;

-- 加列是加法类 DDL，重复执行会被判重跳过
--changeset orivone:add-sys-dict-remark
ALTER TABLE sys_dict ADD remark VARCHAR(255);
--rollback ALTER TABLE sys_dict DROP COLUMN remark;

-- DML 不会自动判重，WHERE 条件就是幂等保证
--changeset orivone:backfill-sys-config-remark
UPDATE sys_config SET remark = 'added by 1.3.0' WHERE remark IS NULL;
--rollback UPDATE sys_config SET remark = NULL;
