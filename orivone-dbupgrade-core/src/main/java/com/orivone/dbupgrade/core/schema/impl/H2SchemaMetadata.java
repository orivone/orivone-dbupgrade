package com.orivone.dbupgrade.core.schema.impl;

import com.orivone.dbupgrade.core.StrUtils;

import com.orivone.dbupgrade.core.schema.AbstractSchemaMetadata;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;

/**
 * H2 元数据检查器，主要用于单元测试与本地验证。
 * <p>
 * H2 的 INFORMATION_SCHEMA 没有 STATISTICS 视图，索引信息在 INFORMATION_SCHEMA.INDEXES；
 * 未加引号的标识符以大写存储，因此用 UPPER() 比较。
 * @author orivone
 */
public class H2SchemaMetadata extends AbstractSchemaMetadata {

    public H2SchemaMetadata(JdbcExecutor jdbcExecutor) {
        super(jdbcExecutor);
    }

    @Override
    public SqlQuery tableExistsQuery(String schema, String table) {
        if (isBlank(schema)) {
            return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                    + " WHERE UPPER(TABLE_NAME) = UPPER(?) AND TABLE_SCHEMA = CURRENT_SCHEMA", new Object[]{table});
        }
        return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                + " WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(TABLE_SCHEMA) = UPPER(?)",
                new Object[]{table, schema});
    }

    @Override
    public SqlQuery columnExistsQuery(String schema, String table, String column) {
        if (isBlank(schema)) {
            return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                    + " WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)"
                    + " AND TABLE_SCHEMA = CURRENT_SCHEMA", new Object[]{table, column});
        }
        return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)"
                + " AND UPPER(TABLE_SCHEMA) = UPPER(?)", new Object[]{table, column, schema});
    }

    @Override
    public SqlQuery indexExistsQuery(String schema, String table, String index) {
        if (isBlank(schema)) {
            return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES"
                    + " WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(INDEX_NAME) = UPPER(?)"
                    + " AND TABLE_SCHEMA = CURRENT_SCHEMA", new Object[]{table, index});
        }
        return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES"
                + " WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(INDEX_NAME) = UPPER(?)"
                + " AND UPPER(TABLE_SCHEMA) = UPPER(?)", new Object[]{table, index, schema});
    }

    private static boolean isBlank(String value) {
        return value == null || StrUtils.isBlank(value);
    }
}
