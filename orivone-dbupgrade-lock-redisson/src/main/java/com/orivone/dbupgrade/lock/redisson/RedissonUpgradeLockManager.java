package com.orivone.dbupgrade.lock.redisson;

import com.orivone.dbupgrade.core.StrUtils;
import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.service.UpgradeLockManager;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁（Redisson 实现），放在可选模块里。
 * <p>
 * 单独成模块，为的是不让 core 背上 Redis 客户端——不用 Redis 的项目连这个 jar 都见不到。
 * <p>
 * 锁 key 的拼法是 {@code <lock-key>:<scope>}，scope 是 {@code __GLOBAL__}（全局单例）或分组名。
 * <p>
 * 拿锁不排队（waitTime=0），多节点同时启动不会互相干等；租期默认 1800 秒，释放出问题就靠它过期兜底。
 * 要注意一点：显式设了租期，Redisson 就不再看门狗续期，脚本总耗时超过租期时锁会提前松手，
 * 得按实际耗时把 {@code orivone.dbupgrade.lease-seconds} 调够。
 * @author orivone
 */
public class RedissonUpgradeLockManager implements UpgradeLockManager {

    private static final Logger log = LoggerFactory.getLogger(RedissonUpgradeLockManager.class);

    private final RedissonClient redissonClient;

    private final String lockKeyPrefix;

    private final long leaseSeconds;

    private final ThreadLocal<String> heldScope = new ThreadLocal<String>();

    private final ThreadLocal<RLock> currentLock = new ThreadLocal<RLock>();

    /**
     * @param redissonClient Redisson 客户端
     * @param lockKey        锁 key 前缀，实际 key 为 {@code lockKey:scope}
     * @param leaseSeconds   租期（秒），小于等于 0 时取 1800
     */
    public RedissonUpgradeLockManager(RedissonClient redissonClient, String lockKey, long leaseSeconds) {
        this.redissonClient = redissonClient;
        this.lockKeyPrefix = StrUtils.isBlank(lockKey) ? "orivone:dbupgrade:lock" : StrUtils.strip(lockKey);
        this.leaseSeconds = leaseSeconds <= 0 ? 1800L : leaseSeconds;
    }

    @Override
    public boolean tryLock(String scope) {
        String key = key(scope);
        RLock lock = redissonClient.getLock(key);
        try {
            boolean acquired = lock.tryLock(0L, leaseSeconds, TimeUnit.SECONDS);
            if (acquired) {
                currentLock.set(lock);
                heldScope.set(scope);
                log.info("已获取升级锁[{}]，租期 {} 秒", key, leaseSeconds);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取升级锁[{}]被中断，本次跳过升级", key);
            return false;
        } catch (Exception e) {
            // 锁服务异常不能等同于“其他节点在升级”，必须暴露为升级失败，避免升级被永久静默跳过
            throw new UpgradeException("获取分布式升级锁[" + key + "]失败，请检查 Redis 可用性", e);
        }
    }

    @Override
    public void unlock(String scope) {
        RLock lock = currentLock.get();
        currentLock.remove();
        heldScope.remove();
        if (lock == null) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("已释放升级锁[{}]", key(scope));
            }
        } catch (Exception e) {
            log.warn("释放升级锁[{}]失败，将等待租期（{} 秒）自动释放：{}", key(scope), leaseSeconds, e.getMessage());
        }
    }

    @Override
    public boolean isHeldByCurrentThread() {
        RLock lock = currentLock.get();
        if (lock == null || heldScope.get() == null) {
            return false;
        }
        try {
            return lock.isHeldByCurrentThread();
        } catch (Exception e) {
            return false;
        }
    }

    private String key(String scope) {
        if (StrUtils.isBlank(scope)) {
            throw new UpgradeException("升级锁作用域不能为空，应为 __GLOBAL__ 或 registry.group()");
        }
        return lockKeyPrefix + ":" + StrUtils.strip(scope);
    }
}
