package com.orivone.dbupgrade.core.schema.impl;

import com.orivone.dbupgrade.core.StrUtils;

import com.orivone.dbupgrade.core.schema.AbstractSchemaMetadata;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;

/**
 * Oracle 系元数据检查器，同时用于达梦 DM。
 * <p>
 * Oracle 没有 information_schema，元数据在 ALL_TABLES / ALL_TAB_COLUMNS / ALL_INDEXES 中，
 * 且未加引号的标识符以大写存储，因此统一用 UPPER() 做不区分大小写比较。
 * 当前用户用 {@code (SELECT USER FROM DUAL)} 取，达梦同样支持，避免依赖 SYS_CONTEXT 的差异。
 * @author orivone
 */
public class OracleSchemaMetadata extends AbstractSchemaMetadata {

    /**
     * 当前用户的 schema 名，Oracle 与达梦通用
     */
    private static final String CURRENT_SCHEMA = "(SELECT USER FROM DUAL)";

    public OracleSchemaMetadata(JdbcExecutor jdbcExecutor) {
        super(jdbcExecutor);
    }

    @Override
    public SqlQuery tableExistsQuery(String schema, String table) {
        return schemaQuery("SELECT COUNT(*) FROM ALL_TABLES WHERE UPPER(TABLE_NAME) = UPPER(?)", schema, table);
    }

    @Override
    public SqlQuery columnExistsQuery(String schema, String table, String column) {
        return schemaQuery("SELECT COUNT(*) FROM ALL_TAB_COLUMNS WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)",
                schema, table, column);
    }

    @Override
    public SqlQuery indexExistsQuery(String schema, String table, String index) {
        return schemaQuery("SELECT COUNT(*) FROM ALL_INDEXES WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(INDEX_NAME) = UPPER(?)",
                schema, table, index);
    }

    /**
     * 拼接 OWNER 条件：指定了 schema 用参数，否则取当前用户
     */
    public SqlQuery schemaQuery(String baseSql, String schema, Object... args) {
        Object[] params = new Object[args.length + (schema == null || StrUtils.isBlank(schema) ? 0 : 1)];
        System.arraycopy(args, 0, params, 0, args.length);
        if (schema == null || StrUtils.isBlank(schema)) {
            return new SqlQuery(baseSql + " AND OWNER = " + CURRENT_SCHEMA, params);
        }
        params[params.length - 1] = schema.toUpperCase();
        return new SqlQuery(baseSql + " AND OWNER = ?", params);
    }
}
