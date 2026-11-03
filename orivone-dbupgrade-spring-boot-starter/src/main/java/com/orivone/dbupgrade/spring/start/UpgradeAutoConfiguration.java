package com.orivone.dbupgrade.spring.start;

import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.config.UpgradeSettings;
import com.orivone.dbupgrade.core.dialect.DialectResolver;
import com.orivone.dbupgrade.core.dialect.UpgradeDialect;
import com.orivone.dbupgrade.core.dialect.UpgradeContext;
import com.orivone.dbupgrade.core.exec.ScriptExecutor;
import com.orivone.dbupgrade.core.jdbc.DataSourceConnectionProvider;
import com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry;
import com.orivone.dbupgrade.core.service.DbUpgradeLockManager;
import com.orivone.dbupgrade.core.service.LocalUpgradeLockManager;
import com.orivone.dbupgrade.core.service.UpgradeLockManager;
import com.orivone.dbupgrade.spring.start.config.DbUpgradeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据库升级的自动装配。
 * <p>
 * 生效条件：容器里存在 {@link DataSource} Bean，且 {@code orivone.dbupgrade.enabled} 不是 false。
 * 只是 classpath 上有 JDBC 是不够的——{@code javax.sql.DataSource} 由 JDK 自带，
 * 单靠它做条件永远成立，会把「压根没接数据库」的应用也拉进来，然后因为找不到数据源而启动失败。
 * <p>
 * {@code afterName} 列出两代 Boot 的数据源自动配置类名，确保 {@code @ConditionalOnBean} 评估时
 * 数据源已经注册。用类名字符串而不是 {@code after = ...class}，是因为 Boot 4 把这批类从
 * {@code org.springframework.boot.autoconfigure.jdbc} 挪到了 {@code org.springframework.boot.jdbc.autoconfigure}，
 * 写死类型就会断代。
 * <p>
 * 通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册，不依赖宿主应用扫描本包；用 {@code @AutoConfiguration} 而不是 {@code @Configuration}，
 * 这是 Boot 2.7 起的标准入口，语义等价于 {@code proxyBeanMethods = false}。
 * <p>
 * 能同时兼容 2.7 / 3.x / 4.x，靠的是只用三代共有的 API，并且<b>绝不引用</b>
 * {@code org.springframework.boot.autoconfigure.jdbc.*}——那批类在 Boot 4 搬去了
 * {@code org.springframework.boot.jdbc.autoconfigure.*}，引了就断代。数据源一律用
 * {@code ObjectProvider<DataSource>} 运行期按类型找，顺带不受自动配置顺序影响。
 * <p>
 * 几处设计取舍：
 * <ol>
 *     <li>不注册 JdbcTemplate：core 自带 {@code JdbcExecutor}，用不上，也不会跟业务侧的 Bean 撞名</li>
 *     <li>不提供默认注册表：业务声明几个就跑几个，免得「框架默认实现 + 业务实现」两个同类型 Bean 打架</li>
 *     <li>数据源、执行器、方言、元数据统一收进 {@link UpgradeContext}，多数据源不串库</li>
 * </ol>
 * 其余组件都带 {@code @ConditionalOnMissingBean}，想换随时覆盖；加一种数据库只要声明一个
 * {@link UpgradeDialect} Bean（多个时取第一个并告警）。
 * @author orivone
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"})
@EnableConfigurationProperties(DbUpgradeProperties.class)
@ConditionalOnClass(DataSource.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "orivone.dbupgrade", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UpgradeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(UpgradeAutoConfiguration.class);

    /**
     * 升级上下文：选定数据源 → 包成 ConnectionProvider → 解析方言 → 创建元数据检查器。
     * <p>
     * 容器中若存在业务方自定义的 {@link UpgradeDialect} Bean，优先使用它（取第一个并提示），
     * 这样新增数据库只需自行实现方言，无需改动框架。
     */
    @Bean
    @ConditionalOnMissingBean
    public UpgradeContext upgradeJdbcContext(BeanFactory beanFactory,
                                                 ObjectProvider<DataSource> dataSourceProvider,
                                                 ObjectProvider<UpgradeDialect> dialectProvider,
                                                 DbUpgradeProperties upgradeConfig) {
        UpgradeSettings upgradeProperties = upgradeConfig.toSettings();
        DataSource dataSource = resolveDataSource(beanFactory, dataSourceProvider, upgradeProperties);
        List<UpgradeDialect> dialects = dialectProvider.orderedStream()
                .collect(Collectors.toList());
        UpgradeDialect dialect;
        if (dialects.isEmpty()) {
            dialect = DialectResolver.resolve(dataSource, upgradeProperties.getDbType());
        } else {
            dialect = dialects.get(0);
            if (dialects.size() > 1) {
                log.warn("检测到 {} 个 UpgradeDialect Bean，使用第一个：{}", dialects.size(), dialect.getClass().getName());
            }
            log.info("数据库升级方言由自定义 Bean 提供：{}", dialect.getClass().getName());
        }
        log.info("数据库升级组件就绪：方言={}，记录表={}、{}", dialect.dbType().description(),
                upgradeProperties.getVersionTable(),
                upgradeProperties.getRecordTable());
        return new UpgradeContext(new DataSourceConnectionProvider(dataSource), dialect);
    }

    /**
     * 升级入口，注册成 Bean 后由 Boot 在容器就绪时回调（ApplicationRunner）。
     * <p>
     * 这里显式声明，而不是在 {@link UpgradeRunner} 上打 {@code @Component}：本模块靠自动配置注册，
     * 不依赖宿主应用扫描本包。
     * <p>
     * 容器里有 {@link ScriptExecutor} 就用它，没有就用内置的 SQL 执行器——想跑 SQL 之外的东西时
     * 声明一个 Bean 即可，不必改装配代码。
     */
    @Bean
    @ConditionalOnMissingBean
    public UpgradeRunner upgradeRunner(List<UpgradeVersionRegistry> versionRegistryList,
                                       UpgradeContext upgradeJdbcContext,
                                       DbUpgradeProperties upgradeConfig,
                                       UpgradeLockManager upgradeLockManager,
                                       ObjectProvider<ScriptExecutor> scriptExecutorProvider) {
        return new UpgradeRunner(versionRegistryList, upgradeJdbcContext, upgradeConfig, upgradeLockManager,
                scriptExecutorProvider.getIfAvailable());
    }

    /**
     * 选定升级所使用的数据源。
     * <p>
     * 多数据源场景必须显式指定 orivone.dbupgrade.datasource-bean，
     * 否则直接报错而不是随便挑一个——挑错会导致版本记录与业务库不一致。
     */
    private static DataSource resolveDataSource(BeanFactory beanFactory,
                                                ObjectProvider<DataSource> dataSourceProvider,
                                                UpgradeSettings upgradeProperties) {
        String beanName = upgradeProperties.getDatasourceBean();
        if (beanName != null && !beanName.trim().isEmpty()) {
            if (!beanFactory.containsBean(beanName.trim())) {
                throw new UpgradeException("配置项[orivone.dbupgrade.datasource-bean]指定的 Bean[" + beanName + "]不存在");
            }
            Object bean = beanFactory.getBean(beanName.trim());
            if (!(bean instanceof DataSource)) {
                throw new UpgradeException("配置项[orivone.dbupgrade.datasource-bean]指定的 Bean[" + beanName + "]不是 DataSource");
            }
            log.info("数据库升级使用指定数据源 Bean：{}", beanName);
            return (DataSource) bean;
        }
        DataSource dataSource;
        try {
            dataSource = dataSourceProvider.getIfAvailable();
        } catch (Exception e) {
            throw new UpgradeException("容器中存在多个 DataSource，请通过 orivone.dbupgrade.datasource-bean "
                    + "指定数据库升级使用哪一个", e);
        }
        if (dataSource == null) {
            throw new UpgradeException("未找到可用的 DataSource，数据库升级无法执行");
        }
        return dataSource;
    }

    /**
     * 数据库单表锁（默认）。
     * <p>
     * 不依赖任何中间件，多节点可用：锁表 dbupgrade_lock 以「行是否存在」表达占用，
     * 通过 lock_scope 主键唯一约束做并发互斥。
     * <p>
     * {@code matchIfMissing = true} 表示不配置 lock-type 时走这里。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "orivone.dbupgrade", name = "lock-type", havingValue = "db", matchIfMissing = true)
    static class DbLockConfiguration {

        @Bean
        @ConditionalOnMissingBean(UpgradeLockManager.class)
        public UpgradeLockManager upgradeLockManager(UpgradeContext upgradeJdbcContext, DbUpgradeProperties upgradeConfig) {
            UpgradeSettings upgradeProperties = upgradeConfig.toSettings();
            log.info("数据库升级锁实现：db（锁表={}，{}）", upgradeProperties.getLockTable(),
                    upgradeProperties.isGlobalLock() ? "全局单例执行" : "按 lock_scope 分别加锁");
            return new DbUpgradeLockManager(upgradeJdbcContext.jdbcExecutor(),
                    upgradeJdbcContext.dialect(), upgradeProperties);
        }
    }

    /**
     * 单机内存锁：仅供单机部署或测试使用，多节点等于没有锁
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "orivone.dbupgrade", name = "lock-type", havingValue = "local")
    static class LocalLockConfiguration {

        @Bean
        @ConditionalOnMissingBean(UpgradeLockManager.class)
        public UpgradeLockManager upgradeLockManager() {
            return new LocalUpgradeLockManager();
        }
    }

    /**
     * lock-type=redis，但 classpath 缺少 orivone-dbupgrade-lock-redisson 模块：直接失败并给出明确指引，不静默降级。
     * <p>
     * Redis 锁的装配由 {@code orivone-dbupgrade-lock-redisson} 模块自带的
     * {@code RedissonLockAutoConfiguration} 负责——本 starter 不认识 Redisson，
     * 也不依赖它，所以「用默认数据库锁」的项目不会被动引入 14.5 MB 的 Redis 客户端。
     * <p>
     * 这里的兜底用的是 {@link ConditionalOnMissingClass} 的<b>字符串</b>形式：
     * 类名以常量形式参与条件判断，不需要编译期存在该类型，因此不会把可选依赖变回强依赖。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("com.orivone.dbupgrade.lock.redisson.RedissonUpgradeLockManager")
    @ConditionalOnProperty(prefix = "orivone.dbupgrade", name = "lock-type", havingValue = "redis")
    static class MissingRedisLockConfiguration {

        @Bean
        @ConditionalOnMissingBean(UpgradeLockManager.class)
        public UpgradeLockManager upgradeLockManager() {
            throw new UpgradeException("orivone.dbupgrade.lock-type=redis，但 classpath 缺少 orivone-dbupgrade-lock-redisson 模块；"
                    + "请引入 com.orivone:orivone-dbupgrade-lock-redisson（并自行提供 RedissonClient Bean），"
                    + "或改用 lock-type=db（默认，无需额外依赖）/ lock-type=local（仅单机）");
        }
    }
}
