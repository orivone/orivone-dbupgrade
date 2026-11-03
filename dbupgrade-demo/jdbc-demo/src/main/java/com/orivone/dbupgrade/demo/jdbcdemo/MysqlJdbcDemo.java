package com.orivone.dbupgrade.demo.jdbcdemo;

/**
 * 【jdbc-demo】MySQL 版，与 {@link H2JdbcDemo} 同级的精简示例。
 * <p>
 * 与 H2 版的唯一差别是脚本目录与默认连接：MySQL 的 DDL 有自己的写法
 * （{@code ENGINE=InnoDB}、{@code utf8mb4}、{@code TINYINT}），不能和 H2 共用一份脚本，
 * 所以每个数据库一套脚本、一个 demo，靠类名区分。
 * <p>
 * 默认连接与 spring-boot-demo 的 {@code application-mysql.yml} 保持一致
 * （{@code orivone_upgrade_demo} 库 / {@code orivone_upgrade_demo} 账号），先跑过 Boot 版的话这里可直接运行：
 * <pre>
 *   java -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.MysqlJdbcDemo
 *
 *   # 换成自己的库
 *   java -Ddb.url="jdbc:mysql://127.0.0.1:3306/other_db?useSSL=false&amp;serverTimezone=Asia/Shanghai" \
 *        -Ddb.username=root -Ddb.password=123456 \
 *        -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.MysqlJdbcDemo
 * </pre>
 * 库需要事先存在，账号需要有建表权限。支持 MySQL 5.7 及以上
 * （驱动固定 8.0.x，能同时连 5.7 / 8.0 / 8.4，见工程根 pom 的说明）。
 * @author orivone
 */
public final class MysqlJdbcDemo {

    private MysqlJdbcDemo() {
    }

    public static void main(String[] args) {
        JdbcDemoSupport.run("mysql",
                "jdbc:mysql://127.0.0.1:3306/orivone_upgrade_demo"
                        + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                        + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                "orivone_upgrade_demo", "orivone_upgrade_demo");
    }
}
