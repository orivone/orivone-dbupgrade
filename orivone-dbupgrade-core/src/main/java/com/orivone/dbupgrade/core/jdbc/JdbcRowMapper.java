package com.orivone.dbupgrade.core.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 行映射器，把结果集当前行转换为对象。
 * <p>
 * 与 Spring 的 {@code JdbcRowMapper} 同名同形，但<b>不带行号参数</b>：框架内的查询都不依赖行号，
 * 少一个参数可以让实现写成方法引用或极短的 lambda。
 * <p>
 * 约定：实现方只读取当前行，不要调用 {@code next()}，游标移动由 {@link JdbcExecutor} 负责。
 * @author orivone
 */
public interface JdbcRowMapper<T> {

    /**
     * 把结果集当前行转换为对象
     */
    T mapRow(ResultSet rs) throws SQLException;
}
