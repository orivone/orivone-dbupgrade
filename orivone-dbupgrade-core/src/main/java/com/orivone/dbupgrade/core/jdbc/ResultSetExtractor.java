package com.orivone.dbupgrade.core.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 结果集提取器，把整个结果集转换为一个值（取首行首列、判断有无行等）。
 * <p>
 * 与 {@link JdbcRowMapper} 的区别：JdbcRowMapper 只处理「当前行」，由框架遍历；
 * 提取器拿到的是未移动游标的结果集，自己决定怎么读（例如 {@code rs.next() ? rs.getObject(1) : null}）。
 * @author orivone
 */
public interface ResultSetExtractor<T> {

    /**
     * 从结果集中提取结果，游标由实现方自己移动
     */
    T extract(ResultSet rs) throws SQLException;
}
