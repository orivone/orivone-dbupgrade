package com.orivone.dbupgrade.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 一个升级版本的定义。版本号、是否为首次安装基线、该版本包含的 SQL 文件（顺序即执行顺序）全部在 Java 侧声明。
 * SQL 文件名不解析版本号、不参与排序。
 * @author orivone
 */
public final class UpgradeVersion {

    private final String version;

    private final boolean install;

    private final List<String> filePaths;

    /**
     * @param version   版本号，点分数字格式，如 1.0.0
     * @param install   是否为首次安装基线版本
     * @param filePaths 该版本包含的脚本文件路径（classpath 路径），列表顺序即文件执行顺序
     */
    public UpgradeVersion(String version, boolean install, List<String> filePaths) {
        this.version = version == null ? null : version.trim();
        this.install = install;
        this.filePaths = filePaths == null || filePaths.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(filePaths));
    }

    public static UpgradeVersion of(String version, String... filePaths) {
        return new UpgradeVersion(version, false,
                filePaths == null ? Collections.<String>emptyList() : Arrays.asList(filePaths));
    }

    public static UpgradeVersion installOf(String version, String... filePaths) {
        return new UpgradeVersion(version, true,
                filePaths == null ? Collections.<String>emptyList() : Arrays.asList(filePaths));
    }

    public String version() {
        return version;
    }

    public boolean install() {
        return install;
    }

    public List<String> filePaths() {
        return filePaths;
    }

    @Override
    public String toString() {
        return "UpgradeVersion{version='" + version + "', install=" + install + ", filePaths=" + filePaths + '}';
    }
}
