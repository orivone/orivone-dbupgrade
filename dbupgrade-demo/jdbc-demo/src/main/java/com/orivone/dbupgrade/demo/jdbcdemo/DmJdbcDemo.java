package com.orivone.dbupgrade.demo.jdbcdemo;

/**
 * 【jdbc-demo】达梦 DM 版，与 {@link H2JdbcDemo} 同级的精简示例。
 * <p>
 * 达梦语法高度兼容 Oracle，框架内部直接复用 Oracle 方言实现，脚本也按 Oracle 风格写
 * （{@code VARCHAR2} / {@code NUMBER}），只有连接串与驱动不同。
 * <p>
 * 运行（默认连 127.0.0.1:5236，SYSDBA/SYSDBA001，可用系统属性覆盖）：
 * <pre>
 *   java -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.DmJdbcDemo
 *
 *   java -Ddb.url="jdbc:dm://127.0.0.1:5236" \
 *        -Ddb.username=SYSDBA -Ddb.password=SYSDBA001 \
 *        -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.DmJdbcDemo
 * </pre>
 * 注意达梦默认大小写敏感，脚本里的表名 / 列名统一用小写并保持不加引号，
 * 由数据库按默认规则折叠，避免出现「建表成功但查不到」的情况。
 * @author orivone
 */
public final class DmJdbcDemo {

    private DmJdbcDemo() {
    }

    public static void main(String[] args) {
        JdbcDemoSupport.run("dm", "jdbc:dm://127.0.0.1:5236", "SYSDBA", "SYSDBA001");
    }
}
