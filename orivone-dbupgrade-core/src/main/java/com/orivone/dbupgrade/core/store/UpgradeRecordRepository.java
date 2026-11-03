package com.orivone.dbupgrade.core.store;

import com.orivone.dbupgrade.core.IdentifierUtils;
import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.config.UpgradeSettings;
import com.orivone.dbupgrade.core.dialect.UpgradeDialect;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;
import com.orivone.dbupgrade.core.model.VersionedChangeset;
import com.orivone.dbupgrade.core.model.ChangesetStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 记录仓储，管两张记录表的建表与读写。
 * <p>
 * 记录按分组存，{@code group_code} 来自注册表的 {@code group()}：
 * <ul>
 *     <li>{@code dbupgrade_version}——每个分组一行水位，主键是 UUID，唯一键是 {@code group_code}</li>
 *     <li>{@code dbupgrade_record}——每个 changeset 一行，唯一键 {@code (changeset_id, group_code)}，
 *     所以同一个 changesetId 在不同分组下互不相干</li>
 * </ul>
 * 走原生 JDBC 而不是 Mapper，三个原因：升级发生在启动最早期，不想让业务方为它配 {@code @MapperScan}；
 * 要执行任意 DDL，ORM 帮不上忙；也不想跟全局表前缀机制扯上关系。
 * <p>
 * 跨库靠三条约定：建表语句由 {@link UpgradeDialect} 提供，本类不写方言；建表前先探测表在不在，
 * 不指望 {@code CREATE TABLE IF NOT EXISTS}；不用 {@code LIMIT}、{@code UPDATE ... ORDER BY}
 * 这类写法（改单行水位时先查 id 再按 id 更新）。
 * <p>
 * 表名可以用 {@code orivone.dbupgrade.version-table / record-table} 改，但必须是合法标识符。
 * @author orivone
 */
