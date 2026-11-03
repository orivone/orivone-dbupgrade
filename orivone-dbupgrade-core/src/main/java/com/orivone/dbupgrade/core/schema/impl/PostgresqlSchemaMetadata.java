package com.orivone.dbupgrade.core.schema.impl;

import com.orivone.dbupgrade.core.StrUtils;

import com.orivone.dbupgrade.core.schema.AbstractSchemaMetadata;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;

/**
 * PostgreSQL 系元数据检查器。
 * <p>
 * 适用：PostgreSQL、人大金仓 KingbaseES、openGauss、华为 GaussDB、瀚高 HighGo、Vastbase。
 * <p>
 * 表与列取自 information_schema，索引取自 pg_indexes（information_schema 没有索引视图）。
 * <p>
 * <b>标识符大小写</b>：PostgreSQL 把未加引号的标识符折叠成小写存进系统表，加引号的才原样保留。
 * 所以脚本里写 {@code ALTER TABLE DEMO_ORDER ADD ...}，实际表名是 {@code demo_order}，
 * 只按 {@code table_name = 'DEMO_ORDER'} 去查必然查不到，这条 DDL 就会被当成「尚未执行」而反复执行。
 * 这里统一用 {@code (name = ? OR name = LOWER(?))} 两段匹配：原样命中照顾「建表时加了引号」的场景，
 * 小写命中照顾「没加引号、被数据库折叠」的场景，两种写法都能正确判断。
 * @author orivone
 */
public class PostgresqlSchemaMetadata extends AbstractSchemaMetadata {

    /**
     * 表存在性判断：原名与折叠后的小写名各试一次
     */
    private static final String TABLE_EXISTS = "SELECT COUNT(*) FROM information_schema.tables"
            + " WHERE (table_name = ? OR table_name = LOWER(?))";

    private static final String COLUMN_EXISTS = "SELECT COUNT(*) FROM information_schema.columns"
            + " WHERE (table_name = ? OR table_name = LOWER(?))"
            + " AND (column_name = ? OR column_name = LOWER(?))";

    private static final String INDEX_EXISTS = "SELECT COUNT(*) FROM pg_indexes"
            + " WHERE (tablename = ? OR tablename = LOWER(?))"
            + " AND (indexname = ? OR indexname = LOWER(?))";

    public PostgresqlSchemaMetadata(JdbcExecutor jdbcExecutor) {
        super(jdbcExecutor);
    }

    @Override
    public SqlQuery tableExistsQuery(String schema, String table) {
        if (isBlank(schema)) {
            return new SqlQuery(TABLE_EXISTS + " AND table_schema = current_schema()",
                    new Object[]{table, table});
        }
        return new SqlQuery(TABLE_EXISTS + " AND (table_schema = ? OR table_schema = LOWER(?))",
                new Object[]{table, table, schema, schema});
    }

    @Override
    public SqlQuery columnExistsQuery(String schema, String table, String column) {
        if (isBlank(schema)) {
            return new SqlQuery(COLUMN_EXISTS + " AND table_schema = current_schema()",
                    new Object[]{table, table, column, column});
        }
        return new SqlQuery(COLUMN_EXISTS + " AND (table_schema = ? OR table_schema = LOWER(?))",
                new Object[]{table, table, column, column, schema, schema});
    }

    @Override
    public SqlQuery indexExistsQuery(String schema, String table, String index) {
        if (isBlank(schema)) {
            return new SqlQuery(INDEX_EXISTS + " AND schemaname = current_schema()",
                    new Object[]{table, table, index, index});
        }
        return new SqlQuery(INDEX_EXISTS + " AND (schemaname = ? OR schemaname = LOWER(?))",
                new Object[]{table, table, index, index, schema, schema});
    }

    private static boolean isBlank(String value) {
        return value == null || StrUtils.isBlank(value);
    }
}
