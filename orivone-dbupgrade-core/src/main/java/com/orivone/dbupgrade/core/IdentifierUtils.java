package com.orivone.dbupgrade.core;

import com.orivone.dbupgrade.core.StrUtils;

import java.util.regex.Pattern;

/**
 * SQL 标识符处理工具，屏蔽各数据库的引号差异。
 * <p>
 * 支持剥离 MySQL 反引号、标准双引号、SQL Server 方括号三种引用形式。
 * @author orivone
 */
public final class IdentifierUtils {

    /**
     * 合法的表名：简单标识符，或 schema.table 两段式；不允许空白、引号、分号等
     */
    private static final Pattern TABLE_NAME = Pattern.compile("^[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)?$");

    private IdentifierUtils() {
    }

    /**
     * 校验并返回表名，防止配置项被拼进 SQL 造成注入。
     * <p>
     * 表名无法用 {@code ?} 参数化，只能靠白名单校验：仅允许字母、数字、下划线、美元符号，
     * 允许 {@code schema.table} 两段式。
     *
     * @param table     配置项中的表名
     * @param configKey 配置项名称，仅用于异常提示
     */
    public static String requireTableName(String table, String configKey) {
        if (table == null || StrUtils.isBlank(table)) {
            throw new UpgradeException("配置项[" + configKey + "]不能为空");
        }
        String value = StrUtils.strip(table);
        if (!TABLE_NAME.matcher(value).matches()) {
            throw new UpgradeException("配置项[" + configKey + "]不是合法的表名：" + value
                    + "，只允许字母、数字、下划线、美元符号，或 schema.table 形式");
        }
        return value;
    }

    /**
     * 提取表名中的 schema 部分，无 schema 前缀时返回 null 表示使用当前库
     */
    public static String schemaName(String table) {
        if (table == null) {
            return null;
        }
        int index = table.lastIndexOf('.');
        return index > 0 ? stripQuotes(table.substring(0, index)) : null;
    }

    /**
     * 取表名的简单名（去掉 schema 前缀与引号）
     */
    public static String simpleName(String table) {
        if (table == null) {
            return null;
        }
        int index = table.lastIndexOf('.');
        return stripQuotes(index >= 0 ? table.substring(index + 1) : table);
    }

    /**
     * 剥离标识符外层引号，支持 `x`、"x"、[x] 三种写法
     */
    public static String stripQuotes(String identifier) {
        if (identifier == null) {
            return null;
        }
        String value = StrUtils.strip(identifier);
        while (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            boolean wrapped = (first == '`' && last == '`') || (first == '"' && last == '"')
                    || (first == '[' && last == ']');
            if (!wrapped) {
                break;
            }
            value = StrUtils.strip(value.substring(1, value.length() - 1));
        }
        return value;
    }

    /**
     * 取表名最后一段的原始写法，保留外层引号。
     * <p>
     * 需要区分「写了引号」和「没写引号」时用它，例如 PostgreSQL：未加引号的标识符会被折叠成小写，
     * 加了引号才保持原大小写。{@link #simpleName} 会把引号剥掉，这层信息就丢了。
     */
    public static String rawSimpleName(String table) {
        if (table == null) {
            return null;
        }
        String value = StrUtils.strip(table);
        int index = value.lastIndexOf('.');
        return StrUtils.strip(index >= 0 ? value.substring(index + 1) : value);
    }

    /**
     * 取 schema 部分的原始写法（未剥引号），无 schema 前缀时返回 null
     */
    public static String rawSchemaName(String table) {
        if (table == null) {
            return null;
        }
        String value = StrUtils.strip(table);
        int index = value.lastIndexOf('.');
        return index > 0 ? StrUtils.strip(value.substring(0, index)) : null;
    }

    /**
     * 标识符是否被引号包裹（`x`、"x"、[x]）
     */
    public static boolean isQuoted(String identifier) {
        if (identifier == null || identifier.length() < 2) {
            return false;
        }
        char first = identifier.charAt(0);
        char last = identifier.charAt(identifier.length() - 1);
        return (first == '`' && last == '`') || (first == '"' && last == '"')
                || (first == '[' && last == ']');
    }

    /**
     * 生成约束名/索引名：prefix_表名_suffix，非法字符替换为下划线并按数据库上限截断。
     * <p>
     * Oracle 标识符历史上限 30 字符、PostgreSQL 为 63，统一按调用方传入的上限截断，
     * 避免表名较长时建表语句因约束名超长而失败。
     * <p>
     * 截断时保留原名的短哈希后缀。Oracle 的约束名在 schema 内唯一，如果只是硬截断，
     * {@code uk_dbupgrade_version_group_code} 与 {@code uk_dbupgrade_version_group_code2}
     * 这类长名字会截成同一个，第二张表直接建不起来；带上哈希后不同名字必然不同。
     *
     * @param maxLength 该数据库标识符允许的最大长度
     */
    public static String buildName(String prefix, String table, String suffix, int maxLength) {
        String simple = simpleName(table);
        String raw = prefix + "_" + (simple == null ? "table" : simple) + "_" + suffix;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
        }
        String name = sb.toString();
        if (name.length() <= maxLength) {
            return name;
        }
        String hash = String.format("%08x", name.hashCode());
        int keep = maxLength - hash.length() - 1;
        if (keep <= 0) {
            return hash.substring(hash.length() - Math.min(maxLength, hash.length()));
        }
        return name.substring(0, keep) + "_" + hash;
    }
}
