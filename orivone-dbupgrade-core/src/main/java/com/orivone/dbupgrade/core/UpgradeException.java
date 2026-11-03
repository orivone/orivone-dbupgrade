package com.orivone.dbupgrade.core;

import com.orivone.dbupgrade.core.StrUtils;

/**
 * 数据库升级异常，统一封装升级过程中的注册表校验、脚本解析、执行等错误
 * @author orivone
 */
public class UpgradeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 出错时正在处理的版本号，可为空
     */
    private final String version;

    /**
     * 出错时正在处理的 changesetId（author:id），可为空
     */
    private final String changesetId;

    public UpgradeException(String message) {
        this(message, null, null, null);
    }

    public UpgradeException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public UpgradeException(String message, String changesetId, Throwable cause) {
        this(message, null, changesetId, cause);
    }

    public UpgradeException(String message, String version, String changesetId, Throwable cause) {
        super(buildMessage(message, version, changesetId), cause);
        this.version = version;
        this.changesetId = changesetId;
    }

    private static String buildMessage(String message, String version, String changesetId) {
        StringBuilder sb = new StringBuilder();
        if (version != null && !StrUtils.isBlank(version)) {
            sb.append("[version=").append(version).append("] ");
        }
        if (changesetId != null && !StrUtils.isBlank(changesetId)) {
            sb.append("[changeset=").append(changesetId).append("] ");
        }
        sb.append(message == null ? "数据库升级失败" : message);
        return sb.toString();
    }

    public String getVersion() {
        return version;
    }

    public String getChangesetId() {
        return changesetId;
    }
}
