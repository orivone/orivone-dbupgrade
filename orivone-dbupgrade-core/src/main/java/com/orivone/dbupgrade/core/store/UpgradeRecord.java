package com.orivone.dbupgrade.core.store;

import java.time.LocalDateTime;

/**
 * 一个 changeset 的执行记录，对应 dbupgrade_record 的一行。
 * <p>
 * 不可变：字段全 final、只有读取方法。Java 8 没有 record，方法名照 record 的风格写，
 * 读起来清楚，将来换 record 也不影响调用方。
 * @author orivone
 */
public final class UpgradeRecord {

    private final long id;

    private final String changesetId;

    private final String filePath;

    private final String version;

    private final String checksum;

    private final boolean runAlways;

    private final String status;

    private final String executedBy;

    private final LocalDateTime startTime;

    private final LocalDateTime endTime;

    private final String errorMsg;

    public UpgradeRecord(long id, String changesetId, String filePath, String version, String checksum,
                         boolean runAlways, String status, String executedBy,
                         LocalDateTime startTime, LocalDateTime endTime, String errorMsg) {
        this.id = id;
        this.changesetId = changesetId;
        this.filePath = filePath;
        this.version = version;
        this.checksum = checksum;
        this.runAlways = runAlways;
        this.status = status;
        this.executedBy = executedBy;
        this.startTime = startTime;
        this.endTime = endTime;
        this.errorMsg = errorMsg;
    }

    /**
     * 主键
     */
    public long id() {
        return id;
    }

    /**
     * changeset 标识，author:id
     */
    public String changesetId() {
        return changesetId;
    }

    /**
     * 所属脚本文件
     */
    public String filePath() {
        return filePath;
    }

    /**
     * 所属版本号
     */
    public String version() {
        return version;
    }

    /**
     * 内容校验和
     */
    public String checksum() {
        return checksum;
    }

    /**
     * 是否每次执行
     */
    public boolean runAlways() {
        return runAlways;
    }

    /**
     * EXECUTING / SUCCESS / FAILED
     */
    public String status() {
        return status;
    }

    /**
     * 执行节点
     */
    public String executedBy() {
        return executedBy;
    }

    /**
     * 开始时间
     */
    public LocalDateTime startTime() {
        return startTime;
    }

    /**
     * 结束时间
     */
    public LocalDateTime endTime() {
        return endTime;
    }

    /**
     * 失败原因，或跳过执行的备注
     */
    public String errorMsg() {
        return errorMsg;
    }

    @Override
    public String toString() {
        return "UpgradeRecord{id=" + id + ", changesetId='" + changesetId + "', version='" + version
                + "', status='" + status + "'}";
    }
}
