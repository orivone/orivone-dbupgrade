package com.orivone.dbupgrade.core.dialect;

import java.util.Collections;

import com.orivone.dbupgrade.core.schema.SchemaMetadata;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;

import java.sql.SQLException;
import java.util.List;

/**
 * 数据库方言扩展点，把升级模块中所有与具体数据库绑定的部分收敛到这里。
 * <p>
 * 需要区分的只有三件事：
 * <ol>
 *     <li>两张记录表的建表语法（自增主键、时间类型、长文本类型、字符集、注释）</li>
 *     <li>元数据查询方式（information_schema / ALL_TABLES / sys.indexes）</li>
 *     <li>自增主键回填方式、是否支持 # 行注释等零散差异</li>
 * </ol>
 * 版本注册表、changeset 解析、checksum、版本比较、锁、执行调度都与方言无关，新增数据库不需要动。
 * <p>
 * 新增一种数据库的步骤：实现本接口 → 在 {@link DialectResolver} 中注册 → 配置或自动探测命中。
 * 框架内置实现见 {@code MysqlDialect / PostgreSqlDialect / OracleDialect / SqlServerDialect / H2Dialect}。
 * @author orivone
 */
public interface UpgradeDialect {

    /**
     * 方言对应的数据库类型
     */
    DbType dbType();

    /**
     * 当前版本表建表语句。
     * <p>
     * 返回列表而非单条，是因为部分数据库（Oracle、SQL Server）不支持 CREATE TABLE IF NOT EXISTS，
     * 且索引需要独立语句创建。调用方按列表顺序执行，执行前已确认表不存在。
     */
    List<String> versionTableDdl(String table);

    /**
     * changeset 记录表建表语句，含唯一约束与两个普通索引
     */
    List<String> recordTableDdl(String table);

    /**
     * 升级锁表建表语句，仅 {@code lock-type=db} 时使用。
     * <p>
     * 锁表只有三列：{@code lock_scope}（主键，全局模式固定 {@code __GLOBAL__}）、
     * {@code lock_owner}（持有者 host:pid:uuid）、{@code lock_time}（获取时间，取数据库时间）。
     * 锁的语义是「行存在即被占用」，不通过 UPDATE 改状态列。
     * <p>
     * 默认给出 SQL 标准写法，MySQL（ENGINE / CHARSET / COMMENT）与 SQL Server（NVARCHAR / DATETIME2）覆盖。
     */
    default List<String> lockTableDdl(String table) {
        return Collections.singletonList("CREATE TABLE " + table + " ("
                + " lock_scope VARCHAR(80) NOT NULL,"
                + " lock_owner VARCHAR(255) NOT NULL,"
                + " lock_time TIMESTAMP NOT NULL,"
                + " PRIMARY KEY (lock_scope))");
    }

    /**
     * 当前数据库时间的查询语句，用于锁表租期判断。
     * <p>
     * 取数据库时间而不是 JVM 时间，避免多节点时钟漂移导致僵尸锁误判。
     */
    default String currentTimestampSql() {
        return "SELECT CURRENT_TIMESTAMP";
    }

    /**
     * 判断异常是否为唯一键冲突。
     * <p>
     * 锁表靠主键唯一约束做并发互斥，必须能识别这类异常：SQLState 以 {@code 23} 开头
     * （integrity constraint violation）在各数据库通用，MySQL 1062 / PostgreSQL 23505 /
     * Oracle ORA-00001 / SQL Server 2627 都落在这个区间。
     */
    default boolean isUniqueViolation(SQLException e) {
        String sqlState = e == null ? null : e.getSQLState();
        return sqlState != null && sqlState.startsWith("23");
    }

    /**
     * 自增主键回填时需要的列名，返回 null 表示使用 JDBC 标准的 RETURN_GENERATED_KEYS。
     * <p>
     * Oracle 等数据库需要用 {@code prepareStatement(sql, new String[]{"ID"})} 显式声明返回列。
     */
    default String[] generatedKeyColumns() {
        return null;
    }

    /**
     * 脚本中 # 是否按行注释处理。
     * <p>
     * # 是 MySQL 专有行注释；PostgreSQL 的 #&gt; / #&gt;&gt; 是 JSON 操作符，按注释剔除会破坏 SQL。
     */
    default boolean supportsHashComment() {
        return dbType().isMysqlFamily();
    }

    /**
     * 创建该方言的元数据检查器
     */
    SchemaMetadata newSchemaMetadata(JdbcExecutor jdbcExecutor);
}
