package com.orivone.dbupgrade.demo.jdbcdemo;

import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.config.UpgradeSettings;
import com.orivone.dbupgrade.core.dialect.DbType;
import com.orivone.dbupgrade.core.dialect.DialectResolver;
import com.orivone.dbupgrade.core.dialect.UpgradeDialect;
import com.orivone.dbupgrade.core.dialect.UpgradeContext;
import com.orivone.dbupgrade.core.jdbc.ConnectionProvider;
import com.orivone.dbupgrade.core.jdbc.DriverManagerConnectionProvider;
import com.orivone.dbupgrade.core.model.UpgradeResult;
import com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry;
import com.orivone.dbupgrade.core.service.DbUpgradeLockManager;
import com.orivone.dbupgrade.core.service.UpgradeLockManager;
import com.orivone.dbupgrade.core.service.UpgradeEngine;

import java.util.Optional;

/**
 * 各数据库 JDBC demo 的共享装配，避免每个 demo 复制一遍接线代码。
 * <p>
 * 装配的六个步骤（各数据库完全一致，只有 url / 方言 / 脚本目录不同）：
 * <pre>
 *   DriverManager 建连接 → ConnectionProvider → UpgradeContext（连接 + 方言 + 元数据检查器）
 *     → UpgradeSettings → 锁管理器 → UpgradeEngine → upgrade()
 * </pre>
 * 全程没有任何 Spring 组件参与。
 * @author orivone
 */
final class JdbcDemoSupport {

    private JdbcDemoSupport() {
    }

    /**
     * 连接数据库并连跑两次升级。
     *
     * @param dbType        数据库类型，取值见 {@link DbType}（mysql / postgresql / oracle / dm / sqlserver / h2）
     * @param defaultUrl    默认连接地址，可用系统属性 {@code -Ddb.url} 覆盖
     * @param defaultUser   默认账号，可用 {@code -Ddb.username} 覆盖
     * @param defaultPass   默认密码，可用 {@code -Ddb.password} 覆盖
     */
    static void run(String dbType, String defaultUrl, String defaultUser, String defaultPass) {
        run(dbType, defaultUrl, defaultUser, defaultPass, new JdbcDemoRegistry(dbType));
    }

    /**
     * 同上，但由调用方指定版本声明。H2 讲解版用它换一份演示点更全的注册表。
     */
    static void run(String dbType, String defaultUrl, String defaultUser, String defaultPass,
                    UpgradeVersionRegistry registry) {

        // 三个系统属性让 demo 可以指向任意一台库，不必改代码
        String url = System.getProperty("db.url", defaultUrl);
        String username = System.getProperty("db.username", defaultUser);
        String password = System.getProperty("db.password", defaultPass);

        System.out.println();
        System.out.println("==== 原生接入演示 · " + dbType + " ====");
        System.out.println("连接地址 : " + url);
        System.out.println("升级脚本 : upgrade-scripts/" + dbType + "/");

        // 1. 连接来源：core 只认 ConnectionProvider，底层是 DriverManager 还是连接池由调用方决定
        ConnectionProvider connectionProvider = new DriverManagerConnectionProvider(url, username, password);

        // 2. 方言：原生场景没有自动探测，显式指定；探测也支持（DialectResolver.detect），但那要多占一次连接
        Optional<DbType> parsedType = DialectResolver.parseType(dbType);
        if (!parsedType.isPresent()) {
            throw new UpgradeException("无法识别的数据库类型[" + dbType + "]，可选值：mysql / postgresql / oracle / dm / sqlserver / h2");
        }
        UpgradeDialect dialect = DialectResolver.of(parsedType.get());

        // 3. 上下文：连接 + 方言 + 元数据检查器收敛在一起，避免多数据源串库
        UpgradeContext context = new UpgradeContext(connectionProvider, dialect);

        // 4. 配置：原生场景没有 application.yml，直接 set
        UpgradeSettings properties = buildProperties(dbType);

        // 5. 锁：多节点部署用数据库单表锁；单机 demo 也可换 LocalUpgradeLockManager
        UpgradeLockManager lockManager = new DbUpgradeLockManager(context.jdbcExecutor(), dialect, properties);

        // 6. 升级入口
        UpgradeEngine service = new UpgradeEngine(registry, properties, context, lockManager);

        String target = registry.targetVersion();
        if (target == null || target.trim().isEmpty()) {
            target = "注册表最高版本";
        }

        try {
            System.out.println();
            System.out.println("第 1 次执行：0 基线 → " + target);
            print(service.upgrade());

            System.out.println("第 2 次执行：已到目标版本，期望 executed=false");
            print(service.upgrade());

            System.out.println();
            System.out.println("demo_customer 行数 : "
                    + context.jdbcExecutor().queryInt("SELECT COUNT(*) FROM demo_customer"));
        } catch (RuntimeException e) {
            System.out.println();
            System.out.println("！！ 执行失败：" + e.getMessage());
            System.out.println("！！ 请确认数据库已启动、账号可连，或用 -Ddb.url / -Ddb.username / -Ddb.password 覆盖连接信息");
            throw e;
        }
    }

    /**
     * 原生场景的配置。
     * <p>
     * core 的 {@link UpgradeSettings} 是纯 POJO，不带任何 Spring 注解：
     * Boot 工程里由 starter 绑定后传入，这里则由调用方自己填。
     * 表名沿用默认的 dbupgrade_version / dbupgrade_record / dbupgrade_lock，
     * 与 Spring 版 demo 落在同一个库里时靠 group_code 区分，互不干扰。
     */
    private static UpgradeSettings buildProperties(String dbType) {
        UpgradeSettings properties = new UpgradeSettings();

        // 数据库单表锁，多节点可用；local 仅单机
        properties.setLockType("db");
        properties.setGlobalLock(true);
        properties.setLeaseSeconds(1800L);

        // 记录表不存在时自动建
        properties.setAutoCreateTable(true);
        properties.setQueryTimeoutSeconds(600);

        // 脚本扫描根目录只用于「注册表没声明但目录里有」的告警，不参与路径拼接。
        // 按库分目录，避免上一个库的脚本被当成“未声明文件”提示。
        properties.setScriptRoot("upgrade-scripts/" + dbType);
        properties.setCheckUndeclaredFiles(true);

        // 写入 dbupgrade_record.executed_by，留空则取主机名
        properties.setOperator("jdbc-demo");

        // 目标版本不在配置里：由注册表的 targetVersion() 决定，未覆盖即取 versions() 最高版本
        return properties;
    }

    private static void print(UpgradeResult result) {
        System.out.println("  executed=" + result.executed()
                + " , from=" + result.fromVersion()
                + " , to=" + result.toVersion()
                + " , applied=" + result.appliedVersions());
    }
}
