package com.orivone.dbupgrade.core.parser;

import java.util.Collections;

import com.orivone.dbupgrade.core.StrUtils;

import com.orivone.dbupgrade.core.model.Changeset;
import com.orivone.dbupgrade.core.model.ParsedSqlFile;
import com.orivone.dbupgrade.core.model.Precondition;
import com.orivone.dbupgrade.core.model.PreconditionOnFail;
import com.orivone.dbupgrade.core.model.PreconditionType;
import com.orivone.dbupgrade.core.service.ChecksumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatted SQL 解析器
 * <p>
 * 支持指令：
 * <ul>
 *     <li>--dbupgrade formatted sql：文件标识，识别后开始处理</li>
 *     <li>--changeset author:id [runAlways:true] [runOnChange:true] [endDelimiter:;] [splitStatements:false]</li>
 *     <li>--rollback SQL：收集反向 SQL，当前版本不自动执行</li>
 *     <li>--precondition &lt;type&gt; &lt;args...&gt; [onFail:HALT|MARK_RAN|CONTINUE]</li>
 *     <li>--ignoreLines:start / --ignoreLines:end：区间内内容忽略</li>
 *     <li>--comment：注释，忽略</li>
 * </ul>
 * 规则：文件内 changeset 顺序即执行顺序；--changeset 之前的内容忽略；非法行只 warn 不中断解析。
 * @author orivone
 */
public class FormattedSqlParser {

    private static final Logger log = LoggerFactory.getLogger(FormattedSqlParser.class);

