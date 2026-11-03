package com.orivone.dbupgrade.core.schema.impl;

import com.orivone.dbupgrade.core.StrUtils;

import com.orivone.dbupgrade.core.schema.AbstractSchemaMetadata;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;

/**
 * MySQL 元数据检查器，查 information_schema 判断表、列、索引在不在。
 * <p>
 * 索引信息在 information_schema.STATISTICS，这是 MySQL 自己的视图，PG、Oracle 各有各的查法。
 * @author orivone
 */
public class MysqlSchemaMetadata extends AbstractSchemaMetadata {

    public MysqlSchemaMetadata(JdbcExecutor jdbcExecutor) {
        super(jdbcExecutor);
    }

    @Override
    public SqlQuery tableExistsQuery(String schema, String table) {
        if (isBlank(schema)) {
            return new SqlQuery("SELECT COUNT(*) FROM information_schema.TABLES"
                    + " WHERE TABLE_NAME = ? AND TABLE_SCHEMA = DATABASE()", new Object[]{table});
        }
        return new SqlQuery("SELECT COUNT(*) FROM information_schema.TABLES"
                + " WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?", new Object[]{table, schema});
    }

    @Override
    public SqlQuery columnExistsQuery(String schema, String table, String column) {
        if (isBlank(schema)) {
            return new SqlQuery("SELECT COUNT(*) FROM information_schema.COLUMNS"
                    + " WHERE TABLE_NAME = ? AND COLUMN_NAME = ? AND TABLE_SCHEMA = DATABASE()",
                    new Object[]{table, column});
        }
        return new SqlQuery("SELECT COUNT(*) FROM information_schema.COLUMNS"
                + " WHERE TABLE_NAME = ? AND COLUMN_NAME = ? AND TABLE_SCHEMA = ?",
                new Object[]{table, column, schema});
    }

    @Override
    public SqlQuery indexExistsQuery(String schema, String table, String index) {
        if (isBlank(schema)) {
            return new SqlQuery("SELECT COUNT(*) FROM information_schema.STATISTICS"
                    + " WHERE TABLE_NAME = ? AND INDEX_NAME = ? AND TABLE_SCHEMA = DATABASE()",
                    new Object[]{table, index});
        }
        return new SqlQuery("SELECT COUNT(*) FROM information_schema.STATISTICS"
                + " WHERE TABLE_NAME = ? AND INDEX_NAME = ? AND TABLE_SCHEMA = ?",
                new Object[]{table, index, schema});
    }

    private static boolean isBlank(String value) {
        return value == null || StrUtils.isBlank(value);
    }
}
