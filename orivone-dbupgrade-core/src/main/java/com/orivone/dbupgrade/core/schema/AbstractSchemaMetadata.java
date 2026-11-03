package com.orivone.dbupgrade.core.schema;

import com.orivone.dbupgrade.core.IdentifierUtils;
import com.orivone.dbupgrade.core.StrUtils;
import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;
import com.orivone.dbupgrade.core.model.Precondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 元数据检查器通用骨架，把与数据库无关的逻辑集中在这里。
 * <p>
 * 子类只需要提供三条元数据查询语句（表/列/索引），其余前置条件评估、DDL 存在性判断、
 * sqlCheck 断言全部复用，新增数据库不用重复实现。
 * <p>
 * 查询失败一律返回“不存在”，让 SQL 执行暴露真实问题，不做静默跳过。
 * @author orivone
 */
public abstract class AbstractSchemaMetadata implements SchemaMetadata {

    private static final Logger log = LoggerFactory.getLogger(AbstractSchemaMetadata.class);

    protected final JdbcExecutor jdbcExecutor;

    public AbstractSchemaMetadata(JdbcExecutor jdbcExecutor) {
        this.jdbcExecutor = jdbcExecutor;
    }

    /**
     * 一条元数据查询：SQL 与绑定参数。
     * <p>
     * Java 8 基线没有 record，这里用不可变类表达同样的语义。
     */
    public static final class SqlQuery {

        private final String sql;

        private final Object[] args;

        public SqlQuery(String sql, Object[] args) {
            this.sql = sql;
            this.args = args == null ? new Object[0] : args;
        }

        public String sql() {
            return sql;
        }

        public Object[] args() {
            return args;
        }

        @Override
        public String toString() {
            return "SqlQuery{sql='" + sql + "'}";
        }
    }

    /**
     * 表存在性查询
     *
     * @param schema 表名中的 schema 前缀，无前缀时为 null 表示当前库/当前用户
     * @param table  表简单名
     */
    public abstract SqlQuery tableExistsQuery(String schema, String table);

    /**
     * 列存在性查询
     */
    public abstract SqlQuery columnExistsQuery(String schema, String table, String column);

    /**
     * 索引存在性查询
     */
    public abstract SqlQuery indexExistsQuery(String schema, String table, String index);

    @Override
    public boolean tableExists(String table) {
        if (StrUtils.isBlank(table)) {
            return false;
        }
        String schema = IdentifierUtils.schemaName(table);
        String simple = IdentifierUtils.simpleName(table);
        return count(tableExistsQuery(schema, simple));
    }

    @Override
    public boolean columnExists(String table, String column) {
        if (table == null || column == null) {
            return false;
        }
        String schema = IdentifierUtils.schemaName(table);
        String simple = IdentifierUtils.simpleName(table);
        String col = IdentifierUtils.stripQuotes(column);
        return count(columnExistsQuery(schema, simple, col));
    }

    @Override
    public boolean indexExists(String table, String index) {
        if (table == null || index == null) {
            return false;
        }
        String schema = IdentifierUtils.schemaName(table);
        String simple = IdentifierUtils.simpleName(table);
        String idx = IdentifierUtils.stripQuotes(index);
        return count(indexExistsQuery(schema, simple, idx));
    }

    @Override
    public boolean evaluate(Precondition precondition) {
        if (precondition == null || precondition.type() == null) {
            return true;
        }
        switch (precondition.type()) {
            case TABLE_EXISTS:
                return tableExists(precondition.arg(0));
            case COLUMN_EXISTS:
                return columnExists(precondition.arg(0), precondition.arg(1));
            case INDEX_EXISTS:
                return indexExists(precondition.arg(0), precondition.arg(1));
            case SQL_CHECK:
                return evaluateSqlCheck(precondition);
            case UNKNOWN:
            default:
                return true;
        }
    }

    @Override
    public boolean alreadyApplied(String statement) {
        Optional<DdlInfo> parsed = DdlParser.parse(statement);
        if (!parsed.isPresent()) {
            return false;
        }
        DdlInfo info = parsed.get();
        switch (info.type()) {
            case CREATE_TABLE:
                return tableExists(info.table());
            case ADD_COLUMN:
                if (info.columns().isEmpty()) {
                    return false;
                }
                for (String column : info.columns()) {
                    if (!columnExists(info.table(), column)) {
                        return false;
                    }
                }
                return true;
            case CREATE_INDEX:
                return indexExists(info.table(), info.indexName());
            case OTHER:
            default:
                return false;
        }
    }

    @Override
    public boolean isIdempotentDdl(String statement) {
        Optional<DdlInfo> parsed = DdlParser.parse(statement);
        return parsed.isPresent() && parsed.get().type() != DdlType.OTHER;
    }

    @Override
    public Precondition firstUnsatisfied(List<Precondition> preconditions) {
        if (preconditions == null || preconditions.isEmpty()) {
            return null;
        }
        for (Precondition precondition : preconditions) {
            if (!evaluate(precondition)) {
                return precondition;
            }
        }
        return null;
    }

    /**
     * 执行统计查询，异常时返回 false
     */
    protected boolean count(SqlQuery query) {
        try {
            return jdbcExecutor.queryInt(query.sql(), query.args()) > 0;
        } catch (RuntimeException e) {
            log.debug("元数据查询失败[{}]：{}", query.sql(), e.getMessage());
            return false;
        }
    }

    /**
     * 评估 sqlCheck：结果第一行第一列，数字非 0、布尔 true、非空字符串视为满足
     */
    private boolean evaluateSqlCheck(Precondition precondition) {
        String sql = precondition.sqlCheckSql();
        if (StrUtils.isBlank(sql)) {
            return true;
        }
        Object value;
        try {
            value = jdbcExecutor.extract(sql, rs -> rs.next() ? rs.getObject(1) : null);
        } catch (RuntimeException e) {
            throw new UpgradeException("前置条件 sqlCheck 执行失败：" + sql, e);
        }
        return isTruthy(value);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0D;
        }
        String text = StrUtils.strip(String.valueOf(value));
        if (text.isEmpty()) {
            return false;
        }
        try {
            return new BigDecimal(text).compareTo(BigDecimal.ZERO) != 0;
        } catch (NumberFormatException ignored) {
            return "true".equalsIgnoreCase(text) || "y".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
        }
    }
}
