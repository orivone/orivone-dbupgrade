package com.orivone.dbupgrade.core.parser;

import com.orivone.dbupgrade.core.StrUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 语句拆分器。
 * <p>
 * 逐字符扫描，正确处理：
 * <ul>
 *     <li>单引号、双引号、反引号内的分隔符不拆分（支持反斜杠转义与引号双写转义）</li>
 *     <li>行注释（-- 与 #）与块注释（/* *&#47;）内的分隔符不拆分，注释整体剔除</li>
 *     <li>自定义 endDelimiter，可多字符（如 $$、//）</li>
 * </ul>
 * splitStatements=false 时整段作为一条语句返回，供存储过程等场景使用。
 * @author orivone
 */
public final class SqlStatementSplitter {

    private SqlStatementSplitter() {
    }

    /**
     * 拆分脚本为独立语句
     *
     * @param script          脚本内容
     * @param endDelimiter    语句分隔符，默认 ;
     * @param splitStatements 是否拆分
     * @return 去掉空白语句后的语句列表，顺序与脚本中一致
     */
    public static List<String> split(String script, String endDelimiter, boolean splitStatements) {
        return split(script, endDelimiter, splitStatements, true);
    }

    /**
     * 拆分脚本为独立语句，可指定是否将 # 作为行注释。
     * <p>
     * # 是 MySQL 专有行注释；PostgreSQL 的 #&gt; / #&gt;&gt; 是 JSON 操作符，按注释剔除会破坏 SQL，
     * 因此非 MySQL 方言调用时应传 false。
     *
     * @param hashLineComment 是否把 # 开头的行视为注释
     */
    public static List<String> split(String script, String endDelimiter, boolean splitStatements,
                                     boolean hashLineComment) {
        List<String> statements = new ArrayList<>();
        if (script == null || StrUtils.isBlank(script)) {
            return statements;
        }
        if (!splitStatements || endDelimiter == null || endDelimiter.isEmpty()) {
            String whole = StrUtils.strip(script);
            if (!isBlankStatement(whole)) {
                statements.add(whole);
            }
            return statements;
        }

        StringBuilder current = new StringBuilder(script.length());
        int i = 0;
        int n = script.length();
        while (i < n) {
            char c = script.charAt(i);

            // 行注释 --
            if (c == '-' && i + 1 < n && script.charAt(i + 1) == '-') {
                while (i < n && script.charAt(i) != '\n') {
                    i++;
                }
                current.append(' ');
                continue;
            }
            // 行注释 #（仅 MySQL 语法族）
            if (hashLineComment && c == '#') {
                while (i < n && script.charAt(i) != '\n') {
                    i++;
                }
                current.append(' ');
                continue;
            }
            // 块注释 /* ... */
            if (c == '/' && i + 1 < n && script.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(script.charAt(i) == '*' && script.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(n, i + 2);
                current.append(' ');
                continue;
            }
            // 引号块，原样保留
            if (c == '\'' || c == '"' || c == '`') {
                i = appendQuoted(script, i, current);
                continue;
            }
            // 分隔符
            if (script.startsWith(endDelimiter, i)) {
                addStatement(statements, current);
                current.setLength(0);
                i += endDelimiter.length();
                continue;
            }
            current.append(c);
            i++;
        }
        addStatement(statements, current);
        return statements;
    }

    /**
     * 拷贝一个引号块（含起止引号），返回下一个扫描位置
     */
    private static int appendQuoted(String script, int start, StringBuilder out) {
        char quote = script.charAt(start);
        int i = start;
        int n = script.length();
        out.append(quote);
        i++;
        while (i < n) {
            char ch = script.charAt(i);
            // 反斜杠转义（反引号内不识别转义，用双写）
            if (ch == '\\' && quote != '`' && i + 1 < n) {
                out.append(ch).append(script.charAt(i + 1));
                i += 2;
                continue;
            }
            out.append(ch);
            if (ch == quote) {
                // 引号双写转义，如 '' 或 ""
                if (i + 1 < n && script.charAt(i + 1) == quote) {
                    out.append(quote);
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return i;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = StrUtils.strip(current.toString());
        if (!isBlankStatement(statement)) {
            statements.add(statement);
        }
    }

    /**
     * 只有空白或只有分隔符的语句视为空语句
     */
    private static boolean isBlankStatement(String statement) {
        if (statement == null || StrUtils.isBlank(statement)) {
            return true;
        }
        for (int i = 0; i < statement.length(); i++) {
            char c = statement.charAt(i);
            if (!Character.isWhitespace(c) && c != ';') {
                return false;
            }
        }
        return true;
    }
}
