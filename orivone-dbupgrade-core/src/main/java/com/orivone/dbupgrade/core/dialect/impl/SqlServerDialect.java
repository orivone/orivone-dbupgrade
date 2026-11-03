package com.orivone.dbupgrade.core.dialect.impl;

import java.util.Collections;

import com.orivone.dbupgrade.core.dialect.DbType;
import com.orivone.dbupgrade.core.dialect.UpgradeDialect;
import com.orivone.dbupgrade.core.IdentifierUtils;
import com.orivone.dbupgrade.core.schema.SchemaMetadata;
import com.orivone.dbupgrade.core.schema.impl.SqlServerSchemaMetadata;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * Microsoft SQL Server 方言（2016+）。
 * <p>
 * 和 MySQL 基准的差别：
 * <ul>
 *     <li>没有 CREATE TABLE IF NOT EXISTS，先探测表在不在，再决定建不建</li>
 *     <li>TINYINT 不带长度；时间用 DATETIME2；长文本用 NVARCHAR(MAX)</li>
 *     <li>自增用 IDENTITY(1,1)；索引单独建</li>
 *     <li>字符串统一 NVARCHAR，VARCHAR 在非 UTF-8 排序规则下会把中文丢掉</li>
 * </ul>
 * 字段与约束和基准一致：版本表 id 是 NVARCHAR(40) 存 UUID、按 group_code 唯一；
 * 记录表按 (changeset_id, group_code) 联合唯一。
 * @author orivone
 */
public class SqlServerDialect implements UpgradeDialect {

    /**
     * SQL Server 标识符上限 128 字符
     */
    private static final int MAX_IDENTIFIER = 128;

    @Override
    public DbType dbType() {
        return DbType.SQLSERVER;
    }

    @Override
    public List<String> versionTableDdl(String table) {
        return Collections.singletonList("CREATE TABLE " + table + " ("
                + " id NVARCHAR(40) NOT NULL,"
                + " version NVARCHAR(32) NOT NULL,"
                + " updated_by NVARCHAR(128) NULL,"
                + " updated_at DATETIME2 NULL,"
                + " group_code NVARCHAR(80) NOT NULL DEFAULT 'system',"
                + " CONSTRAINT " + IdentifierUtils.buildName("pk", table, "id", MAX_IDENTIFIER) + " PRIMARY KEY (id),"
                + " CONSTRAINT " + IdentifierUtils.buildName("uk", table, "group_code", MAX_IDENTIFIER)
                + " UNIQUE (group_code)"
                + ")");
    }

    @Override
    public List<String> recordTableDdl(String table) {
        List<String> ddl = new ArrayList<>(3);
        ddl.add("CREATE TABLE " + table + " ("
                + " id BIGINT IDENTITY(1,1) NOT NULL,"
                + " changeset_id NVARCHAR(255) NOT NULL,"
                + " file_path NVARCHAR(512) NOT NULL,"
                + " version NVARCHAR(32) NOT NULL,"
                + " checksum NVARCHAR(64) NULL,"
                + " run_always TINYINT NOT NULL DEFAULT 0,"
                + " status NVARCHAR(16) NOT NULL,"
                + " executed_by NVARCHAR(128) NULL,"
                + " start_time DATETIME2 NULL,"
                + " end_time DATETIME2 NULL,"
                + " error_msg NVARCHAR(MAX) NULL,"
                + " group_code NVARCHAR(80) NOT NULL DEFAULT 'system',"
                + " CONSTRAINT " + IdentifierUtils.buildName("pk", table, "id", MAX_IDENTIFIER) + " PRIMARY KEY (id),"
                + " CONSTRAINT " + IdentifierUtils.buildName("uk", table, "changeset_group", MAX_IDENTIFIER)
                + " UNIQUE (changeset_id, group_code)"
                + ")");
        ddl.add("CREATE INDEX " + IdentifierUtils.buildName("idx", table, "status", MAX_IDENTIFIER)
                + " ON " + table + " (status)");
        ddl.add("CREATE INDEX " + IdentifierUtils.buildName("idx", table, "version", MAX_IDENTIFIER)
                + " ON " + table + " (version)");
        return ddl;
    }

    @Override
    public List<String> lockTableDdl(String table) {
        // SQL Server 的 TIMESTAMP 是 rowversion（二进制版本号），时间必须用 DATETIME2
        return Collections.singletonList("CREATE TABLE " + table + " ("
                + " lock_scope NVARCHAR(80) NOT NULL,"
                + " lock_owner NVARCHAR(255) NOT NULL,"
                + " lock_time DATETIME2 NOT NULL,"
                + " CONSTRAINT " + IdentifierUtils.buildName("pk", table, "scope", MAX_IDENTIFIER)
                + " PRIMARY KEY (lock_scope))");
    }

    @Override
    public boolean supportsHashComment() {
        return false;
    }

    @Override
    public SchemaMetadata newSchemaMetadata(JdbcExecutor jdbcExecutor) {
        return new SqlServerSchemaMetadata(jdbcExecutor);
    }
}