    /**
     * 文件标识行
     */
    private static final Pattern HEADER_PATTERN =
            Pattern.compile("^--\\s*dbupgrade\\s+formatted\\s+sql\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * changeset 属性，如 runOnChange:true、endDelimiter:"$$"
     */
    private static final Pattern ATTRIBUTE_PATTERN =
            Pattern.compile("([A-Za-z][\\w-]*)\\s*:\\s*(\"[^\"]*\"|'[^']*'|\\S+)");

    /**
     * onFail 声明
     */
    private static final Pattern ON_FAIL_PATTERN =
            Pattern.compile("(?i)onFail\\s*:\\s*([A-Za-z_]+)");

    /**
     * 带引号或普通的位置参数
     */
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    private static final String CHANGESET = "changeset";
    private static final String ROLLBACK = "rollback";
    private static final String PRECONDITION = "precondition";
    private static final String COMMENT = "comment";
    private static final String DBUPGRADE = "dbupgrade";
    private static final String IGNORE_START = "ignoreLines:start";
    private static final String IGNORE_END = "ignoreLines:end";

    /**
     * 解析单个 SQL 文件
     *
     * @param filePath 文件路径，仅用于记录与日志
     * @param content  文件内容
     * @return 解析结果，无有效 changeset 时返回空列表
     */
    public ParsedSqlFile parse(String filePath, String content) {
        List<Changeset> changesets = new ArrayList<>();
        if (content == null || StrUtils.isBlank(content)) {
            log.warn("升级脚本[{}]内容为空，已跳过", filePath);
            return new ParsedSqlFile(filePath, changesets);
        }

        String text = stripBom(content);
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        boolean headerSeen = false;
        boolean ignoreBlock = false;
        Changeset.Builder builder = null;

        for (int index = 0; index < lines.length; index++) {
            String rawLine = lines[index];
            int lineNo = index + 1;
            String trimmed = StrUtils.strip(rawLine);

            if (ignoreBlock) {
                if (isDirective(trimmed, IGNORE_END)) {
                    ignoreBlock = false;
                }
                continue;
            }
            if (isDirective(trimmed, IGNORE_START)) {
                ignoreBlock = true;
                continue;
            }
            if (HEADER_PATTERN.matcher(trimmed).matches()) {
                headerSeen = true;
                continue;
            }
            if (trimmed.startsWith("--")) {
                String directive = StrUtils.strip(trimmed.substring(2));
                if (startsWithWord(directive, CHANGESET)) {
                    flush(builder, changesets, filePath);
                    builder = parseChangesetHeader(StrUtils.strip(directive.substring(CHANGESET.length())), filePath, lineNo);
                    continue;
                }
                if (startsWithWord(directive, ROLLBACK)) {
                    if (builder == null) {
                        log.warn("升级脚本[{}]第 {} 行：--rollback 不在任何 changeset 内，已忽略", filePath, lineNo);
                    } else {
                        builder.addRollbackSql(StrUtils.strip(directive.substring(ROLLBACK.length())));
                    }
                    continue;
                }
                if (startsWithWord(directive, PRECONDITION)) {
                    if (builder == null) {
                        log.warn("升级脚本[{}]第 {} 行：--precondition 不在任何 changeset 内，已忽略", filePath, lineNo);
                    } else {
                        Precondition precondition = parsePrecondition(StrUtils.strip(directive.substring(PRECONDITION.length())), filePath, lineNo);
                        if (precondition != null) {
                            builder.addPrecondition(precondition);
                        }
                    }
                    continue;
                }
                if (startsWithWord(directive, COMMENT) || startsWithWord(directive, DBUPGRADE)) {
                    continue;
                }
                // 普通注释：保留到正文，由拆分器剔除
                if (builder != null) {
                    builder.appendLine(rawLine);
                }
                continue;
            }

            if (builder == null) {
                // --changeset 之前的内容一律忽略
                continue;
            }
            builder.appendLine(rawLine);
        }
        flush(builder, changesets, filePath);

        if (!headerSeen && !changesets.isEmpty()) {
            log.warn("升级脚本[{}]缺少文件标识行 --dbupgrade formatted sql，建议补充", filePath);
        }
        log.debug("升级脚本[{}]解析完成，changeset 数量：{}", filePath, changesets.size());
        return new ParsedSqlFile(filePath, changesets);
    }

    /**
     * 收尾当前 changeset
     */
    private void flush(Changeset.Builder builder, List<Changeset> changesets, String filePath) {
        if (builder == null) {
            return;
        }
        String rawSql = builder.rawSql();
        if (!hasExecutableSql(rawSql)) {
            log.warn("升级脚本[{}]中 changeset[{}] 正文为空或仅含注释，已忽略", filePath, builder.changesetId());
            return;
        }
        // checksum 只覆盖「脚本里写了什么」，包含改写前的原始正文。
        // 执行期 rewriteSql 的改写结果不计入 checksum：改写通常是运行环境相关的
        // （表前缀、多租户 schema），若纳入 checksum，换环境就会让全部历史
        // changeset 判定为“内容变了”，在无 runOnChange 时直接阻断启动。
        builder.checksum(ChecksumUtils.sha256(rawSql));
        changesets.add(builder.build());
    }

    /**
     * 解析 --changeset 行
     */
    private Changeset.Builder parseChangesetHeader(String text, String filePath, int lineNo) {
        if (StrUtils.isBlank(text)) {
            log.warn("升级脚本[{}]第 {} 行：--changeset 缺少 author:id，已忽略该 changeset", filePath, lineNo);
            return null;
        }
        int split = firstWhitespaceIndex(text);
        String idToken = split < 0 ? text : text.substring(0, split);
        String attributes = split < 0 ? "" : StrUtils.strip(text.substring(split));

        String changesetId = normalizeChangesetId(idToken);
        if (changesetId == null) {
            log.warn("升级脚本[{}]第 {} 行：changesetId[{}]非法，必须为 author:id 格式，已忽略该 changeset", filePath, lineNo, idToken);
            return null;
        }

        Changeset.Builder builder = Changeset.builder()
                .changesetId(changesetId)
                .filePath(filePath)
                .line(lineNo);

        if (!StrUtils.isBlank(attributes)) {
            Matcher matcher = ATTRIBUTE_PATTERN.matcher(attributes);
            while (matcher.find()) {
                String key = matcher.group(1).toLowerCase();
                String value = unquote(matcher.group(2));
                switch (key) {
                    case "runalways":
                        builder.runAlways(parseBoolean(value, false));
                        break;
                    case "runonchange":
                        builder.runOnChange(parseBoolean(value, false));
                        break;
                    case "enddelimiter":
                        builder.endDelimiter(value);
                        break;
                    case "splitstatements":
                        builder.splitStatements(parseBoolean(value, true));
                        break;
                    default:
                        log.debug("升级脚本[{}]第 {} 行：changeset 属性[{}]未知，已忽略", filePath, lineNo, key);
                        break;
                }
            }
        }
        return builder;
    }

    /**
     * 解析 --precondition 行
     */
    private Precondition parsePrecondition(String text, String filePath, int lineNo) {
        if (StrUtils.isBlank(text)) {
            log.warn("升级脚本[{}]第 {} 行：--precondition 缺少条件类型，已忽略", filePath, lineNo);
            return null;
        }
        String raw = text;
        PreconditionOnFail onFail = PreconditionOnFail.HALT;
        Matcher onFailMatcher = ON_FAIL_PATTERN.matcher(text);
        if (onFailMatcher.find()) {
            onFail = PreconditionOnFail.of(onFailMatcher.group(1));
            text = StrUtils.strip((text.substring(0, onFailMatcher.start()) + " " + text.substring(onFailMatcher.end())));
        }

        int split = firstWhitespaceIndex(text);
        String typeToken = split < 0 ? text : text.substring(0, split);
        String rest = split < 0 ? "" : StrUtils.strip(text.substring(split));
        PreconditionType type = PreconditionType.of(typeToken);
        if (type == PreconditionType.UNKNOWN) {
            log.warn("升级脚本[{}]第 {} 行：未知前置条件类型[{}]，已忽略该条件", filePath, lineNo, typeToken);
            return new Precondition(PreconditionType.UNKNOWN, onFail, Collections.<String>emptyList(), null, raw);
        }
        if (type == PreconditionType.SQL_CHECK) {
            String sql = unquote(rest);
            if (StrUtils.isBlank(sql)) {
                log.warn("升级脚本[{}]第 {} 行：sqlCheck 缺少断言 SQL，已忽略该条件", filePath, lineNo);
                return null;
            }
            return new Precondition(type, onFail, Collections.<String>emptyList(), sql, raw);
        }

        List<String> args = tokens(rest);
        int required;
        switch (type) {
            case TABLE_EXISTS:
                required = 1;
                break;
            case COLUMN_EXISTS:
            case INDEX_EXISTS:
                required = 2;
                break;
            default:
                required = 0;
                break;
        }
        if (args.size() < required) {
            log.warn("升级脚本[{}]第 {} 行：前置条件[{}]参数不足，期望 {} 个，实际 {} 个，已忽略该条件",
                    filePath, lineNo, type, required, args.size());
            return null;
        }
        return new Precondition(type, onFail, args, null, raw);
    }

    /**
     * 判断脚本是否包含可执行 SQL（剔除注释与分隔符后非空）
     */
    public static boolean hasExecutableSql(String sql) {
        if (sql == null || StrUtils.isBlank(sql)) {
            return false;
        }
        String stripped = StrUtils.strip(sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ")
                .replaceAll("(?m)^\\s*#.*$", " "));
        if (stripped.isEmpty()) {
            return false;
        }
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (!Character.isWhitespace(c) && c != ';') {
                return true;
            }
        }
        return false;
    }

