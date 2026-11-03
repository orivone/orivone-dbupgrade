package com.orivone.dbupgrade.demo.jdbcdemo;

/**
 * 【jdbc-demo】Oracle 版，与 {@link H2JdbcDemo} 同级的精简示例。
 * <p>
 * Oracle 脚本有三处必须区别对待的地方，都体现在 {@code upgrade-scripts/oracle/} 里：
 * <ul>
 *     <li>类型用 {@code NUMBER(19)} / {@code VARCHAR2(64)}，没有 {@code BIGINT} / {@code VARCHAR}；</li>
 *     <li>加列写 {@code ALTER TABLE t ADD col VARCHAR2(32)}，不带 {@code COLUMN} 关键字
 *         （带 {@code COLUMN} 在 Oracle 也能跑，但不带更地道；框架两种都能识别）；</li>
 *     <li>DDL 隐式提交，失败无法整体回滚，只能靠幂等重跑补齐。</li>
 * </ul>
 * 运行（默认连 127.0.0.1:1521 的 ORCLPDB1，scott/tiger，可用系统属性覆盖）：
 * <pre>
 *   java -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.OracleJdbcDemo
 *
 *   java -Ddb.url="jdbc:oracle:thin:@//127.0.0.1:1521/ORCLPDB1" \
 *        -Ddb.username=scott -Ddb.password=tiger \
 *        -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.OracleJdbcDemo
 * </pre>
 * 账号需要有 {@code CREATE TABLE} 权限（{@code scott} 默认就有）。
 * @author orivone
 */
public final class OracleJdbcDemo {

    private OracleJdbcDemo() {
    }

    public static void main(String[] args) {
        JdbcDemoSupport.run("oracle", "jdbc:oracle:thin:@//127.0.0.1:1521/ORCLPDB1", "scott", "tiger");
    }
}
