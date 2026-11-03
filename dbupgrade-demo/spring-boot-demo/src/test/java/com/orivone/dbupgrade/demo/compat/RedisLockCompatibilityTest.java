package com.orivone.dbupgrade.demo.compat;

import com.orivone.dbupgrade.core.service.UpgradeLockManager;
import com.orivone.dbupgrade.lock.redisson.RedissonUpgradeLockManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Redis 分布式锁的真实回归（需要本地 Redis）。
 * <p>
 * 覆盖的是「自动装配」而非「锁算法」：改造后 Redis 锁的装配由
 * {@code orivone-dbupgrade-lock-redisson} 模块自带的 {@code RedissonLockAutoConfiguration} 负责，
 * starter 完全不认识 Redisson。所以这里要证明的是链路真的接上了：
 * 引入模块 + {@code lock-type=redis} → 容器里拿到的是 {@link RedissonUpgradeLockManager}
 * → 升级流程走通 → 结束后锁被释放。
 * <p>
 * <b>为什么必须有第二个用例</b>
 * <p>
 * 只有「升级成功」的话，一个根本没加锁的假实现也能通过。所以第二个用例先由另一个
 * Redis 客户端占住同名锁，再启动容器，断言本节点<b>跳过升级且不写任何记录</b> ——
 * 这才是锁存在的意义（多节点同时启动时只能有一个执行）。
 * <p>
 * <b>没有 Redis 的环境</b>
 * <p>
 * 用 {@code assumeTrue} 跳过而不是失败：CI 机器通常没有 Redis，
 * 强行要求会让整条回归线变红。本地有 Redis 时自动生效：
 * <pre>
 *   mvn -pl dbupgrade-demo/spring-boot-demo test
 *   # 需要换地址/密码：
 *   mvn -pl dbupgrade-demo/spring-boot-demo test -Dredis.address=redis://10.0.0.5:6379 -Dredis.password=xxx
 * </pre>
 * @author orivone
 */
class RedisLockCompatibilityTest {

    private static final String ADDRESS = System.getProperty("redis.address", "redis://127.0.0.1:6379");

    private static final String PASSWORD = System.getProperty("redis.password", "123456");

    /**
     * 专用锁前缀，避免与业务实际使用的 orivone:dbupgrade:lock 撞上
     */
    private static final String LOCK_KEY = "orivone.dbupgrade:test:lock";

    private static boolean redisAvailable;

    private static String unavailableReason;

    @BeforeAll
    static void probeRedis() {
        RedissonClient client = null;
        try {
            client = newClient();
            client.getBucket("orivone.dbupgrade:test:probe").set("ok");
            redisAvailable = true;
        } catch (Exception e) {
            redisAvailable = false;
            unavailableReason = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            shutdownQuietly(client);
        }
    }