    private static String stripBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }

    private static boolean isDirective(String trimmed, String directive) {
        return trimmed.startsWith("--") && startsWithWord(StrUtils.strip(trimmed.substring(2)), directive);
    }

    /**
     * 判断指令文本是否以关键字开头（关键字后必须是空白或结束，避免 changesetXxx 误命中）
     */
    private static boolean startsWithWord(String text, String word) {
        if (text == null || text.length() < word.length()) {
            return false;
        }
        if (!text.regionMatches(true, 0, word, 0, word.length())) {
            return false;
        }
        return text.length() == word.length() || Character.isWhitespace(text.charAt(word.length()));
    }

    private static int firstWhitespaceIndex(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeChangesetId(String token) {
        if (token == null) {
            return null;
        }
        String value = StrUtils.strip(token);
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            return null;
        }
        String author = StrUtils.strip(value.substring(0, colon));
        String id = StrUtils.strip(value.substring(colon + 1));
        if (author.isEmpty() || id.isEmpty() || id.contains(":")) {
            return null;
        }
        return author + ":" + id;
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || StrUtils.isBlank(value)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(StrUtils.strip(value)) || "1".equals(StrUtils.strip(value));
    }

    private static String unquote(String value) {
        if (value == null) {
            return null;
        }
        String v = StrUtils.strip(value);
        if (v.length() >= 2) {
            char first = v.charAt(0);
            char last = v.charAt(v.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    private static List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || StrUtils.isBlank(text)) {
            return result;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                result.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                result.add(matcher.group(2));
            } else {
                result.add(matcher.group(3));
            }
        }
        return result;
    }
}
