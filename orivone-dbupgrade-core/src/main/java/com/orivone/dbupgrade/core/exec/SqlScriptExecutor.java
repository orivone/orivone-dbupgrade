package com.orivone.dbupgrade.core.exec;

import com.orivone.dbupgrade.core.StrUtils;
import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.jdbc.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * SQL 执行器，按顺序执行一个 changeset 内的全部语句。
 * <p>
 * 连接按 changeset 复用：同一个 changeset 的全部语句走同一个连接，{@code SET @x}、
 * {@code SET FOREIGN_KEY_CHECKS=0}、{@code CREATE TEMPORARY TABLE} 这类会话级语句才有意义，
 * 连接借还次数也从「每条语句一次」降成「每个 changeset 一次」。连接只活在一个 changeset 内，
 * 不会从头占到尾。
 * <p>
 * 执行前把 autoCommit 打开，免得连接池的手动提交模式让 DML 悄悄留在未提交事务里。
 * <b>不包事务</b>：MySQL 这类库的 DDL 自己就隐式提交，包一层只是看着安全，同一 changeset 里
 * 几条语句跑一半失败照样回不去；重跑安全靠 changeset 幂等与 DDL 存在性判断。
 * <p>
 * 一条语句失败就中断整批，连接随 try-with-resources 关掉，异常抛给调用方去记 FAILED。
 * @author orivone
 */
public class SqlScriptExecutor implements ScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(SqlScriptExecutor.class);

    private static final int SUMMARY_LENGTH = 300;

    private final ConnectionProvider connectionProvider;

    private final int queryTimeoutSeconds;

    public SqlScriptExecutor(ConnectionProvider connectionProvider, int queryTimeoutSeconds) {
        this.connectionProvider = connectionProvider;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    /**
     * 执行单条语句。每次调用独立取连接，适用于零散语句；
     * changeset 的主体执行走 {@link #executeAll(List, String, String)}。
     */
    @Override
    public void execute(String statement, String version, String changesetId) {
        if (StrUtils.isBlank(statement)) {
            return;
        }
        long begin = System.currentTimeMillis();
        try (Connection connection = connectionProvider.getConnection();
             Statement stmt = connection.createStatement()) {
            prepare(connection);
            applyTimeout(stmt);
            stmt.execute(statement);
            log.debug("SQL 执行成功（{} ms）：{}", System.currentTimeMillis() - begin, abbreviate(statement));
        } catch (SQLException e) {
            String message = "SQL 执行失败：" + abbreviate(statement)
                    + "；原因：" + e.getMessage()
                    + "（SQLState=" + e.getSQLState() + ", errorCode=" + e.getErrorCode() + "）";
            throw new UpgradeException(message, version, changesetId, e);
        } catch (Exception e) {
            throw new UpgradeException("SQL 执行异常：" + abbreviate(statement) + "；原因：" + e.getMessage(),
                    version, changesetId, e);
        }
    }

    /**
     * 执行一个 changeset 内的全部语句，整批共用一个连接。
     * <p>
     * 语句顺序即脚本顺序；任一语句失败立即中断，后面尚未执行的语句不再执行
     * （与「每条语句独立连接」时的行为一致，changeset 仍然是失败即停的原子单位）。
     */
    @Override
    public void executeAll(List<String> statements, String version, String changesetId) {
        if (statements == null || statements.isEmpty()) {
            return;
        }
        long begin = System.currentTimeMillis();
        try (Connection connection = connectionProvider.getConnection()) {
            prepare(connection);
            int index = 0;
            for (String statement : statements) {
                index++;
                if (StrUtils.isBlank(statement)) {
                    continue;
                }
                try (Statement stmt = connection.createStatement()) {
                    applyTimeout(stmt);
                    stmt.execute(statement);
                    log.debug("SQL 执行成功（{} ms）[{} 第 {}/{} 条]：{}",
                            System.currentTimeMillis() - begin, changesetId, index, statements.size(),
                            abbreviate(statement));
                } catch (SQLException e) {
                    // 带上「第几条」：整批共用一个连接时，日志里必须能定位到具体是哪条语句炸的
                    String message = "SQL 执行失败（changeset 内第 " + index + "/" + statements.size() + " 条）："
                            + abbreviate(statement)
                            + "；原因：" + e.getMessage()
                            + "（SQLState=" + e.getSQLState() + ", errorCode=" + e.getErrorCode() + "）";
                    throw new UpgradeException(message, version, changesetId, e);
                }
            }
            log.debug("changeset[{}] 的 {} 条语句全部执行完成（{} ms，共用 1 个连接）",
                    changesetId, statements.size(), System.currentTimeMillis() - begin);
        } catch (SQLException e) {
            // 走到这里只可能是取连接或 prepare（autoCommit）失败：
            // 语句创建与执行的异常已在循环内被拦下并带上「第几条」
            throw new UpgradeException("获取数据库连接或准备会话失败：" + e.getMessage(),
                    version, changesetId, e);
        }
    }

    private void prepare(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.setAutoCommit(true);
        }
    }

    /**
     * 设置语句超时。部分驱动不支持，失败只记 debug 不中断执行
     */
    private void applyTimeout(Statement stmt) {
        if (queryTimeoutSeconds <= 0) {
            return;
        }
        try {
            stmt.setQueryTimeout(queryTimeoutSeconds);
        } catch (SQLException e) {
            log.debug("设置 SQL 超时时间失败，忽略：{}", e.getMessage());
        }
    }

    private static String abbreviate(String sql) {
        return StrUtils.abbreviate(sql, SUMMARY_LENGTH);
    }
}
