package com.orivone.dbupgrade.core.model;

/**
 * 运行期 changeset 元数据，绑定所属版本
 * @author orivone
 */
public final class VersionedChangeset {

    private final Changeset body;

    private final String version;

    public VersionedChangeset(Changeset body, String version) {
        this.body = body;
        this.version = version;
    }

    /**
     * 解析后的 changeset 正文
     */
    public Changeset body() {
        return body;
    }

    /**
     * 所属版本号
     */
    public String version() {
        return version;
    }

    public String changesetId() {
        return body.changesetId();
    }

    public String filePath() {
        return body.filePath();
    }

    public String checksum() {
        return body.checksum();
    }

    public boolean runAlways() {
        return body.runAlways();
    }

    public boolean runOnChange() {
        return body.runOnChange();
    }
}
