package com.orivone.dbupgrade.core.schema;

import com.orivone.dbupgrade.core.model.Precondition;

import java.util.List;

/**
 * 元数据检查器扩展点，判断表、列、索引是否已存在。
 * <p>
 * 用途有二：
 * <ol>
 *     <li>执行 changeset 前评估 --precondition 声明的前置条件</li>
 *     <li>执行每条 DDL 前做存在性判断，实现 DDL 级别的重跑安全</li>
 * </ol>
 * 不同数据库的元数据存储差异很大（MySQL 的 information_schema.STATISTICS、
 * PostgreSQL 的 pg_indexes、Oracle 的 ALL_INDEXES、SQL Server 的 sys.indexes），
 * 因此抽成接口，由 {@code UpgradeDialect} 按库返回实现。
 * <p>
 * 约定：查询失败时一律返回“不存在”，让 SQL 执行暴露真实问题，避免误跳过。
 * @author orivone
 */
public interface SchemaMetadata {

    /**
     * 表是否存在
     */
    boolean tableExists(String table);

    /**
     * 列是否存在
     */
    boolean columnExists(String table, String column);

    /**
     * 索引是否存在
     */
    boolean indexExists(String table, String index);

    /**
     * 评估单条前置条件
     *
     * @return true 表示条件满足（可以执行 changeset）
     */
    boolean evaluate(Precondition precondition);

    /**
     * DDL 是否已经生效（已存在则无需再次执行）
     */
    boolean alreadyApplied(String statement);

    /**
     * 是否为可做存在性判断的加法类 DDL
     */
    boolean isIdempotentDdl(String statement);

    /**
     * 批量评估，返回第一个不满足的条件，全部满足返回 null
     */
    Precondition firstUnsatisfied(List<Precondition> preconditions);
}
