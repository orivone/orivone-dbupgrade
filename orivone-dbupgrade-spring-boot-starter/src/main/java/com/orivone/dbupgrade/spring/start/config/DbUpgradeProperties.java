package com.orivone.dbupgrade.spring.start.config;

import com.orivone.dbupgrade.core.StrUtils;
import com.orivone.dbupgrade.core.config.UpgradeSettings;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;

/**
 * 配置绑定入口，前缀 {@code orivone.dbupgrade}。
 * <p>
 * 本类只负责绑定，不参与执行。执行前由 {@link #toSettings()} 拷贝成
 * {@link UpgradeSettings} 交给 core，core 因此不必依赖 Spring 的配置绑定。
 * <p>
 * 新增配置项时两个类都要改，只改一边会在拷贝阶段被静默丢弃。
 * @author orivone
 */
@ConfigurationProperties(prefix = "orivone.dbupgrade")
public class DbUpgradeProperties {

    /**
     * 是否启用自动升级，false 时不注册任何升级组件
     */
    private boolean enabled = true;

    /**
     * 升级失败是否阻断应用启动
     */
    private boolean failFast = true;

    /**
     * 锁实现类型：db（默认，数据库单表锁）/ redis（需引入 orivone-dbupgrade-lock-redisson）/ local（单机内存锁）
     */
    private String lockType = "db";

    /**
     * 是否全局单例执行。
     * true：所有分组共用一把锁，同一时刻整个库只有一个升级任务；
     * false：按 registry.group() 分别加锁，不同分组可各自执行。
     */
    private boolean globalLock = true;

    /**
     * 分布式锁 key，仅 lock-type=redis 使用，实际 key 为 key:scope
     */
    private String lockKey = "orivone:dbupgrade:lock";

    /**
     * 锁租期（秒），需覆盖最长升级耗时；db 模式下同时是僵尸锁的超期时间
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
     * 升级锁表名，仅 lock-type=db 使用
     */
    private String lockTable = "dbupgrade_lock";

    /**
     * 执行人标识，默认取主机名，写入 dbupgrade_record.executed_by
     */
    private String operator;

    /**
     * 启动时自动建表（先探测表是否存在，不存在才执行建表语句）。
     * 关闭后需人工执行建表 SQL，否则升级前会直接报错。
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
     * 远程脚本等价于「把能连生产库的 SQL 执行权交给一个 URL」，默认关闭；
     * 只在脚本托管于受控地址时才打开。
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
        if (!StrUtils.isBlank(operator)) {
            return operator.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    /**
     * 拷贝成本模块之外的只读配置模型，供 core 模块使用。
     * <p>
     * 采用字段同名拷贝（BeanUtils.copyProperties），因此两个类的字段名必须严格一致；
     * 新增配置项时若只改本类，拷贝时该字段会保持 UpgradeSettings 的默认值。
     *
     * @return 配置快照，永不为 null
     */
    public UpgradeSettings toSettings() {
        UpgradeSettings upgradeProperties = new UpgradeSettings();
        BeanUtils.copyProperties(this, upgradeProperties);
        return upgradeProperties;
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