    @Test
    @DisplayName("lock-type=redis：装配 Redisson 锁，升级成功后锁被释放")
    void upgradeWithRedisLock() throws Exception {
        assumeRedis();

        ConfigurableApplicationContext context = start("redis_ok");
        RedissonClient client = context.getBean(RedissonClient.class);
        try {
            UpgradeLockManager lockManager = context.getBean(UpgradeLockManager.class);
            assertTrue(lockManager instanceof RedissonUpgradeLockManager,
                    "配了 lock-type=redis 却拿到 " + lockManager.getClass().getName()
                            + "，说明 orivone-dbupgrade-lock-redisson 的自动配置没生效");

            DataSource dataSource = context.getBean(DataSource.class);
            assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM dbupgrade_version"),
                    "版本水位应当只有 1 行");
            assertEquals(1, count(dataSource,
                            "SELECT COUNT(*) FROM dbupgrade_version WHERE version = '1.1.0'"),
                    "应当推进到 1.1.0");
            assertEquals(3, count(dataSource, "SELECT COUNT(*) FROM dbupgrade_record"),
                    "install 2 个 changeset + 1.1.0 的 1 个，应当有 3 行记录");

            // 升级跑完必须释放锁，否则下次启动要等租期（默认 1800 秒）才能再升
            assertFalse(client.getLock(LOCK_KEY + ":" + UpgradeLockManager.GLOBAL_SCOPE).isLocked(),
                    "升级结束后锁必须已释放");
        } finally {
            context.close();
        }
    }

    @Test
    @DisplayName("锁被其他节点占用：本节点跳过升级，不写任何记录")
    void skipWhenLockHeldByAnotherNode() throws Exception {
        assumeRedis();

        // 用独立客户端实例占锁：Redisson 的锁可重入是按 clientId:threadId 判定的，
        // 换个 client 就不会被当成「自己已经持有」而重入成功。
        RedissonClient blocker = newClient();
        RLock lock = blocker.getLock(LOCK_KEY + ":" + UpgradeLockManager.GLOBAL_SCOPE);
        assertTrue(lock.tryLock(0L, 60L, TimeUnit.SECONDS), "测试前置条件：应当能占住锁");
        try {
            ConfigurableApplicationContext context = start("redis_busy");
            try {
                DataSource dataSource = context.getBean(DataSource.class);
                // 抢锁失败时框架立即返回，连升级记录表都不会创建；
                // 所以这里不能直接 COUNT(*)，表不存在本身就是「没有任何记录」的最强证据
                assertEquals(0, countIfExists(dataSource, "SELECT COUNT(*) FROM dbupgrade_version"),
                        "锁被其他节点持有时不应推进版本水位");
                assertEquals(0, countIfExists(dataSource, "SELECT COUNT(*) FROM dbupgrade_record"),
                        "锁被其他节点持有时不应执行任何 changeset");
            } finally {
                context.close();
            }
        } finally {
            lock.unlock();
            shutdownQuietly(blocker);
        }
    }

    private static ConfigurableApplicationContext start(String dbName) {
        return new SpringApplicationBuilder(CompatUpgradeApplication.class, RedisClientConfig.class)
                .web(WebApplicationType.NONE)
                .run(
                        // 与 BootCompatibilityTest 一致：用命令行参数而不是默认属性，
                        // 否则会被 src/main/resources 下激活 profile 的数据源配置盖掉
                        "--spring.datasource.url=jdbc:h2:mem:" + dbName + "_" + System.nanoTime()
                                + ";DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--orivone.dbupgrade.lock-type=redis",
                        // 与 BootCompatibilityTest 同理：方言显式钉死 h2，
                        // 不依赖当前激活的是哪个 profile
                        "--orivone.dbupgrade.db-type=h2",
                        "--orivone.dbupgrade.lock-key=" + LOCK_KEY,
                        "--orivone.dbupgrade.lease-seconds=60",
                        "--orivone.dbupgrade.script-root=compat-scripts",
                        "--orivone.dbupgrade.operator=redis-lock-test",
                        "--orivone.dbupgrade.version-table=dbupgrade_version",
                        "--orivone.dbupgrade.record-table=dbupgrade_record",
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=WARN"
                );
    }

    private static void assumeRedis() {
        assumeTrue(redisAvailable, "本地 Redis 不可用，跳过 Redis 锁回归（" + unavailableReason + "）");
    }

    private static RedissonClient newClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(ADDRESS)
                .setPassword(PASSWORD)
                .setTimeout(3000)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(4);
        return Redisson.create(config);
    }

    private static void shutdownQuietly(RedissonClient client) {
        if (client == null) {
            return;
        }
        try {
            client.shutdown();
        } catch (Exception ignore) {
            // 探测阶段的清理失败无需影响测试结论
        }
    }

    private static int count(DataSource dataSource, String sql) throws SQLException {        Connection connection = dataSource.getConnection();
        try {
            Statement statement = connection.createStatement();
            try {
                ResultSet resultSet = statement.executeQuery(sql);
                try {
                    return resultSet.next() ? resultSet.getInt(1) : -1;
                } finally {
                    resultSet.close();
                }
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /**
     * 同 {@link #count}，但表不存在时返回 0。
     * <p>
     * 抢锁失败时框架连升级记录表都不会创建，此时 {@code COUNT(*)} 会抛「表不存在」；
     * 对「有没有执行过升级」这个断言来说，「表不存在」与「表存在但 0 行」等价。
     */
    private static int countIfExists(DataSource dataSource, String sql) {
        try {
            return count(dataSource, sql);
        } catch (SQLException e) {
            return 0;
        }
    }

    /**
     * 测试用 Redisson 客户端：由测试自己装配，模拟使用方的真实形态
     * （生产环境通常来自 redisson-spring-boot-starter）。
     */
    @Configuration(proxyBeanMethods = false)
    public static class RedisClientConfig {

        @Bean(destroyMethod = "shutdown")
        public RedissonClient redissonClient() {
            return newClient();
        }
    }
}
