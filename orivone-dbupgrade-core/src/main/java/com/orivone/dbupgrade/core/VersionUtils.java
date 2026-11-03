package com.orivone.dbupgrade.core;

import com.orivone.dbupgrade.core.StrUtils;

import java.util.regex.Pattern;

/**
 * 版本号工具，只接受点分数字格式（如 1.0.0、1.2），按段做数值比较，
 * 段数不足时缺失段按 0 处理，即 1.0 与 1.0.0 视为同一版本。
 * @author orivone
 */
public final class VersionUtils {

    /**
     * 首次安装前数据库中无版本记录时使用的初始版本号
     */
    public static final String INITIAL_VERSION = "0.0.0";

    private static final Pattern PATTERN = Pattern.compile("^\\d+(\\.\\d+)*$");

    private VersionUtils() {
    }

    /**
     * 版本号格式是否合法：必须是点分数字，不允许字母、连字符、SNAPSHOT 等
     */
    public static boolean isValid(String version) {
        return version != null && PATTERN.matcher(version.trim()).matches();
    }

    /**
     * 校验版本号格式，非法直接抛异常
     *
     * @param version 版本号
     * @param scene   场景描述，用于异常信息
     */
    public static void requireValid(String version, String scene) {
        if (!isValid(version)) {
            throw new UpgradeException("版本号[" + version + "]格式非法（" + scene + "），必须为点分数字格式，如 1.0.0");
        }
    }

    /**
     * 比较两个版本号
     *
     * @return 负数表示 left 小于 right，0 表示相等，正数表示 left 大于 right
     */
    public static int compare(String left, String right) {
        long[] l = segments(left);
        long[] r = segments(right);
        int len = Math.max(l.length, r.length);
        for (int i = 0; i < len; i++) {
            long a = i < l.length ? l[i] : 0L;
            long b = i < r.length ? r[i] : 0L;
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }

    public static boolean isGreater(String left, String right) {
        return compare(left, right) > 0;
    }

    public static boolean isGreaterOrEqual(String left, String right) {
        return compare(left, right) >= 0;
    }

    public static boolean isLessOrEqual(String left, String right) {
        return compare(left, right) <= 0;
    }

    public static boolean isEqual(String left, String right) {
        return compare(left, right) == 0;
    }

    /**
     * 归一化版本号，去掉首尾空白；空值返回初始版本
     */
    public static String normalize(String version) {
        if (version == null || StrUtils.isBlank(version)) {
            return INITIAL_VERSION;
        }
        return version.trim();
    }

    private static long[] segments(String version) {
        String v = normalize(version);
        if (!isValid(v)) {
            throw new UpgradeException("版本号[" + version + "]格式非法，无法比较");
        }
        String[] parts = v.split("\\.");
        long[] result = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Long.parseLong(parts[i]);
            } catch (NumberFormatException e) {
                throw new UpgradeException("版本号[" + version + "]数值段非法", e);
            }
        }
        return result;
    }
}
