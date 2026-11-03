package com.orivone.dbupgrade.core.schema.impl;

import com.orivone.dbupgrade.core.StrUtils;

import com.orivone.dbupgrade.core.schema.AbstractSchemaMetadata;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;

/**
 * SQL Server 元数据检查器。
 * <p>
 * 表与列取自 INFORMATION_SCHEMA，索引取自 sys.indexes（INFORMATION_SCHEMA 没有索引视图），
 * 通过 OBJECT_ID(表名) 定位对象。
 * @author orivone
 */
public class SqlServerSchemaMetadata extends AbstractSchemaMetadata {

    public SqlServerSchemaMetadata(JdbcExecutor jdbcExecutor) {
        super(jdbcExecutor);
    }

    @Override
    public SqlQuery tableExistsQuery(String schema, String table) {
        if (isBlank(schema)) {
            return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                    + " WHERE TABLE_NAME = ? AND TABLE_SCHEMA = SCHEMA_NAME()", new Object[]{table});
        }
        return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                + " WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?", new Object[]{table, schema});
    }

    @Override
    public SqlQuery columnExistsQuery(String schema, String table, String column) {
        if (isBlank(schema)) {
            return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                    + " WHERE TABLE_NAME = ? AND COLUMN_NAME = ? AND TABLE_SCHEMA = SCHEMA_NAME()",
                    new Object[]{table, column});
        }
        return new SqlQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE TABLE_NAME = ? AND COLUMN_NAME = ? AND TABLE_SCHEMA = ?",
                new Object[]{table, column, schema});
    }

    @Override
    public SqlQuery indexExistsQuery(String schema, String table, String index) {
        String objectName = isBlank(schema) ? table : schema + "." + table;
        return new SqlQuery("SELECT COUNT(*) FROM sys.indexes WHERE name = ? AND object_id = OBJECT_ID(?)",
                new Object[]{index, objectName});
    }

    private static boolean isBlank(String value) {
        return value == null || StrUtils.isBlank(value);
    }
}
