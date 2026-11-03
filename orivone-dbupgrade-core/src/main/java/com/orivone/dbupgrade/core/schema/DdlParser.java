package com.orivone.dbupgrade.core.schema;

import java.util.Collections;

import com.orivone.dbupgrade.core.StrUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DDL 语句解析器，识别建表、加列、建索引三类可安全重跑的加法类 DDL。
 * <p>
 * 只做“能识别则判断、识别不了就原样执行”的保守处理，绝不替业务改写 SQL。
 * 减法类 DDL（DROP / RENAME / MODIFY）一律归为 OTHER，不做存在性判断。
 * @author orivone
 */
public final class DdlParser {

    private static final String IDENT = "[`\"\\[\\]\\w$.]+";

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)^CREATE\\s+(?:TEMPORARY\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(" + IDENT + ")");

    private static final Pattern CREATE_INDEX = Pattern.compile(
            "(?is)^CREATE\\s+(?:UNIQUE\\s+|FULLTEXT\\s+|SPATIAL\\s+)?INDEX\\s+([`\"\\w$]+)\\s+(?:USING\\s+\\w+\\s+)?ON\\s+(" + IDENT + ")");

    private static final Pattern ALTER_TABLE = Pattern.compile(
            "(?is)^ALTER\\s+TABLE\\s+(" + IDENT + ")\\s+(.+)$");

    /**
     * 加列动作。中间的 {@code IF NOT EXISTS} 必须吃掉：PostgreSQL / H2 允许
     * {@code ADD COLUMN IF NOT EXISTS c int}，不吃的话列名会被解析成 {@code IF}，
     * 后续列存在性判断永远查不到，于是这条 DDL 每次都被当成「还没执行」。
     */
    private static final Pattern ADD_COLUMN_ACTION = Pattern.compile(
            "(?is)^ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?([`\"\\w$]+)\\s+.+$");

    private static final Pattern ADD_NON_COLUMN_ACTION = Pattern.compile(
            "(?is)^ADD\\s+(?:CONSTRAINT|PRIMARY|UNIQUE|INDEX|KEY|FULLTEXT|SPATIAL|FOREIGN|CHECK|PARTITION)\\b.*");

    private DdlParser() {
    }

    /**
     * 解析 DDL 语句
     *
     * @param statement 单条 SQL
     * @return 解析结果，非加法类 DDL 或无法识别时返回 {@link DdlType#OTHER}
     */
    public static Optional<DdlInfo> parse(String statement) {
        if (statement == null || StrUtils.isBlank(statement)) {
            return Optional.empty();
        }
        String sql = StrUtils.strip(statement);

        Matcher createTable = CREATE_TABLE.matcher(sql);
        if (createTable.find()) {
            return Optional.of(new DdlInfo(DdlType.CREATE_TABLE, clean(createTable.group(1)), Collections.<String>emptyList(), null));
        }

        Matcher createIndex = CREATE_INDEX.matcher(sql);
        if (createIndex.find()) {
            return Optional.of(new DdlInfo(DdlType.CREATE_INDEX, clean(createIndex.group(2)), Collections.<String>emptyList(),
                    clean(createIndex.group(1))));
        }

        Matcher alterTable = ALTER_TABLE.matcher(sql);
        if (alterTable.find()) {
            String table = clean(alterTable.group(1));
            List<String> columns = parseAddedColumns(alterTable.group(2));
            if (!columns.isEmpty()) {
                return Optional.of(new DdlInfo(DdlType.ADD_COLUMN, table, columns, null));
            }
            return Optional.of(DdlInfo.other());
        }
        return Optional.of(DdlInfo.other());
    }

    /**
     * 解析 ALTER TABLE 的动作列表，仅当所有动作都是 ADD COLUMN 时返回列名列表，否则返回空表示不判断
     */
    private static List<String> parseAddedColumns(String actions) {
        List<String> parts = splitTopLevel(actions);
        if (parts.isEmpty()) {
            return Collections.<String>emptyList();
        }
        List<String> columns = new ArrayList<>(parts.size());
        for (String part : parts) {
            String action = StrUtils.strip(part);
            if (action.isEmpty() || ADD_NON_COLUMN_ACTION.matcher(action).matches()) {
                return Collections.<String>emptyList();
            }
            Matcher matcher = ADD_COLUMN_ACTION.matcher(action);
            if (!matcher.matches()) {
                return Collections.<String>emptyList();
            }
            columns.add(clean(matcher.group(1)));
        }
        return columns;
    }

    /**
     * 按顶层逗号切分，忽略括号内与引号内的逗号
     */
    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    /**
     * 去掉标识符上的引号。
     * <p>
     * 必须按 {@code .} 分段处理，不能把整串当成一个引用标识符：
     * {@code "PUBLIC"."DEMO_ORDER"} 首尾都是双引号，整串剥一次会得到
     * {@code PUBLIC"."DEMO_ORDER}，后半段还挂着半个引号，存在性判断必然落空。
     */
    private static String clean(String identifier) {
        if (identifier == null) {
            return null;
        }
        String value = StrUtils.strip(identifier);
        String[] parts = value.split("\\.", -1);
        if (parts.length == 1) {
            return stripWrapping(value);
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(stripWrapping(StrUtils.strip(parts[i])));
        }
        return sb.toString();
    }

    /**
     * 反复剥掉最外层的 {@code `x`} / {@code "x"} / {@code [x]}
     */
    private static String stripWrapping(String value) {
        String result = value;
        while (result.length() >= 2) {
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            boolean wrapped = (first == '`' && last == '`') || (first == '"' && last == '"')
                    || (first == '[' && last == ']');
            if (!wrapped) {
                break;
            }
            result = StrUtils.strip(result.substring(1, result.length() - 1));
        }
        return result;
    }
}
