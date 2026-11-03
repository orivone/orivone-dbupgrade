package com.orivone.dbupgrade.core.dialect.impl;

import java.util.Collections;

import com.orivone.dbupgrade.core.dialect.DbType;
import com.orivone.dbupgrade.core.dialect.UpgradeDialect;
import com.orivone.dbupgrade.core.schema.impl.MysqlSchemaMetadata;
import com.orivone.dbupgrade.core.schema.SchemaMetadata;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;

import java.util.List;

/**
 * MySQL / MariaDB 方言，另外四种方言都以它的建表语句为基准。
 * <p>
 * 基准的意思是语义等价：字段、约束一个都不能少，只允许类型写法不同。两条表设计的关键点：
 * <ul>
 *     <li>{@code dbupgrade_version} 的唯一键是 {@code group_code}，代表「每个分组一行水位」，
 *     不是全表一行——同一个库里 framework、crm 等分组可以各自推进。</li>
 *     <li>{@code dbupgrade_record} 的唯一键是 {@code (changeset_id, group_code)}，同一 changesetId
 *     在不同分组下各记各的。{@code group_code} 必须 NOT NULL：NULL 在唯一键里不等于任何值，
 *     可空时联合唯一键等于失效，同一 changeset 能反复插重复行，{@code WHERE group_code = ?}
 *     也永远匹配不到。</li>
 * </ul>
 * 只在表不存在时建表，已有环境的结构不会被框架改动。
 * <p>
 * <b>兼容 MySQL 5.7 ~ 8.x 的四个写法约定</b>（动手改之前先看这里）：
 * <ol>
 *     <li>字符集和排序规则都写明 {@code utf8mb4 / utf8mb4_general_ci}。不写的话 5.7 落 general_ci、
 *     8.0 落 0900_ai_ci，跨版本导入 5.7 直接失败。</li>
 *     <li>写明 {@code ROW_FORMAT=DYNAMIC}。联合唯一键 (255+80)×4=1340 字节，DYNAMIC 的 3072 上限够用；
 *     从 5.6 升上来的 COMPACT 实例只有 767，建表会报 1071。</li>
 *     <li>布尔列用 {@code TINYINT}，不写 {@code TINYINT(1)}——显示宽度从 8.0.19 起已弃用。</li>
 *     <li>索引写标准的 {@code UNIQUE INDEX / INDEX}，不写 {@code USING BTREE}。</li>
 * </ol>
 * @author orivone
 */
public class MysqlDialect implements UpgradeDialect {

    @Override
    public DbType dbType() {
        return DbType.MYSQL;
    }

    @Override
    public List<String> versionTableDdl(String table) {
        return Collections.singletonList("CREATE TABLE IF NOT EXISTS " + table + " ("+
                "  id varchar(40) NOT NULL," +
                "  version varchar(32) NOT NULL COMMENT '当前数据库版本'," +
                "  updated_by varchar(128) DEFAULT NULL COMMENT '最后更新节点'," +
                "  updated_at datetime DEFAULT NULL COMMENT '最后更新时间'," +
                "  group_code varchar(80) NOT NULL DEFAULT 'system' COMMENT '分组名称默认system'," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE INDEX uk_group_code (group_code)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"
                + " ROW_FORMAT=DYNAMIC COMMENT='数据库升级版本记录'");
    }


    @Override
    public List<String> recordTableDdl(String table) {
        return Collections.singletonList("CREATE TABLE IF NOT EXISTS " + table + " ("
                + " id BIGINT NOT NULL AUTO_INCREMENT,"
                + " changeset_id VARCHAR(255) NOT NULL COMMENT 'author:id',"
                + " file_path VARCHAR(512) NOT NULL COMMENT '所属脚本文件',"
                + " version VARCHAR(32) NOT NULL COMMENT '所属版本号',"
                + " checksum VARCHAR(64) DEFAULT NULL COMMENT 'SQL正文SHA-256',"
                + " run_always TINYINT NOT NULL DEFAULT 0 COMMENT '是否每次执行',"
                + " status VARCHAR(16) NOT NULL COMMENT 'EXECUTING/SUCCESS/FAILED',"
                + " executed_by VARCHAR(128) DEFAULT NULL COMMENT '执行节点',"
                + " start_time DATETIME DEFAULT NULL,"
                + " end_time DATETIME DEFAULT NULL,"
                + " error_msg TEXT COMMENT '失败原因或跳过备注',"
                + " group_code varchar(80) NOT NULL DEFAULT 'system' COMMENT '分组',"
                + " PRIMARY KEY (id),"
                + " UNIQUE INDEX uk_group_code_changeset (changeset_id,group_code),"
                + " INDEX idx_status (status),"
                + " INDEX idx_version (version)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"
                + " ROW_FORMAT=DYNAMIC COMMENT='changeset执行记录'");
    }

    @Override
    public List<String> lockTableDdl(String table) {
        return Collections.singletonList("CREATE TABLE " + table + " ("
                + "  lock_scope varchar(80)  NOT NULL COMMENT '锁范围：__GLOBAL__ 或分组名',"
                + "  lock_owner varchar(255) NOT NULL COMMENT '持有者 host:pid:uuid',"
                + "  lock_time  datetime     NOT NULL COMMENT '获取时间（数据库时间）',"
                + "  PRIMARY KEY (lock_scope)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"
                + " ROW_FORMAT=DYNAMIC COMMENT='数据库升级锁'");
    }

    @Override
    public boolean supportsHashComment() {
        return true;
    }

    @Override
    public SchemaMetadata newSchemaMetadata(JdbcExecutor jdbcExecutor) {
        return new MysqlSchemaMetadata(jdbcExecutor);
    }
}
