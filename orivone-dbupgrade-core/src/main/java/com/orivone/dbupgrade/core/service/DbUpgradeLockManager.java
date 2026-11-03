package com.orivone.dbupgrade.core.service;

import com.orivone.dbupgrade.core.IdentifierUtils;
import com.orivone.dbupgrade.core.StrUtils;
import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.config.UpgradeSettings;
import com.orivone.dbupgrade.core.dialect.UpgradeDialect;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * 数据库单表锁，默认实现。
 * <p>
 * 思路跟 Liquibase 的 DATABASECHANGELOGLOCK 一致，但完全不碰 UPDATE，用「行在不在」表达锁：
 * 抢锁就往锁表插一行，插进去就算拿到；互斥靠 {@code lock_scope} 主键唯一约束，两个节点同时插只有一个能成；
 * 放锁按 {@code lock_scope + lock_owner} 删掉那一行；行还在但 {@code lock_time} 已超租期，
 * 说明持有者挂了，别人可以抢占。
 * <p>
 * 锁表 {@code dbupgrade_lock} 就三列：{@code lock_scope}（全局模式固定 __GLOBAL__，分组模式是 group()）、
 * {@code lock_owner}（host:pid:uuid，放锁时校验，避免误删别人的锁）、{@code lock_time}
 * （用数据库时间写，不怕节点时钟漂）。
 * <p>
 * 跟「事务行锁」最大的区别是锁不绑连接：插入提交锁就成立，所以可以继续用连接池的短连接。
 * @author orivone
 */
public class DbUpgradeLockManager implements UpgradeLockManager {

    private static final Logger log = LoggerFactory.getLogger(DbUpgradeLockManager.class);

    /**
     * 抢占僵尸锁后重试插入的最大次数（应对「刚删掉、又被别人抢插」的极端竞争）
     */
    private static final int MAX_ATTEMPTS = 2;

    private final JdbcExecutor jdbcExecutor;

    private final UpgradeDialect dialect;

    private final String lockTable;

    private final long leaseSeconds;

    private final boolean autoCreateTable;

    /**
     * 持有者标识：host:pid:uuid，进程内固定，用于释放校验与僵尸锁归属判断
     */
    private final String owner;

    private final ThreadLocal<String> heldScope = new ThreadLocal<String>();

    private volatile boolean tableReady;

    public DbUpgradeLockManager(JdbcExecutor jdbcExecutor, UpgradeDialect dialect, UpgradeSettings properties) {
        this.jdbcExecutor = jdbcExecutor;
        this.dialect = dialect;
        this.lockTable = IdentifierUtils.requireTableName(properties.getLockTable(), "orivone.dbupgrade.lock-table");
        this.leaseSeconds = properties.getLeaseSeconds() <= 0 ? 1800L : properties.getLeaseSeconds();
        this.autoCreateTable = properties.isAutoCreateTable();
        this.owner = buildOwner();
    }

