package com.orivone.dbupgrade.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次升级的结果。
 * <p>
 * {@link #executed()} 回答的是「这次到底动没动库」：版本号没变、但补跑了几个未完成的 changeset
 * 也算 true。把它和「什么都没做」分开，调用方和日志才不会误判。
 * @author orivone
 */
public final class UpgradeResult {

    private final boolean executed;

    private final String fromVersion;

    private final String toVersion;

    private final List<String> appliedVersions;

    private final String message;

    /**
     * @param executed        是否真正执行了升级
     * @param fromVersion     升级前数据库版本
     * @param toVersion       升级后数据库版本
     * @param appliedVersions 本次成功推进的版本列表
     * @param message         结果说明，用于日志
     */
    public UpgradeResult(boolean executed, String fromVersion, String toVersion,
                         List<String> appliedVersions, String message) {
        this.executed = executed;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.appliedVersions = appliedVersions == null || appliedVersions.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(appliedVersions));
        this.message = message;
    }

    public static UpgradeResult skipped(String fromVersion, String message) {
        return new UpgradeResult(false, fromVersion, fromVersion, Collections.<String>emptyList(), message);
    }

    public static UpgradeResult success(String fromVersion, String toVersion, List<String> appliedVersions) {
        return new UpgradeResult(true, fromVersion, toVersion, appliedVersions,
                "升级完成：" + fromVersion + " -> " + toVersion);
    }

    /**
     * 版本号没变，但补跑该版本内未完成的 changeset（中断恢复、上次失败重试）。
     * <p>
     * 这种情况必须与「完全无事可做」区分开：虽然版本没推进，但确实执行了 SQL，
     * 若返回 skipped 会让调用方误判成「什么都没发生」。
     */
    public static UpgradeResult retried(String version, int changesetCount) {
        return new UpgradeResult(true, version, version, Collections.<String>emptyList(),
                "版本未变化（" + version + "），补跑 " + changesetCount + " 个未完成的 changeset");
    }

    public boolean executed() {
        return executed;
    }

    public String fromVersion() {
        return fromVersion;
    }

    public String toVersion() {
        return toVersion;
    }

    public List<String> appliedVersions() {
        return appliedVersions;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "UpgradeResult{executed=" + executed + ", from='" + fromVersion + "', to='" + toVersion
                + "', applied=" + appliedVersions + ", message='" + message + "'}";
    }
}
