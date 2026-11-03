package com.orivone.dbupgrade.core.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 基于 {@link DriverManager} 的连接提供者，用于无连接池的最小接入场景。
 * <p>
 * 每次调用都新建连接、用完即关闭，性能不如连接池，但胜在零依赖，
 * 适合命令行升级工具、单元测试、以及只跑一次性升级脚本的场合。
 * <p>
 * 生产环境建议自行实现 {@link ConnectionProvider} 接入 HikariCP / Druid 等连接池，
 * 或直接用 {@link DataSourceConnectionProvider} 包装既有数据源。
 * @author orivone
 */
public class DriverManagerConnectionProvider implements ConnectionProvider {

    private final String url;

    private final String username;

    private final String password;

    public DriverManagerConnectionProvider(String url, String username, String password) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库连接地址不能为空");
        }
        this.url = url.trim();
        this.username = username;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        // DriverManager 的超时（秒）沿用系统属性，避免驱动内部无限等待
        return DriverManager.getConnection(url, username, password);
    }

    public String url() {
        return url;
    }
}
