package com.orivone.dbupgrade.spring.start;

import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.config.UpgradeSettings;
import com.orivone.dbupgrade.core.dialect.UpgradeContext;
import com.orivone.dbupgrade.core.exec.ScriptExecutor;
import com.orivone.dbupgrade.core.model.UpgradeResult;
import com.orivone.dbupgrade.core.parser.ScriptResourceLoader;
import com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry;
import com.orivone.dbupgrade.core.service.UpgradeLockManager;
import com.orivone.dbupgrade.core.service.UpgradeEngine;
import com.orivone.dbupgrade.spring.start.config.DbUpgradeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 升级入口，应用启动时跑一次。
 * <p>
 * 用 ApplicationRunner 而不是 CommandLineRunner：容器完全就绪后再动数据库，DataSource、
 * Redis（如果用了）都已经可用。顺序给到最高优先级，尽量抢在缓存预热、业务初始化之前把表结构弄好。
 * <p>
 * 执行模型是「多注册表、一次任务、顺序执行」：收集容器里所有 {@link UpgradeVersionRegistry}，
 * 按 {@code order()} 从小到大跑；每个注册表有自己的 group 与水位，互不干扰；
 * 谁失败了，按 {@code orivone.dbupgrade.fail-fast} 决定是中断启动还是记日志继续。
 * <p>
 * {@code global-lock=true}（默认）时，锁在循环外取一次、覆盖整批注册表——每组加一次的话，
 * 组间放锁的空窗会让别的节点插进来，本节点后续分组就抢不到锁、静默漏执行。
 * 改成 false 才是「每组一把锁」，不同分组可以各自并行。
 * @author orivone
 */

public class UpgradeRunner implements ApplicationRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(UpgradeRunner.class);

    private final UpgradeSettings properties;

    private final UpgradeContext upgradeJdbcContext;

    private final UpgradeLockManager upgradeLockManager;

    private final List<UpgradeVersionRegistry> versionRegistryList;

    private final ScriptExecutor scriptExecutor;

    public UpgradeRunner(List<UpgradeVersionRegistry> versionRegistryList,
                         UpgradeContext upgradeJdbcContext,
                         DbUpgradeProperties upgradeConfig,
                         UpgradeLockManager upgradeLockManager) {
        this(versionRegistryList, upgradeJdbcContext, upgradeConfig, upgradeLockManager, null);
    }

    /**
     * @param scriptExecutor 自定义脚本执行器，null 表示用内置 SQL 执行器
     */
    public UpgradeRunner(List<UpgradeVersionRegistry> versionRegistryList,
                         UpgradeContext upgradeJdbcContext,
                         DbUpgradeProperties upgradeConfig,
                         UpgradeLockManager upgradeLockManager,
                         ScriptExecutor scriptExecutor) {
        this.versionRegistryList = versionRegistryList == null
                ? new ArrayList<UpgradeVersionRegistry>() : versionRegistryList;

        this.upgradeJdbcContext = upgradeJdbcContext;
        this.upgradeLockManager = upgradeLockManager;
        this.scriptExecutor = scriptExecutor;
        this.properties = upgradeConfig.toSettings();
    }

    @Override
    public void run(ApplicationArguments args) {

        log.info("执行安装升级脚本 start>>>>>>>>");
        if (versionRegistryList.isEmpty()) {
            log.info("未声明任何 UpgradeVersionRegistry，数据库升级不会执行任何变更");
            return;
        }
        List<UpgradeVersionRegistry> sortedRegistries = versionRegistryListOrderAsc();
        List<String> registryNames = new ArrayList<String>(sortedRegistries.size());
        for (UpgradeVersionRegistry registry : sortedRegistries) {
            registryNames.add(registry.group() + "(" + registry.getClass().getSimpleName() + ")");
        }
        log.info("检测到 {} 个升级版本注册表：{}", sortedRegistries.size(), registryNames);

        boolean global = properties.isGlobalLock();
        boolean locked = false;
        if (global) {
            // 全局单例执行：锁必须覆盖整批注册表，不能每组加一次
            locked = upgradeLockManager.tryLock(UpgradeLockManager.GLOBAL_SCOPE);
            if (!locked) {
                log.warn("未获取到全局升级锁[{}]，其他节点正在执行升级，本节点跳过全部 {} 个注册表",
                        UpgradeLockManager.GLOBAL_SCOPE, sortedRegistries.size());
                return;
            }
            log.info("已获取全局升级锁，本次按顺序执行 {} 个注册表（同一时刻只允许一个升级任务）", sortedRegistries.size());
        }

        try {
            for (UpgradeVersionRegistry registry : sortedRegistries) {
                // 脚本加载器按配置决定是否放行远程脚本；默认只认 classpath 与本地文件
                UpgradeEngine engine = new UpgradeEngine(registry,
                        properties,
                        new ScriptResourceLoader(null, properties.isAllowRemoteScripts()),
                        upgradeJdbcContext,
                        upgradeLockManager,
                        scriptExecutor);

                // 各分组的目标版本不同（各自注册表的 targetVersion()，为空则取其最高版本），
                // 由 UpgradeEngine 在组内打印，这里只交代分组
                log.info("开始检查数据库版本升级：分组={}", registry.group());
                try {
                    // 全局模式：锁已由本方法在循环外持有，服务内部不再加锁
                    UpgradeResult result = global
                            ? engine.upgradeWithLockHeld()
                            : engine.upgrade();
                    log.info("数据库升级检查结束（分组={}）：{}", registry.group(), result.message());
                } catch (Exception e) {
                    log.error("数据库升级失败（分组={}）：{}", registry.group(), e.getMessage(), e);
                    if (properties.isFailFast()) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new UpgradeException("数据库升级失败", e);
                    }
                    log.error("orivone.dbupgrade.fail-fast=false，升级失败不阻断应用启动，请尽快人工处理");
                }
                log.info("执行安装升级脚本 end >>>>>>>>");
            }
        } finally {
            if (locked) {
                upgradeLockManager.unlock(UpgradeLockManager.GLOBAL_SCOPE);
            }
        }
    }

    private List<UpgradeVersionRegistry> versionRegistryListOrderAsc() {
        List<UpgradeVersionRegistry> sorted = new ArrayList<UpgradeVersionRegistry>(versionRegistryList);
        java.util.Collections.sort(sorted, new Comparator<UpgradeVersionRegistry>() {
            @Override
            public int compare(UpgradeVersionRegistry left, UpgradeVersionRegistry right) {
                return Integer.compare(left.order(), right.order());
            }
        });
        return sorted;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
