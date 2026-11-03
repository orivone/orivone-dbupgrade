package com.orivone.dbupgrade.core.store;

import java.time.LocalDateTime;

/**
 * 当前数据库版本记录（对应 dbupgrade_version，每个分组一行）。
 * <p>
 * 一个 group_code 对应一行，主键 id 是审计用的 UUID，业务语义上唯一的键是 group_code。
 * 记录本身不携带 group_code 字段：调用方都是先按 group 查询，再使用结果。
 * @author orivone
 */
public final class UpgradeVersionRecord {

    private final String id;

    private final String version;

    private final String updatedBy;

    private final LocalDateTime updatedAt;

    public UpgradeVersionRecord(String id, String version, String updatedBy, LocalDateTime updatedAt) {
        this.id = id;
        this.version = version;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /**
     * 主键（UUID），仅用于定位与审计
     */
    public String id() {
        return id;
    }

    /**
     * 已升到的版本号
     */
    public String version() {
        return version;
    }

    /**
     * 最后推进版本的节点
     */
    public String updatedBy() {
        return updatedBy;
    }

    /**
     * 最后推进时间
     */
    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "UpgradeVersionRecord{version='" + version + "', updatedBy='" + updatedBy + "'}";
    }
}
