package com.orivone.dbupgrade.core;

import java.nio.charset.StandardCharsets;

/**
 * 字符串工具，补齐 JDK 8 缺失的常用方法。
 * <p>
 * 框架的字节码基线是 Java 8，因此不能使用 {@code String#isBlank}（11）、{@code String#strip}（11）、
 * {@code HexFormat}（17）等后续版本才有的 API。这里给出语义完全一致（isBlank / strip 按
 * {@link Character#isWhitespace(char)} 判定，与 JDK 11 实现相同）的替代实现，
 * 集中在一处，避免各文件各写一份导致语义漂移。
 * <p>
 * 所有方法对 null 入参安全（isBlank(null) = true，strip(null) = null）。
 * @author orivone
 */
public final class StrUtils {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private StrUtils() {
    }

    /**
     * 是否为空或仅含空白字符（等价于 JDK 11 的 {@code String#isBlank}）
     */
    public static boolean isBlank(CharSequence text) {
        if (text == null) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否非空且含至少一个非空白字符
     */
    public static boolean isNotBlank(CharSequence text) {
        return !isBlank(text);
    }

    /**
     * 去掉首尾空白（等价于 JDK 11 的 {@code String#strip}）。
     * <p>
     * 与 {@code trim()} 的区别：trim 只处理 {@code <= U+0020} 的字符，strip 处理所有
     * {@link Character#isWhitespace(char)} 字符（含全角空格 U+3000）。
     */
    public static String strip(String text) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        int start = 0;
        while (start < length && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        int end = length;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return (start == 0 && end == length) ? text : text.substring(start, end);
    }

    /**
     * 去掉尾部空白（等价于 JDK 11 的 {@code String#stripTrailing}）
     */
    public static String stripTrailing(String text) {
        if (text == null) {
            return null;
        }
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end == text.length() ? text : text.substring(0, end);
    }

    /**
     * 为空时返回默认值
     */
    public static String defaultIfBlank(String text, String defaultValue) {
        return isBlank(text) ? defaultValue : text;
    }

    /**
     * 字节数组转小写十六进制字符串（等价于 JDK 17 的 {@code HexFormat.of().formatHex})
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX_DIGITS[value >>> 4];
            chars[i * 2 + 1] = HEX_DIGITS[value & 0x0F];
        }
        return new String(chars);
    }

    /**
     * UTF-8 字节数组转字符串
     */
    public static String fromUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 压缩空白并截断，用于日志里输出 SQL 摘要
     */
    public static String abbreviate(String sql, int maxLength) {
        if (sql == null) {
            return "";
        }
        String flat = strip(sql.replaceAll("\\s+", " "));
        return flat.length() <= maxLength ? flat : flat.substring(0, maxLength) + "...";
    }
}
