package com.orivone.dbupgrade.demo.jdbcdemo;

/**
 * 【jdbc-demo】SQL Server 版，与 {@link H2JdbcDemo} 同级的精简示例。
 * <p>
 * SQL Server 脚本的两点差异，见 {@code upgrade-scripts/sqlserver/}：
 * <ul>
 *     <li>字符串列用 {@code NVARCHAR}，中文常量要加 {@code N} 前缀（{@code N'张三'}），否则可能变成问号；</li>
 *     <li>没有 {@code CREATE TABLE IF NOT EXISTS}，建表由框架先探测再决定，脚本里写普通 {@code CREATE TABLE} 即可。</li>
 * </ul>
 * 运行（默认连 127.0.0.1:1433 的 dbupgrade_demo 库，sa/yourStrong(!)Password，可用系统属性覆盖）：
 * <pre>
 *   java -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.SqlServerJdbcDemo
 *
 *   java -Ddb.url="jdbc:sqlserver://127.0.0.1:1433;databaseName=demo;encrypt=false" \
 *        -Ddb.username=sa -Ddb.password=yourStrong(!)Password \
 *        -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.SqlServerJdbcDemo
 * </pre>
 * 本模块引的驱动是 {@code mssql-jdbc 13.x}，运行时需要 JDK 11+；
 * 若要在 JDK 8 上跑，把驱动换成 {@code 10.2.3.jre8} 即可（框架本身仍是 Java 8 字节码）。
 * @author orivone
 */
public final class SqlServerJdbcDemo {

    private SqlServerJdbcDemo() {
    }

    public static void main(String[] args) {
        JdbcDemoSupport.run("sqlserver",
                "jdbc:sqlserver://127.0.0.1:1433;databaseName=dbupgrade_demo;encrypt=false;trustServerCertificate=true",
                "sa", "yourStrong(!)Password");
    }
}
