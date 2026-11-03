package com.orivone.dbupgrade.core.service;

import com.orivone.dbupgrade.core.StrUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单机内存锁，只能保证同一个 JVM 内串行。
 * <p>
 * 多节点部署下它等于没有锁，所以只有显式配 {@code orivone.dbupgrade.lock-type=local} 才会用到，
 * 默认走的是 {@link DbUpgradeLockManager}。
 * <p>
 * scope 在这里只用来记「谁持有」：本 JVM 同一时刻只允许一个升级任务，不管传的是全局还是分组。
 * @author orivone
 */
public class LocalUpgradeLockManager implements UpgradeLockManager {

    private static final Logger log = LoggerFactory.getLogger(LocalUpgradeLockManager.class);

    private final ReentrantLock lock = new ReentrantLock();

    private final ThreadLocal<String> heldScope = new ThreadLocal<>();

    private final AtomicBoolean warnOnce = new AtomicBoolean(false);

    @Override
    public boolean tryLock(String scope) {
        if (scope == null || StrUtils.isBlank(scope)) {
            throw new IllegalArgumentException("升级锁作用域不能为空");
        }
        boolean acquired = lock.tryLock();
        if (acquired) {
            heldScope.set(scope);
            if (warnOnce.compareAndSet(false, true)) {
                log.warn("当前使用单机内存锁（lock-type=local），只能保证同一 JVM 内串行；"
                        + "多节点部署请改用 lock-type=db 或 redis");
            }
            log.info("已获取单机升级锁[{}]", scope);
        }
        return acquired;
    }

    @Override
    public void unlock(String scope) {
        heldScope.remove();
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.info("已释放单机升级锁[{}]", scope);
        }
    }

    @Override
    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }
}
