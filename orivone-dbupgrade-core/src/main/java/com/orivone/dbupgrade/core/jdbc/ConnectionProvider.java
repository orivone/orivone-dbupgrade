package com.orivone.dbupgrade.core.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接提供者，core 与具体数据源之间的唯一纽带。
 * <p>
 * core 不依赖 Spring、不依赖任何连接池，只依赖这一个接口：
 * <ul>
 *     <li>Spring Boot 场景：starter 用 {@link DataSourceConnectionProvider} 包装容器的 DataSource</li>
 *     <li>非 Spring 场景：实现本接口，用 DriverManager 或自有连接池返回连接</li>
 * </ul>
 * 实现方必须保证：<b>每次调用返回一个可用的连接，且调用方负责 close</b>。
 * core 内部每次操作都会 {@code try-with-resources} 关闭连接，因此返回池化连接是安全的。
 * @author orivone
 */
public interface ConnectionProvider {

    /**
     * 获取一个连接，调用方负责关闭
     */
    Connection getConnection() throws SQLException;
}