    @Override
    public boolean tryLock(String scope) {
        String lockScope = requireScope(scope);
        ensureTable();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Timestamp now = currentDbTime();
            LockRow row = findRow(lockScope);
            if (row != null) {
                long ageMillis = now.getTime() - (row.lockTime() == null ? 0L : row.lockTime().getTime());
                if (ageMillis < 0) {
                    // 锁时间比当前数据库时间还晚：多见于会话时区与 JVM 时区不一致。
                    // 此时无法判断是否超期，按「未超期」处理，宁可不抢占也不误抢正在执行的锁。
                    log.warn("升级锁[{}]的 lock_time[{}]晚于当前数据库时间[{}]，"
                                    + "可能存在时区/时钟不一致，本次按未超期处理，不抢占",
                            lockScope, row.lockTime(), now);
                }
                if (!isExpired(row.lockTime(), now)) {
                    log.debug("升级锁[{}]正被 {} 持有（获取于 {}），本节点跳过", lockScope, row.owner(), row.lockTime());
                    return false;
                }
                int removed = deleteRow(lockScope, row.owner());
                log.warn("升级锁[{}]已超过租期 {} 秒，作为僵尸锁抢占（原持有者 {}，获取于 {}），删除结果={}",
                        lockScope, leaseSeconds, row.owner(), row.lockTime(), removed);
            }
            try {
                jdbcExecutor.update("INSERT INTO " + lockTable
                                + " (lock_scope, lock_owner, lock_time) VALUES (?, ?, ?)",
                        lockScope, owner, now);
                heldScope.set(lockScope);
                log.info("已获取升级锁[{}]，持有者 {}，租期 {} 秒", lockScope, owner, leaseSeconds);
                return true;
            } catch (UpgradeException e) {
                if (!isUniqueViolation(e)) {
                    throw new UpgradeException("获取升级锁[" + lockScope + "]失败：" + e.getMessage(), e);
                }
                log.debug("升级锁[{}]被其他节点抢先插入（第 {} 次尝试），本次未获取到", lockScope, attempt);
                return false;
            }
        }
        return false;
    }

    @Override
    public void unlock(String scope) {
        String lockScope = requireScope(scope);
        try {
            int rows = deleteRow(lockScope, owner);
            if (rows > 0) {
                log.info("已释放升级锁[{}]", lockScope);
            } else {
                log.warn("升级锁[{}]不存在或不属于当前节点（{}），未执行释放", lockScope, owner);
            }
        } catch (RuntimeException e) {
            // 释放失败不影响升级结果，依赖租期兜底
            log.warn("释放升级锁[{}]失败，将在租期 {} 秒后自动失效：{}", lockScope, leaseSeconds, e.getMessage());
        } finally {
            heldScope.remove();
        }
    }

    @Override
    public boolean isHeldByCurrentThread() {
        String scope = heldScope.get();
        if (scope == null) {
            return false;
        }
        try {
            LockRow row = findRow(scope);
            return row != null && owner.equals(row.owner());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 探测锁表是否可读，不可读时按 auto-create-table 决定建表还是报错。
     * <p>
     * 与记录表保持同一套约定：不依赖 CREATE TABLE IF NOT EXISTS（Oracle / SQL Server 不支持）。
     * 「先探测再建表」存在竞争窗口，两个节点同时启动时后到的必然撞「表已存在」，
     * 因此建表失败后再复查一次：表能读就算别人建好了，本节点照常抢锁。
     */
    private synchronized void ensureTable() {
        if (tableReady) {
            return;
        }
        if (jdbcExecutor.isReadable(lockTable)) {
            tableReady = true;
            return;
        }
        if (!autoCreateTable) {
            throw new UpgradeException("升级锁表[" + lockTable + "]不存在或不可访问，"
                    + "请先执行建表 SQL，或开启 orivone.dbupgrade.auto-create-table");
        }
        boolean created = false;
        try {
            for (String ddl : dialect.lockTableDdl(lockTable)) {
                jdbcExecutor.execute(ddl);
            }
            created = true;
        } catch (RuntimeException e) {
            if (!jdbcExecutor.isReadable(lockTable)) {
                throw e;
            }
            log.debug("升级锁表[{}]已由其他节点创建，忽略本次建表异常：{}", lockTable, e.getMessage());
        }
        tableReady = true;
        if (created) {
            log.info("已创建升级锁表：{}", lockTable);
        }
    }

    private LockRow findRow(String scope) {
        return jdbcExecutor.queryFirst("SELECT lock_owner, lock_time FROM " + lockTable + " WHERE lock_scope = ?",
                rs -> new LockRow(rs.getString(1), rs.getTimestamp(2)), scope);
    }

    private int deleteRow(String scope, String lockOwner) {
        return jdbcExecutor.update("DELETE FROM " + lockTable + " WHERE lock_scope = ? AND lock_owner = ?",
                scope, lockOwner);
    }

    private Timestamp currentDbTime() {
        try {
            Timestamp now = jdbcExecutor.queryTimestamp(dialect.currentTimestampSql());
            if (now != null) {
                return now;
            }
        } catch (RuntimeException e) {
            log.debug("读取数据库时间失败，退化为 JVM 时间：{}", e.getMessage());
        }
        return new Timestamp(System.currentTimeMillis());
    }

    private boolean isExpired(Timestamp lockTime, Timestamp now) {
        if (lockTime == null) {
            return true;
        }
        return now.getTime() - lockTime.getTime() > leaseSeconds * 1000L;
    }

    /**
     * 判断是否为唯一键冲突：{@link JdbcExecutor} 把 SQLException 包在 cause 里，
     * 这里沿 cause 链找出原始异常再交给方言判断
     */
    private boolean isUniqueViolation(Throwable e) {
        Throwable cause = e;
        int depth = 0;
        while (cause != null && depth < 8) {
            if (cause instanceof SQLException) {
                return dialect.isUniqueViolation((SQLException) cause);
            }
            cause = cause.getCause();
            depth++;
        }
        return false;
    }

    private static String requireScope(String scope) {
        if (StrUtils.isBlank(scope)) {
            throw new UpgradeException("升级锁作用域不能为空，应为 __GLOBAL__ 或 registry.group()");
        }
        return StrUtils.strip(scope);
    }

    private static String buildOwner() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        return host + ":" + processId() + ":" + UUID.randomUUID();
    }

    /**
     * 取当前进程号。
     * <p>
     * 不用 {@code ProcessHandle}（Java 9+），改为从 RuntimeMXBean 的 {@code pid@host} 里截取，
     * 这是 Java 8 上的等效做法。
     */
    private static String processId() {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            int at = name.indexOf('@');
            return at > 0 ? name.substring(0, at) : name;
        } catch (Throwable e) {
            return "unknown";
        }
    }

    /**
     * 锁表当前行（Java 8 基线没有 record）
     */
    private static final class LockRow {

        private final String owner;

        private final Timestamp lockTime;

        private LockRow(String owner, Timestamp lockTime) {
            this.owner = owner;
            this.lockTime = lockTime;
        }

        private String owner() {
            return owner;
        }

        private Timestamp lockTime() {
            return lockTime;
        }
    }
}
