package com.orivone.dbupgrade.core.jdbc;

import com.orivone.dbupgrade.core.StrUtils;
import com.orivone.dbupgrade.core.UpgradeException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 极简 JDBC 执行器，只覆盖框架需要的操作。
 * <p>
 * 执行 DDL、更新、插入并回填自增主键、查整数、查时间戳、查多行、查单行，
 * 不做通用 DAO 封装，换来的是零依赖与 Java 8 字节码。
 * <p>
 * 三条约定：
 * <ol>
 *     <li>每次操作现取连接，用完就关。锁语义不依赖长事务，短连接足够。</li>
 *     <li>写之前强制 {@code autoCommit=true}。连接池常把连接置成手动提交，不重置的话 DML 会白跑。</li>
 *     <li>{@link SQLException} 一律包成 {@link UpgradeException}，原始异常挂在 cause 上——
 *     锁实现要靠它辨认唯一键冲突。</li>
 * </ol>
 * 参数绑定同时做了类型归一：{@code LocalDateTime → Timestamp}、{@code LocalDate → Date}、枚举取 name()，
 * 避免不同驱动对 {@code setObject} 的处理差异。
 * @author orivone
 */
public class JdbcExecutor {

    private static final int SQL_SUMMARY_LENGTH = 200;

    private final ConnectionProvider connectionProvider;

