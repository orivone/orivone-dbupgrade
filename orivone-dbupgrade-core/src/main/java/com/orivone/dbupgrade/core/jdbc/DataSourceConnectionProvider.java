package com.orivone.dbupgrade.core.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 基于 {@link DataSource} 的连接提供者。
 * <p>
 * {@code javax.sql.DataSource} 是 JDK 自带类型（Java 8 起就有，且不受 Jakarta 命名空间迁移影响），
 * 因此这里既不算第三方依赖，也能同时适配 Spring Boot 2.7 / 3.x / 4.x 的 DataSource。
 * <p>
 * 纯 JDBC 场景（无连接池）可直接用 {@link DriverManagerConnectionProvider}。
 * @author orivone
 */
public class DataSourceConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    public DataSourceConnectionProvider(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource 不能为空");
        }
        this.dataSource = dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DataSource dataSource() {
        return dataSource;
    }
}
