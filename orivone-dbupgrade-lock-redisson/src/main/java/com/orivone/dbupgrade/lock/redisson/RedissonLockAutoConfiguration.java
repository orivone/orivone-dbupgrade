package com.orivone.dbupgrade.lock.redisson;

import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.service.UpgradeLockManager;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Redis 锁的自动装配，由可选模块自己提供。
 * <p>
 * 目的只有一个：让 starter 完全不认识 Redisson。Redisson 会顺带拉进 netty、kryo、rxjava 等
 * 二十来个包、十几 MB，而 core 加 starter 一共才一百多 KB。只用默认数据库锁的项目没理由背这个包袱，
 * 所以依赖是单向的 {@code orivone-dbupgrade-core ← orivone-dbupgrade-lock-redisson}，谁要谁引。
 * <p>
 * 生效条件两个：classpath 上有 {@link RedissonClient}，且 {@code orivone.dbupgrade.lock-type=redis}。
 * 和 starter 里的 db / local 两种锁天然互斥，不会同时冒出两个 {@link UpgradeLockManager}。
 * <p>
 * 这里不引用 starter 的 {@code DbUpgradeProperties}——那会让 lock 反向依赖 starter，两个可选模块互相依赖就成环了。
 * 两项配置用 {@code @Value} 直接读，默认值交给 {@link RedissonUpgradeLockManager} 的构造器兜底。
 * <p>
 * 兼容 Spring Boot 2.7 / 3.x / 4.x，字节码基线 Java 8：用到的注解与 API 在三代里全限定名一致。
 * @author orivone
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "orivone.dbupgrade", name = "lock-type", havingValue = "redis")
public class RedissonLockAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedissonLockAutoConfiguration.class);

    /**
     * 注册 Redis 分布式锁实现。
     * <p>
     * 用 {@link ObjectProvider} 而不是直接注入 {@link RedissonClient}：没配 Redisson 时
     * 后者会抛 {@code NoSuchBeanDefinitionException}（报错信息指向注入点，与真实原因隔了一层），
     * 前者能让本框架给出明确指引。
     *
     * @param redissonClientProvider Redisson 客户端（由使用方自行装配，通常是 redisson-spring-boot-starter）
     * @param lockKey                锁 key 前缀，空则由构造器兜底为 orivone:dbupgrade:lock
     * @param leaseSeconds           租期秒数，小于等于 0 由构造器兜底为 1800
     */
    @Bean
    @ConditionalOnMissingBean(UpgradeLockManager.class)
    public UpgradeLockManager upgradeLockManager(ObjectProvider<RedissonClient> redissonClientProvider,
                                                 @Value("${orivone.dbupgrade.lock-key:}") String lockKey,
                                                 @Value("${orivone.dbupgrade.lease-seconds:0}") long leaseSeconds) {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            throw new UpgradeException("orivone.dbupgrade.lock-type=redis，但容器中没有 RedissonClient Bean，"
                    + "请先配置 Redisson（如引入 redisson-spring-boot-starter 并配置 spring.data.redis.*），"
                    + "或改用 lock-type=db（默认，无需额外依赖）");
        }
        log.info("数据库升级锁实现：redis（Redisson 分布式锁，作用域 __GLOBAL__ 或分组名）");
        return new RedissonUpgradeLockManager(redissonClient, lockKey, leaseSeconds);
    }
}