public class UpgradeRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(UpgradeRecordRepository.class);

    private static final int ERROR_MSG_MAX_LENGTH = 2000;

    /**
     * 版本表查询语句。
     * <p>
     * 表名用 {@code %s} 拼（表名无法参数化，靠 {@link IdentifierUtils#requireTableName} 白名单校验），
     * 分组名用 {@code ?} 占位——分组名来自业务方实现的 {@code group()}，不能拼进 SQL。
     */
    private static final String SELECT_VERSION_COLUMNS =
            "SELECT id, version, updated_by, updated_at FROM %s WHERE group_code = ?";

    private static final String SELECT_RECORD_COLUMNS =
            "SELECT id, changeset_id, file_path, version, checksum, run_always, status, executed_by,"
                    + " start_time, end_time, error_msg FROM %s";

    private final JdbcExecutor jdbcExecutor;

    private final UpgradeDialect dialect;

    private final String versionTable;

    private final String recordTable;

    public UpgradeRecordRepository(JdbcExecutor jdbcExecutor, UpgradeDialect dialect, UpgradeSettings properties) {
        this.jdbcExecutor = jdbcExecutor;
        this.dialect = dialect;
        this.versionTable = IdentifierUtils.requireTableName(properties.getVersionTable(), "orivone.dbupgrade.version-table");
        this.recordTable = IdentifierUtils.requireTableName(properties.getRecordTable(), "orivone.dbupgrade.record-table");
    }

    /**
     * 自动建表（幂等）。
     * <p>
     * 先探测表是否可读：可读则跳过，不可读才执行方言提供的建表语句。
     * 这样既不依赖 CREATE TABLE IF NOT EXISTS（Oracle / SQL Server 不支持），
     * 也避免了「表已存在但结构不同」时静默忽略。
     */
    public void ensureTables() {
        ensureTable(versionTable, dialect.versionTableDdl(versionTable));
        ensureTable(recordTable, dialect.recordTableDdl(recordTable));
        log.debug("升级记录表检查完成：{}、{}", versionTable, recordTable);
    }

    /**
     * 校验记录表存在（auto-create-table=false 时使用）
     */
    public void assertTables() {
        try {
            jdbcExecutor.queryInt("SELECT COUNT(*) FROM " + versionTable + " WHERE 1 = 0");
            jdbcExecutor.queryInt("SELECT COUNT(*) FROM " + recordTable + " WHERE 1 = 0");
        } catch (RuntimeException e) {
            throw new UpgradeException("升级记录表不存在或不可访问，请先执行建表 SQL 或开启 orivone.dbupgrade.auto-create-table", e);
        }
    }

    /**
     * 查询指定分组的当前数据库版本
     *
     * @param groupCode 分组标识，对应 {@code UpgradeVersionRegistry#group()}
     * @return 该分组的版本记录；该分组从未升级过时返回空
     */
    public Optional<UpgradeVersionRecord> findCurrentVersion(String groupCode) {
        UpgradeVersionRecord record = jdbcExecutor.queryFirst(
                String.format(SELECT_VERSION_COLUMNS, versionTable),
                rs -> new UpgradeVersionRecord(
                        rs.getString("id"),
                        rs.getString("version"),
                        rs.getString("updated_by"),
                        toLocalDateTime(rs.getTimestamp("updated_at"))),
                groupCode);
        return Optional.ofNullable(record);
    }

    /**
     * 保存指定分组的当前数据库版本。每个分组在表中恒有一行。
     * <p>
     * 先按 group_code 查出已有行的主键：有则按主键更新，无则插入一行新的 UUID 主键。
     * 不使用 MySQL 的 {@code UPDATE ... ORDER BY id LIMIT 1}，以便在 PostgreSQL / Oracle /
     * SQL Server 上同样可用。
     * <p>
     * 分组名一律走 {@code ?} 占位符，不拼接进 SQL（group() 由业务方实现，外部可控）。
     * <p>
     * 并发保护依赖 {@code uk_group_code} 唯一键：即使两个节点同时判断“无记录”并各自插入，
     * 也只有一个能成功，不会产生同一分组两行水位。
     */
    public void saveCurrentVersion(String groupCode, String version, String operator) {
        LocalDateTime now = LocalDateTime.now();
        List<String> ids = jdbcExecutor.query("SELECT id FROM " + versionTable + " WHERE group_code = ?",
                rs -> rs.getString(1), groupCode);
        int rows;
        if (ids.isEmpty()) {
            rows = jdbcExecutor.update("INSERT INTO " + versionTable
                            + " (id, group_code,version, updated_by, updated_at) VALUES (?,?, ?, ?, ?)",
                    UUID.randomUUID().toString(), groupCode, version, operator, now);
        } else {
            rows = jdbcExecutor.update("UPDATE " + versionTable
                            + " SET version = ?, updated_by = ?, updated_at = ? WHERE id = ?",
                    version, operator, now, ids.get(0));
        }
        if (rows <= 0) {
            log.warn("版本记录写入未影响任何行，请检查 {} 表状态（version={}）", versionTable, version);
        }
        log.info("数据库版本记录已更新为：{}（{}）", version, operator);
    }

    /**
     * 查询单个 changeset 记录
     */
    public Optional<UpgradeRecord> findRecord(String groupCode, String changesetId) {
        UpgradeRecord record = jdbcExecutor.queryFirst(
                String.format(SELECT_RECORD_COLUMNS, recordTable) + " WHERE changeset_id = ? and  group_code = ?",
                rs -> mapRecord(rs), changesetId, groupCode);
        return Optional.ofNullable(record);
    }

    /**
     * 查询某个版本下的所有 changeset 记录
     */
    public List<UpgradeRecord> findRecordsByVersion(String groupCode, String version) {
        return jdbcExecutor.query(String.format(SELECT_RECORD_COLUMNS, recordTable)
                        + " WHERE version = ?  and  group_code = ? ORDER BY id",
                rs -> mapRecord(rs), version, groupCode);
    }

    /**
     * 标记 changeset 开始执行。记录已存在时复用并重置状态，保证重跑时同一条 changeset 只有一行记录。
     *
     * @return 记录主键
     */
    public long beginChangeset(String groupCode, VersionedChangeset meta, String operator, LocalDateTime startTime) {
        Optional<UpgradeRecord> existing = findRecord(groupCode, meta.changesetId());
        if (existing.isPresent()) {
            jdbcExecutor.update("UPDATE " + recordTable
                            + " SET file_path = ?, version = ?, checksum = ?, run_always = ?, status = ?,"
                            + " executed_by = ?, start_time = ?, end_time = NULL, error_msg = NULL WHERE id = ?",
                    meta.filePath(), meta.version(), meta.checksum(), meta.runAlways() ? 1 : 0,
                    ChangesetStatus.EXECUTING.code(), operator, startTime, existing.get().id());
            return existing.get().id();
        }

        Long id = jdbcExecutor.insertAndReturnKey("INSERT INTO " + recordTable
                        + " (changeset_id, file_path, version, checksum, run_always, status, executed_by, start_time,group_code)"
                        + " VALUES (?,?, ?, ?, ?, ?, ?, ?, ?)",
                dialect.generatedKeyColumns(),
                meta.changesetId(), meta.filePath(), meta.version(), meta.checksum(),
                meta.runAlways() ? 1 : 0, ChangesetStatus.EXECUTING.code(), operator,
                Timestamp.valueOf(startTime), groupCode);

        if (id == null) {
            // 兜底：部分驱动不返回自增主键，按唯一键反查
            id = jdbcExecutor.queryFirst("SELECT id FROM " + recordTable
                            + " WHERE changeset_id = ? and  group_code = ?",
                    rs -> rs.getLong(1), meta.changesetId(), groupCode);
        }
        if (id == null) {
            throw new UpgradeException("写入 changeset 执行记录失败，未获取到主键：" + meta.changesetId()
                    + " 分组:" + groupCode);
        }
        return id;
    }

    /**
     * 标记 changeset 执行成功
     *
     * @param remark 备注，用于记录跳过原因（如 DDL 已存在、前置条件 MARK_RAN）
     */
    public void markSuccess(long id, String remark) {
        jdbcExecutor.update("UPDATE " + recordTable
                        + " SET status = ?, end_time = ?, error_msg = ? WHERE id = ?",
                ChangesetStatus.SUCCESS.code(), LocalDateTime.now(), truncate(remark), id);
    }

    /**
     * 标记 changeset 执行失败
     */
    public void markFailed(long id, String errorMsg) {
        jdbcExecutor.update("UPDATE " + recordTable
                        + " SET status = ?, end_time = ?, error_msg = ? WHERE id = ?",
                ChangesetStatus.FAILED.code(), LocalDateTime.now(), truncate(errorMsg), id);
    }

    /**
     * 仅更新 checksum，用于历史记录缺失 checksum 时补齐
     */
    public void updateChecksum(long id, String checksum) {
        jdbcExecutor.update("UPDATE " + recordTable + " SET checksum = ? WHERE id = ?", checksum, id);
    }

    /**
     * 中断恢复：把本分组残留的 EXECUTING 记录标记为 FAILED。
     * <p>
     * 必须带 {@code group_code} 条件。全局锁模式下同一个库里有多个分组，本节点执行 A 分组时
     * 完全可能有 B 分组正在别的节点上跑，无差别清理会把别人正在执行中的记录改成 FAILED，
     * 后续那个节点写 SUCCESS 之前就会被误判成「上次失败了」。分组之间只共享库、不共享进度。
     *
     * @param groupCode 分组标识，只清理该分组
     * @return 受影响行数
     */
    public int recoverStaleExecuting(String groupCode, String operator) {
        int rows = jdbcExecutor.update("UPDATE " + recordTable
                        + " SET status = ?, end_time = ?, executed_by = ?, error_msg = ?"
                        + " WHERE status = ? AND group_code = ?",
                ChangesetStatus.FAILED.code(), LocalDateTime.now(), operator,
                "进程中断，启动时恢复为 FAILED，等待重试",
                ChangesetStatus.EXECUTING.code(), groupCode);
        if (rows > 0) {
            log.warn("分组[{}]检测到 {} 条残留的执行中记录，已恢复为 FAILED，本次将重新执行", groupCode, rows);
        }
        return rows;
    }

    /**
     * 探测表是否可读，用于决定是否需要建表。
     * <p>
     * 「先探测再建表」天然存在竞争窗口：两个节点同时启动时都可能探测到表不存在，
     * 然后都去 CREATE，后到的那个必然报「表已存在」。这里不把它当成失败——
     * 建表异常时复查一次，表能读就说明别人先建好了，本节点直接继续。
     * 这样并发启动不会演变成启动失败，而真正的建表失败（权限、语法、磁盘）依然会抛出去。
     */
    private void ensureTable(String table, List<String> ddl) {
        if (jdbcExecutor.isReadable(table)) {
            return;
        }
        try {
            for (String statement : ddl) {
                jdbcExecutor.execute(statement);
            }
            log.info("已创建升级记录表：{}", table);
        } catch (RuntimeException e) {
            if (jdbcExecutor.isReadable(table)) {
                log.debug("升级记录表[{}]已由其他节点创建，忽略本次建表异常：{}", table, e.getMessage());
                return;
            }
            throw e;
        }
    }

    private UpgradeRecord mapRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UpgradeRecord(
                rs.getLong("id"),
                rs.getString("changeset_id"),
                rs.getString("file_path"),
                rs.getString("version"),
                rs.getString("checksum"),
                rs.getInt("run_always") == 1,
                rs.getString("status"),
                rs.getString("executed_by"),
                toLocalDateTime(rs.getTimestamp("start_time")),
                toLocalDateTime(rs.getTimestamp("end_time")),
                rs.getString("error_msg"));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= ERROR_MSG_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, ERROR_MSG_MAX_LENGTH);
    }
}
