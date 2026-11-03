package com.orivone.dbupgrade.core.config;

import com.orivone.dbupgrade.core.StrUtils;


import java.net.InetAddress;

/**
 * 运行期配置快照，只读，不做 Spring 绑定。
 * <p>
 * 由 starter 的 {@code DbUpgradeProperties}（{@code @ConfigurationProperties}）拷贝而来；core 只认这个类，
 * 所以非 Boot 环境自己 new 一个、填好字段就能用。
 * <p>
 * 新增配置项时两个类都要改，只改一边会在拷贝时被静默丢弃。
 * <p>
 * 没有 target-version 这一项：升到哪一版由注册表的 {@code targetVersion()} 决定，不填就取最高版本。
 * 版本清单与目标版本同源，避免两处状态需要互相对齐。
 * <p>
 * 全部配置都在 {@code orivone.dbupgrade.*} 下，逐项含义见各字段注释。
 * @author orivone
 */

public class UpgradeSettings {


    /**
     * 是否启用自动升级，false 时不注册任何升级组件
     */
    private boolean enabled = true;

    /**
     * 升级失败是否阻断应用启动
     */
    private boolean failFast = true;

    /**
     * 锁实现类型，可选值：
     * <ul>
     *     <li>{@code db}（默认）：数据库单表锁，多节点可用，需要锁表 {@link #lockTable}</li>
     *     <li>{@code redis}：Redisson 分布式锁，需要额外引入 orivone-dbupgrade-lock-redisson 模块</li>
     *     <li>{@code local}：JVM 内存锁，仅单机或测试使用，多节点等于没有锁</li>
     * </ul>
     * 指定了实现但依赖缺失时直接启动失败，不静默降级，避免误以为有分布式锁。
     */
    private String lockType = "db";

    /**
     * 是否全局单例执行。
     * <p>
     * true（默认）：所有分组共用一把锁（作用域 {@code __GLOBAL__}），同一时刻整个库只有一个升级任务；
     * false：按 {@code registry.group()} 分别加锁，不同分组可以各自执行。
     * <p>
     * 注意：true 与 false 的锁作用域不同，切换时必须停机或在维护窗口进行，
     * 否则切换瞬间两种作用域的锁互不阻塞。
     */
    private boolean globalLock = true;

    /**
     * 分布式锁 key，仅 {@code lock-type=redis} 使用
     */
    private String lockKey = "orivone:dbupgrade:lock";

    /**
     * 锁租期（秒），需覆盖最长升级耗时，超时后锁自动释放
     */
    private long leaseSeconds = 1800L;

    /**
     * 当前版本记录表名
     */
    private String versionTable = "dbupgrade_version";

    /**
     * changeset 记录表名
     */
    private String recordTable = "dbupgrade_record";

    /**
     * 升级锁表名，仅 {@code lock-type=db} 使用。
     * <p>
     * 表结构见 {@code UpgradeDialect#lockTableDdl}：
     * lock_scope（主键，全局模式固定 __GLOBAL__）、lock_owner、lock_time。
     */
    private String lockTable = "dbupgrade_lock";

    /**
     * 执行人标识，默认取主机名，写入 dbupgrade_record.executed_by
     */
    private String operator;

    /**
     * 启动时自动建表。
     * <p>
     * 打开后先探测记录表/锁表是否存在，不存在才执行方言提供的 CREATE TABLE——
     * 不用 {@code CREATE TABLE IF NOT EXISTS}，因为 Oracle / SQL Server 不支持这种写法。
     * 关闭时要求升级前两张表已经存在，否则直接报错。
     */
    private boolean autoCreateTable = true;

    /**
     * 单条 SQL 的执行超时（秒），0 表示不限制
     */
    private int queryTimeoutSeconds = 600;

    /**
     * 脚本根目录（classpath 相对路径），仅用于扫描“已存在但未在注册表声明”的文件并打日志
     */
    private String scriptRoot = "upgrade-scripts";

    /**
     * 是否检查并提示未在注册表声明的脚本文件，仅打日志不执行
     */
    private boolean checkUndeclaredFiles = true;

    /**
     * 是否允许从 http(s) 加载升级脚本，默认 false。
     * <p>
     * 默认禁用：远程脚本会在能连生产库的进程里执行任意 SQL，而它的内容不受代码仓库评审约束。
     * 只有确实把脚本托管在受控的内网地址时才打开。
     */
    private boolean allowRemoteScripts = false;

    /**
     * 数据库方言，auto 表示运行时从 DatabaseMetaData 自动探测。
     * 可选值：auto / mysql / postgresql / oracle / dm / sqlserver / h2
     */
    private String dbType = "auto";

    /**
     * 多数据源场景下指定用于升级的数据源 Bean 名称。
     * 为空时要求容器中只有一个 DataSource，否则启动报错并提示配置本项。
     */
    private String datasourceBean;

    /**
     * 解析执行人标识，未显式配置时使用主机名
     */
    public String resolveOperator() {
        if (operator != null && !StrUtils.isBlank(operator)) {
            return operator.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public String getLockType() {
        return lockType;
    }

    public void setLockType(String lockType) {
        this.lockType = lockType;
    }

    public boolean isGlobalLock() {
        return globalLock;
    }

    public void setGlobalLock(boolean globalLock) {
        this.globalLock = globalLock;
    }

    public String getLockKey() {
        return lockKey;
    }

    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }

    public long getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(long leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public String getVersionTable() {
        return versionTable;
    }

    public void setVersionTable(String versionTable) {
        this.versionTable = versionTable;
    }

    public String getRecordTable() {
        return recordTable;
    }

    public void setRecordTable(String recordTable) {
        this.recordTable = recordTable;
    }

    public String getLockTable() {
        return lockTable;
    }

    public void setLockTable(String lockTable) {
        this.lockTable = lockTable;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public boolean isAutoCreateTable() {
        return autoCreateTable;
    }

    public void setAutoCreateTable(boolean autoCreateTable) {
        this.autoCreateTable = autoCreateTable;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public String getScriptRoot() {
        return scriptRoot;
    }

    public void setScriptRoot(String scriptRoot) {
        this.scriptRoot = scriptRoot;
    }

    public boolean isCheckUndeclaredFiles() {
        return checkUndeclaredFiles;
    }

    public void setCheckUndeclaredFiles(boolean checkUndeclaredFiles) {
        this.checkUndeclaredFiles = checkUndeclaredFiles;
    }

    public boolean isAllowRemoteScripts() {
        return allowRemoteScripts;
    }

    public void setAllowRemoteScripts(boolean allowRemoteScripts) {
        this.allowRemoteScripts = allowRemoteScripts;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public String getDatasourceBean() {
        return datasourceBean;
    }

    public void setDatasourceBean(String datasourceBean) {
        this.datasourceBean = datasourceBean;
    }

}
