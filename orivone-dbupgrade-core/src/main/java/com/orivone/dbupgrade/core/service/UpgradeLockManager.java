package com.orivone.dbupgrade.core.service;

/**
 * 升级锁，保证同一时刻只有一个升级任务在跑。
 * <p>
 * 锁按「作用域」分粒度，作用域字符串本身就是锁的标识：
 * <ul>
 *     <li>{@link #GLOBAL_SCOPE}——全局锁，所有分组共用一把，对应 {@code global-lock=true}（默认）</li>
 *     <li>注册表的 {@code group()}——分组锁，对应 {@code global-lock=false}，不同分组各跑各的</li>
 * </ul>
 * 内置两个实现：{@link DbUpgradeLockManager}（默认，单表锁，多节点可用，不加中间件）与
 * {@link LocalUpgradeLockManager}（内存锁，单机或测试用）。Redis 实现在可选模块
 * {@code orivone-dbupgrade-lock-redisson} 里，core 不带它。
 * @author orivone
 */
public interface UpgradeLockManager {

    /**
     * 全局锁作用域：所有分组共用一把锁，写入锁表时作为固定的 lock_scope 值
     */
    String GLOBAL_SCOPE = "__GLOBAL__";

    /**
     * 非阻塞获取指定作用域的锁
     *
     * @param scope 锁作用域，{@link #GLOBAL_SCOPE} 或 {@code registry.group()}
     * @return true 表示获取成功，可以执行升级；false 表示其他任务正在执行，本次应跳过
     */
    boolean tryLock(String scope);

    /**
     * 释放指定作用域的锁，必须由获取锁的一方调用；释放失败只记录日志，依赖租期兜底
     *
     * @param scope 与 {@link #tryLock(String)} 传入的一致
     */
    void unlock(String scope);

    /**
     * 当前节点是否持有锁
     */
    boolean isHeldByCurrentThread();

    /**
     * 便捷方法：获取全局锁
     */
    default boolean tryLock() {
        return tryLock(GLOBAL_SCOPE);
    }

    /**
     * 便捷方法：释放全局锁
     */
    default void unlock() {
        unlock(GLOBAL_SCOPE);
    }
}
