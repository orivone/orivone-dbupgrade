package com.orivone.dbupgrade.core.model;

import com.orivone.dbupgrade.core.StrUtils;

/**
 * 前置条件类型
 * @author orivone
 */
public enum PreconditionType {

    /**
     * 表是否存在，语法：--precondition tableExists t_user
     */
    TABLE_EXISTS,

    /**
     * 列是否存在，语法：--precondition columnExists t_user phone
     */
    COLUMN_EXISTS,

    /**
     * 索引是否存在，语法：--precondition indexExists t_user idx_phone
     */
    INDEX_EXISTS,

    /**
     * 自定义 SQL 断言，语法：--precondition sqlCheck "SELECT COUNT(*) FROM ..."
     * 结果第一行第一列：数字大于 0、布尔 true、非空字符串视为满足
     */
    SQL_CHECK,

    /**
     * 未知类型，安全忽略（不阻断执行），仅记录 warn 日志
     */
    UNKNOWN;

    /**
     * 归一化解析：忽略大小写、下划线、连字符，如 columnExists / column_exists / COLUMN-EXISTS 等价
     */
    public static PreconditionType of(String token) {
        if (token == null || StrUtils.isBlank(token)) {
            return UNKNOWN;
        }
        String normalized = token.trim().replace("-", "").replace("_", "").toLowerCase();
        switch (normalized) {
            case "tableexists":
                return TABLE_EXISTS;
            case "columnexists":
                return COLUMN_EXISTS;
            case "indexexists":
                return INDEX_EXISTS;
            case "sqlcheck":
                return SQL_CHECK;
            default:
                return UNKNOWN;
        }
    }
}
