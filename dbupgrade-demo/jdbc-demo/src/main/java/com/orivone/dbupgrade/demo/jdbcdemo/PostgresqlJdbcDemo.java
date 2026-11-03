package com.orivone.dbupgrade.demo.jdbcdemo;

/**
 * 【jdbc-demo】PostgreSQL 版，与 {@link H2JdbcDemo} 同级的精简示例。
 * <p>
 * PostgreSQL 的 DDL 与标准 SQL 最接近，脚本里只用到 {@code BIGINT} / {@code VARCHAR} /
 * {@code SMALLINT} 这类通用类型。人大金仓、openGauss、GaussDB 等衍生库同样归入
 * {@code postgresql} 方言。
 * <p>
 * 运行（默认连 127.0.0.1:5432 的 postgres 库，postgres/postgres，可用系统属性覆盖）：
 * <pre>
 *   java -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.PostgresqlJdbcDemo
 *
 *   java -Ddb.url="jdbc:postgresql://127.0.0.1:5432/demo" \
 *        -Ddb.username=postgres -Ddb.password=postgres \
 *        -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.PostgresqlJdbcDemo
 * </pre>
 * 库需要事先存在，且账号在 {@code public} schema 下有建表权限。
 * @author orivone
 */
public final class PostgresqlJdbcDemo {

    private PostgresqlJdbcDemo() {
    }

    public static void main(String[] args) {
        JdbcDemoSupport.run("postgresql", "jdbc:postgresql://127.0.0.1:5432/postgres", "postgres", "postgres");
    }
}
