--dbupgrade formatted sql
-- ============================================================================
-- 通用 · 1.0.0 框架基础表（UpgradeVersion 的 install 标志 = true）
--
-- 这份脚本放在 common/system/ 下，不按数据库分目录 —— 它只用
-- CREATE TABLE / VARCHAR / INT / PRIMARY KEY 这些各库都认的写法，
-- 没有自增、没有时间类型、没有引擎与字符集。
--
-- 顺带说明目录这件事：脚本放哪儿都行，common/system/install/ 只是命名习惯，
-- 框架只按注册表里写的完整路径去读。业务脚本放 {库}/upgrade/、
-- 这份放 common/system/install/，纯属给人看的分类。
-- ============================================================================

--changeset orivone:create-sys-config
CREATE TABLE sys_config (
    config_key   VARCHAR(64)  NOT NULL,
    config_value VARCHAR(255),
    status       INT          DEFAULT 1 NOT NULL,
    PRIMARY KEY (config_key)
);
--rollback DROP TABLE sys_config;

--changeset orivone:create-sys-dict
CREATE TABLE sys_dict (
    dict_code VARCHAR(64)  NOT NULL,
    dict_name VARCHAR(128),
    PRIMARY KEY (dict_code)
);
--rollback DROP TABLE sys_dict;

-- 两条语句写在一个 changeset 里：执行器按分号拆开后顺序执行，共用同一个连接
--changeset orivone:seed-sys-config
INSERT INTO sys_config (config_key, config_value, status) VALUES ('app.name', 'orivone-dbupgrade-demo', 1);
INSERT INTO sys_config (config_key, config_value, status) VALUES ('app.locale', 'zh_CN', 1);
--rollback DELETE FROM sys_config WHERE config_key IN ('app.name', 'app.locale');

--changeset orivone:seed-sys-dict
INSERT INTO sys_dict (dict_code, dict_name) VALUES ('yes_no', '是否');
--rollback DELETE FROM sys_dict WHERE dict_code = 'yes_no';
