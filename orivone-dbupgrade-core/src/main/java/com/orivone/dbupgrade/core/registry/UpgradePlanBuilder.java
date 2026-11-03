package com.orivone.dbupgrade.core.registry;

import java.util.Collections;

import com.orivone.dbupgrade.core.StrUtils;

import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.VersionUtils;
import com.orivone.dbupgrade.core.model.UpgradeVersion;
import com.orivone.dbupgrade.core.parser.ScriptResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 版本注册表校验器，负责把“业务方声明的版本”变成“可安全执行的版本列表”。
 * <p>
 * 校验规则：
 * <ol>
 *     <li>版本号必须存在且为点分数字格式，可比较</li>
 *     <li>版本号必须唯一</li>
 *     <li>install=true 的版本最多一个，且必须是版本号最小的版本</li>
 *     <li>同一脚本文件不能出现在多个版本中</li>
 *     <li>注册表声明的脚本文件必须在 classpath 存在</li>
 *     <li>返回结果按版本号升序排列</li>
 * </ol>
 * @author orivone
 */
public class UpgradePlanBuilder {

    private static final Logger log = LoggerFactory.getLogger(UpgradePlanBuilder.class);

    private final ScriptResourceLoader scriptResourceLoader;

    public UpgradePlanBuilder(ScriptResourceLoader scriptResourceLoader) {
        this.scriptResourceLoader = scriptResourceLoader;
    }

    /**
     * 校验并排序版本列表
     *
     * @param versions 业务方声明的版本列表，允许为空
     * @return 校验通过并按版本号升序排列的不可变列表
     */
    public List<UpgradeVersion> validateAndSort(List<UpgradeVersion> versions) {
        if (versions == null || versions.isEmpty()) {
            return Collections.<UpgradeVersion>emptyList();
        }

        List<UpgradeVersion> effective = new ArrayList<>(versions.size());
        for (UpgradeVersion version : versions) {
            if (version == null) {
                log.warn("版本注册表中存在 null 版本定义，已忽略");
                continue;
            }
            VersionUtils.requireValid(version.version(), "版本注册表声明");
            if (version.filePaths().isEmpty()) {
                log.warn("版本[{}]未声明任何脚本文件，该版本不会执行任何变更", version.version());
            }
            effective.add(version);
        }
        if (effective.isEmpty()) {
            return Collections.<UpgradeVersion>emptyList();
        }

        // 版本号唯一
        Map<String, UpgradeVersion> byVersion = new LinkedHashMap<>();
        for (UpgradeVersion version : effective) {
            UpgradeVersion exists = byVersion.putIfAbsent(version.version(), version);
            if (exists != null) {
                throw new UpgradeException("版本注册表存在重复版本号：" + version.version());
            }
        }

        // 按版本号升序
        List<UpgradeVersion> sorted = new ArrayList<>(effective);
        sorted.sort(Comparator.comparing(UpgradeVersion::version, VersionUtils::compare));

        // install 版本唯一且最小
        List<UpgradeVersion> installVersions = sorted.stream().filter(UpgradeVersion::install).collect(java.util.stream.Collectors.toList());
        if (installVersions.size() > 1) {
            throw new UpgradeException("install=true 的版本只能有一个，当前存在：" + installVersions.stream()
                    .map(UpgradeVersion::version).collect(java.util.stream.Collectors.toList()));
        }
        if (installVersions.size() == 1 && VersionUtils.compare(installVersions.get(0).version(), sorted.get(0).version()) != 0) {
            throw new UpgradeException("install=true 的版本[" + installVersions.get(0).version()
                    + "]必须是版本号最小的版本，当前最小版本为[" + sorted.get(0).version() + "]");
        }

        // 文件唯一 + 文件存在性
        Map<String, String> fileOwners = new HashMap<>();
        for (UpgradeVersion version : sorted) {
            for (String filePath : version.filePaths()) {
                if (filePath == null || StrUtils.isBlank(filePath)) {
                    throw new UpgradeException("版本[" + version.version() + "]声明了空的脚本文件路径");
                }
                String key = normalizePath(filePath);
                String owner = fileOwners.putIfAbsent(key, version.version());
                if (owner != null) {
                    throw new UpgradeException("脚本文件[" + filePath + "]同时出现在版本[" + owner + "]和版本["
                            + version.version() + "]，一个文件只能属于一个版本");
                }
                if (!scriptResourceLoader.exists(filePath)) {
                    throw new UpgradeException("版本[" + version.version() + "]声明的脚本文件在 classpath 中不存在：" + filePath);
                }
            }
        }
        return Collections.unmodifiableList(new java.util.ArrayList<UpgradeVersion>(sorted));
    }

    /**
     * 提示 classpath 中存在但未在注册表声明的脚本文件，仅打日志不执行。
     * <p>
     * 比对用的是「相对脚本根目录的完整路径」而不是文件名。按库分目录时
     * {@code mysql/upgrade/order.sql} 与 {@code oracle/upgrade/order.sql} 同名，
     * 只比文件名会把没声明的那个也算成已声明，这条提示就形同虚设。
     */
    public void warnUndeclaredFiles(List<UpgradeVersion> versions, String scriptRoot) {
        if (versions == null || versions.isEmpty() || scriptRoot == null || StrUtils.isBlank(scriptRoot)) {
            return;
        }
        Set<String> declared = new HashSet<>();
        for (UpgradeVersion version : versions) {
            for (String filePath : version.filePaths()) {
                declared.add(normalizePath(filePath));
            }
        }
        for (String relativePath : scriptResourceLoader.listSqlPaths(scriptRoot)) {
            if (!isDeclared(declared, relativePath)) {
                log.warn("脚本文件[{}]存在于 classpath 但未在版本注册表声明，不会被执行", relativePath);
            }
        }
    }

    /**
     * 声明的完整路径是否指向这个相对路径。
     * <p>
     * 用 {@code endsWith("/" + relative)} 而不是全等，是为了兼容脚本根目录改过名、
     * 或声明里多带了目录前缀（例如 {@code db/upgrade-scripts/...}）的情况：
     * 只要尾部层级与文件名对得上，就算这个文件已经被声明过。
     */
    private static boolean isDeclared(Set<String> declared, String relativePath) {
        String relative = normalizePath(relativePath);
        for (String path : declared) {
            if (path.equals(relative) || path.endsWith("/" + relative)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePath(String path) {
        String normalized = StrUtils.strip(path).replace('\\', '/');
        if (normalized.startsWith("classpath:")) {
            normalized = normalized.substring("classpath:".length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
