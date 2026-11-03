package com.orivone.dbupgrade.core.service;

import com.orivone.dbupgrade.core.StrUtils;

import com.orivone.dbupgrade.core.UpgradeException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * changeset 内容校验和工具。
 * <p>
 * 计算前先做归一化（统一换行、去掉行尾空白、去掉首尾空白），
 * 使格式化调整不会误触发 checksum 冲突，只对真正的语义修改（SQL 内容变化）敏感。
 * @author orivone
 */
public final class ChecksumUtils {

    private ChecksumUtils() {
    }

    /**
     * 计算 SQL 正文的 SHA-256，输出 64 位小写十六进制
     */
    public static String sha256(String sql) {
        String normalized = normalize(sql);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return StrUtils.toHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new UpgradeException("当前 JVM 不支持 SHA-256，无法计算 checksum", e);
        }
    }

    /**
     * 归一化 SQL 文本，供 checksum 使用
     */
    public static String normalize(String sql) {
        if (sql == null) {
            return "";
        }
        String unified = sql.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = unified.split("\n", -1);
        StringBuilder sb = new StringBuilder(unified.length());
        for (String line : lines) {
            sb.append(StrUtils.stripTrailing(line)).append('\n');
        }
        return StrUtils.strip(sb.toString());
    }

    /**
     * 两个 checksum 是否一致，空值视为不一致
     */
    public static boolean same(String left, String right) {
        return left != null && !StrUtils.isBlank(left) && left.equalsIgnoreCase(right);
    }
}
