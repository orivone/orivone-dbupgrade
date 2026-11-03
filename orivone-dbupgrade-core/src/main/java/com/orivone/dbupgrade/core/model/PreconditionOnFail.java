package com.orivone.dbupgrade.core.model;

import com.orivone.dbupgrade.core.StrUtils;

/**
 * 前置条件不满足时的处理策略
 * @author orivone
 */
public enum PreconditionOnFail {

    /**
     * 默认：中止整个升级流程，抛异常
     */
    HALT,

    /**
     * 跳过当前 changeset，并把它标记为已执行，后续不再重试
     */
    MARK_RAN,

    /**
     * 忽略该前置条件，继续执行 changeset
     */
    CONTINUE;

    public static PreconditionOnFail of(String token) {
        if (token == null || StrUtils.isBlank(token)) {
            return HALT;
        }
        for (PreconditionOnFail value : values()) {
            if (value.name().equalsIgnoreCase(token.trim())) {
                return value;
            }
        }
        return HALT;
    }
}
