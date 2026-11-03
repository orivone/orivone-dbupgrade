package com.orivone.dbupgrade.core.model;

/**
 * 脚本类型
 * @author orivone
 */
public enum ScriptType {

    /**
     * 首次安装脚本
     */
    INSTALL,

    /**
     * 增量升级脚本
     */
    UPGRADE;

    /**
     * 根据文件所在目录推断脚本类型，仅用于日志与统计，不参与执行决策
     */
    public static ScriptType ofPath(String filePath) {
        if (filePath == null) {
            return UPGRADE;
        }
        String normalized = filePath.replace('\\', '/');
        return normalized.contains("/install/") ? INSTALL : UPGRADE;
    }
}