    public JdbcExecutor(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("ConnectionProvider 不能为空");
        }
        this.connectionProvider = connectionProvider;
    }

    public ConnectionProvider connectionProvider() {
        return connectionProvider;
    }

    /**
     * 执行一条不需要返回值的语句（DDL、SET 等）
     */
    public void execute(String sql) {
        try (Connection connection = open();
             Statement statement = connection.createStatement()) {
            prepareForWrite(connection);
            statement.execute(sql);
        } catch (SQLException e) {
            throw wrap("SQL 执行失败", sql, e);
        }
    }

    /**
     * 执行一条更新语句（INSERT / UPDATE / DELETE）
     *
     * @return 受影响行数
     */
    public int update(String sql, Object... args) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            prepareForWrite(connection);
            bind(statement, args);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw wrap("SQL 更新失败", sql, e);
        }
    }

    /**
     * 执行插入并回填自增主键。
     *
     * @param sql        插入语句
     * @param keyColumns 需要回填的列名；为 null 时使用 JDBC 标准的 RETURN_GENERATED_KEYS
     * @param args       参数
     * @return 回填到的主键；驱动未返回时返回 null，由调用方决定是否按唯一键反查
     */
    public Long insertAndReturnKey(String sql, String[] keyColumns, Object... args) {
        try (Connection connection = open()) {
            prepareForWrite(connection);
            try (PreparedStatement statement = keyColumns == null
                    ? connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                    : connection.prepareStatement(sql, keyColumns)) {
                bind(statement, args);
                statement.executeUpdate();
                return readGeneratedKey(statement);
            }
        } catch (SQLException e) {
            throw wrap("SQL 插入失败", sql, e);
        }
    }

    /**
     * 查询单个整数（COUNT 之类）
     */
    public int queryInt(String sql, Object... args) {
        Integer value = extract(sql, rs -> rs.next() ? toInteger(rs.getObject(1)) : null, args);
        return value == null ? 0 : value;
    }

    /**
     * 查询单个时间戳，无行或为空时返回 null
     */
    public Timestamp queryTimestamp(String sql, Object... args) {
        return extract(sql, rs -> rs.next() ? rs.getTimestamp(1) : null, args);
    }

    /**
     * 查询多行
     */
    public <T> List<T> query(String sql, JdbcRowMapper<T> mapper, Object... args) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.mapRow(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw wrap("SQL 查询失败", sql, e);
        }
    }

    /**
     * 查询首行，无结果返回 null
     */
    public <T> T queryFirst(String sql, JdbcRowMapper<T> mapper, Object... args) {
        List<T> rows = query(sql, mapper, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 用提取器读整个结果集（取首行首列、判断有无行等特殊场景）
     */
    public <T> T extract(String sql, ResultSetExtractor<T> extractor, Object... args) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                return extractor.extract(rs);
            }
        } catch (SQLException e) {
            throw wrap("SQL 查询失败", sql, e);
        }
    }

    /**
     * 表是否可读，用于「建表前探测」；任何异常都视为不可读
     */
    public boolean isReadable(String table) {
        try {
            queryInt("SELECT COUNT(*) FROM " + table + " WHERE 1 = 0");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Connection open() throws SQLException {
        return connectionProvider.getConnection();
    }

    /**
     * 写操作前确保自动提交，避免连接池的非自动提交模式把变更留在未提交事务里
     */
    private static void prepareForWrite(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.setAutoCommit(true);
        }
    }

    private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            int index = i + 1;
            if (value == null) {
                bindNull(statement, index);
            } else if (value instanceof LocalDateTime) {
                statement.setTimestamp(index, Timestamp.valueOf((LocalDateTime) value));
            } else if (value instanceof LocalDate) {
                statement.setDate(index, Date.valueOf((LocalDate) value));
            } else if (value instanceof Enum) {
                statement.setString(index, ((Enum<?>) value).name());
            } else {
                statement.setObject(index, value);
            }
        }
    }

    /**
     * 绑定 null 参数。
     * <p>
     * 不能图省事写 {@code setObject(index, null)}：Oracle 系驱动会把它当作 {@code Types.OTHER}，
     * 直接抛「无效的列类型: 1111」；也不能一律 {@code setNull(index, Types.VARCHAR)}：
     * PostgreSQL 会按 varchar 下发，目标列是数值或时间类型时同样报类型不匹配。
     * <p>
     * 先走标准的 {@code Types.NULL}（PostgreSQL 借此让服务端按目标列推断类型），驱动不认时
     * 退到 {@code Types.VARCHAR}（Oracle 系驱动的通用写法）。两条都失败才把异常抛出去，
     * 此时说明是该驱动自身限制，交给调用方从错误信息里定位。
     */
    private static void bindNull(PreparedStatement statement, int index) throws SQLException {
        try {
            statement.setNull(index, Types.NULL);
        } catch (SQLException e) {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value).trim());
    }

    /**
     * 读取回填的主键，兼容三种驱动行为：
     * 单列、按列名返回多列（Oracle）、以及完全不返回（此时返回 null 交给调用方兜底）
     */
    private static Long readGeneratedKey(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys == null || !keys.next()) {
                return null;
            }
            ResultSetMetaData metaData = keys.getMetaData();
            int count = metaData == null ? 1 : metaData.getColumnCount();
            Long first = null;
            for (int i = 1; i <= count; i++) {
                Object value = keys.getObject(i);
                if (!(value instanceof Number)) {
                    continue;
                }
                long candidate = ((Number) value).longValue();
                String label = columnLabel(metaData, i);
                if (label != null && "id".equals(label.toLowerCase(Locale.ROOT))) {
                    return candidate;
                }
                if (first == null) {
                    first = candidate;
                }
            }
            return first;
        } catch (SQLException e) {
            // 部分驱动不支持 getGeneratedKeys，交给调用方按唯一键反查
            return null;
        }
    }

    private static String columnLabel(ResultSetMetaData metaData, int index) {
        try {
            return metaData == null ? null : metaData.getColumnLabel(index);
        } catch (SQLException e) {
            return null;
        }
    }

    private static UpgradeException wrap(String action, String sql, SQLException e) {
        String message = action + "：" + StrUtils.abbreviate(sql, SQL_SUMMARY_LENGTH)
                + "；原因：" + e.getMessage()
                + "（SQLState=" + e.getSQLState() + ", errorCode=" + e.getErrorCode() + "）";
        return new UpgradeException(message, e);
    }
}
