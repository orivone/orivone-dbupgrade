package com.orivone.dbupgrade.core.dialect;

import com.orivone.dbupgrade.core.jdbc.ConnectionProvider;
import com.orivone.dbupgrade.core.jdbc.JdbcExecutor;
import com.orivone.dbupgrade.core.schema.SchemaMetadata;

/**
 * 升级用的数据库上下文：连接来源、执行器、方言、元数据检查器，四样绑在一起。
 * <p>
 * 绑在一起是为了不串库。多数据源时，元数据检查、记录表读写、脚本执行如果各自去容器里取，
 * 完全可能取到不同的库——记录写进 A 库、DDL 在 B 库执行，版本永远推不动，还很难查。
 * 这里全部从同一个 {@link ConnectionProvider} 派生，从源头上避免。
 * <p>
 * 手里只有 {@link ConnectionProvider} 和 {@link JdbcExecutor} 两个抽象，不带 Spring、不带连接池，
 * 所以纯 JDBC 环境也能直接 new 出来用。
 * @author orivone
 */
public class UpgradeContext {

    private final ConnectionProvider connectionProvider;

    private final JdbcExecutor jdbcExecutor;

    private final UpgradeDialect dialect;

    private final SchemaMetadata schemaMetadata;

    public UpgradeContext(ConnectionProvider connectionProvider, UpgradeDialect dialect) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("ConnectionProvider 不能为空");
        }
        if (dialect == null) {
            throw new IllegalArgumentException("UpgradeDialect 不能为空");
        }
        this.connectionProvider = connectionProvider;
        this.jdbcExecutor = new JdbcExecutor(connectionProvider);
        this.dialect = dialect;
        this.schemaMetadata = dialect.newSchemaMetadata(jdbcExecutor);
    }

    public ConnectionProvider connectionProvider() {
        return connectionProvider;
    }

    public JdbcExecutor jdbcExecutor() {
        return jdbcExecutor;
    }

    public UpgradeDialect dialect() {
        return dialect;
    }

    public SchemaMetadata schemaMetadata() {
        return schemaMetadata;
    }
}
