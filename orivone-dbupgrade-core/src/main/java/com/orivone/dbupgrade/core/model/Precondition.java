package com.orivone.dbupgrade.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 解析后的前置条件
 * @author orivone
 */
public final class Precondition {

    private final PreconditionType type;

    private final PreconditionOnFail onFail;

    private final List<String> args;

    private final String sqlCheckSql;

    private final String raw;

    /**
     * @param type         条件类型
     * @param onFail       条件不满足时的处理策略
     * @param args         位置参数：columnExists 为 [表, 列]，indexExists 为 [表, 索引]，tableExists 为 [表]
     * @param sqlCheckSql  sqlCheck 类型的断言 SQL
     * @param raw          原始声明文本，便于日志定位
     */
    public Precondition(PreconditionType type, PreconditionOnFail onFail,
                        List<String> args, String sqlCheckSql, String raw) {
        this.type = type;
        this.onFail = onFail == null ? PreconditionOnFail.HALT : onFail;
        this.args = args == null || args.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(args));
        this.sqlCheckSql = sqlCheckSql;
        this.raw = raw;
    }

    public PreconditionType type() {
        return type;
    }

    public PreconditionOnFail onFail() {
        return onFail;
    }

    public List<String> args() {
        return args;
    }

    public String sqlCheckSql() {
        return sqlCheckSql;
    }

    public String raw() {
        return raw;
    }

    /**
     * 取第 index 个位置参数，越界返回 null
     */
    public String arg(int index) {
        return index >= 0 && index < args.size() ? args.get(index) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Precondition)) {
            return false;
        }
        Precondition other = (Precondition) o;
        return type == other.type
                && onFail == other.onFail
                && args.equals(other.args)
                && (sqlCheckSql == null ? other.sqlCheckSql == null : sqlCheckSql.equals(other.sqlCheckSql))
                && (raw == null ? other.raw == null : raw.equals(other.raw));
    }

    @Override
    public int hashCode() {
        int result = type == null ? 0 : type.hashCode();
        result = 31 * result + (onFail == null ? 0 : onFail.hashCode());
        result = 31 * result + args.hashCode();
        result = 31 * result + (sqlCheckSql == null ? 0 : sqlCheckSql.hashCode());
        result = 31 * result + (raw == null ? 0 : raw.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "Precondition{" + type + ", onFail=" + onFail + ", args=" + args
                + (sqlCheckSql == null ? "" : ", sql=" + sqlCheckSql) + '}';
    }
}
