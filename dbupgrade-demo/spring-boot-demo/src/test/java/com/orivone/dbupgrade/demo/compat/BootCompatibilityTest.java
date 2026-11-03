package com.orivone.dbupgrade.demo.compat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring Boot 三线兼容回归。
 * <p>
 * 同一个测试类会被三个 profile 分别跑一遍，用来证明「starter 一份源码通吃
 * Boot 2.7 / 3.x / 4.x」是真能跑起来，而不只是能编过：
 * <pre>
 *   mvn -pl dbupgrade-demo/spring-boot-demo -Pboot2 test
 *   mvn -pl dbupgrade-demo/spring-boot-demo -Pboot3 test
 *   mvn -pl dbupgrade-demo/spring-boot-demo         test   # Boot 4（默认）
 * </pre>
 * <p>
 * <b>这个测试为什么要启动完整容器，而不是只测几个类</b>
 * <p>
 * 三个版本之间真正会出问题的地方，全在容器启动这一段：
 * <ul>
 *     <li>{@code META-INF/spring/...AutoConfiguration.imports} 能否被识别
 *         （Boot 2.7 起支持，3.x/4.x 是唯一方式）—— 认不出来就是「什么都没发生」，
 *         编译期完全发现不了</li>
 *     <li>自动配置的装配顺序、条件注解的判定结果</li>
 *     <li>{@code ApplicationRunner} 在什么时机被调用</li>
 *     <li>数据源自动配置本身的类名/包名变化</li>
 * </ul>
 * 只做编译验证的话，上面这些一条都测不到。所以这里用 H2 内存库把容器真的拉起来，
 * 再用 JDBC 反查升级结果。
 * <p>
 * <b>为什么不写成 @SpringBootTest</b>
 * <p>
 * {@code @SpringBootTest} 的方言与默认注解在两个大版本之间有过调整，
 * 用 {@link SpringApplicationBuilder} 手工启动反而更稳、更贴近使用方的真实形态，
 * 也不会被测试框架的版本差异干扰结论。
 * @author orivone
 */
class BootCompatibilityTest {

    /**
     * 一次启动要验证的四件事，全部通过才算兼容：
     * <ol>
     *     <li>容器能起得来（自动配置被识别、依赖能被满足）</li>
     *     <li>升级真的执行了（版本记录表出现 1 行目标版本）</li>
     *     <li>changeset 真的执行了（记录表出现 2 行 SUCCESS）</li>
     *     <li>1.1.0 里的 ALTER 真的生效了（新列能查出来）</li>
     * </ol>
     */
    @Test
    void starterShouldBootAndUpgradeOnCurrentSpringBootVersion() throws Exception {

        ConfigurableApplicationContext context = new SpringApplicationBuilder(CompatUpgradeApplication.class)
                .web(WebApplicationType.NONE)
                /*
                 * 必须用命令行参数（--key=value）而不是 .properties(...)：
                 * .properties() 写入的是「默认属性」，优先级最低，会被
                 * src/main/resources 下当前激活 profile 的数据源配置盖掉 ——
                 * 那样测试就会悄悄连到本机真实库上，既不可重复也不安全。
                 * 命令行参数优先级最高，才能保证测试干净、自洽、与机器环境无关。
                 *
                 * 表名也要显式给：application.yml 里通过 db.table-prefix 加了 jtest_ 前缀，
                 * 测试不跟随这个前缀，才能断言到固定的表名。
                 */
                .run(
                        // 内存库 + DB_CLOSE_DELAY=-1：容器关闭前库一直存活，便于事后抽查
                        "--spring.datasource.url=jdbc:h2:mem:compat_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        // 升级配置：目标版本由 compat 注册表声明到 1.1.0，锁用单机实现避免依赖锁表语义
                        "--orivone.dbupgrade.lock-type=local",
                        /*
                         * 方言显式钉死 h2，不吃 profile 的隐含配置。
                         * 只覆盖 spring.datasource.url 是不会连带改 db-type 的：
                         * 一旦有人本地用 -Dspring.profiles.active=mysql 跑测试，
                         * 升级就会拿 MySQL 方言的 DDL（ENGINE=InnoDB / UNIQUE INDEX）
                         * 去建 H2 表，报 "Syntax error ... expected ("。
                         */
                        "--orivone.dbupgrade.db-type=h2",
                        "--orivone.dbupgrade.script-root=compat-scripts",
                        "--orivone.dbupgrade.operator=compat-test",
                        "--orivone.dbupgrade.version-table=dbupgrade_version",
                        "--orivone.dbupgrade.record-table=dbupgrade_record",
                        "--orivone.dbupgrade.lock-table=dbupgrade_lock",
                        // 关掉 banner 与启动噪音，失败时日志才好读
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=WARN"
                );

        try {
            DataSource dataSource = context.getBean(DataSource.class);
            assertTrue(dataSource != null, "数据源应当由 Boot 自动配置提供");

            assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM dbupgrade_version"),
                    "版本记录表应当只有 1 行（每个 group 一行水位）");

            assertEquals(1, count(dataSource,
                            "SELECT COUNT(*) FROM dbupgrade_version WHERE version = '1.1.0'"),
                    "版本水位应当推进到目标版本 1.1.0");

            assertEquals(3, count(dataSource, "SELECT COUNT(*) FROM dbupgrade_record"),
                    "install 基线 2 个 changeset + 1.1.0 的 1 个，应当有 3 行记录");

            assertEquals(3, count(dataSource,
                            "SELECT COUNT(*) FROM dbupgrade_record WHERE status = 'SUCCESS'"),
                    "三条 changeset 都应当是 SUCCESS");

            // 1.1.0 里的 ALTER TABLE ... ADD COLUMN 是否真的落地
            assertEquals(1, count(dataSource,
                            "SELECT COUNT(*) FROM demo_item WHERE remark IS NULL"),
                    "1.1.0 新增的 remark 列应当存在且对已有行取 NULL");

        } finally {
            context.close();
        }
    }

    private static int count(DataSource dataSource, String sql) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            Statement statement = connection.createStatement();
            try {
                ResultSet resultSet = statement.executeQuery(sql);
                try {
                    return resultSet.next() ? resultSet.getInt(1) : -1;
                } finally {
                    resultSet.close();
                }
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }
}
