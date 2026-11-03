package com.orivone.dbupgrade.demo.spring;



import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

/**
 * 【demo】升级能力的最小可运行入口。
 * <p>
 * 这是一个“壳”，不是你接入时要抄的代码。真实项目里只需要做两件事：
 * <ol>
 *     <li>实现 {@link com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry}，
 *     声明版本、文件归属、分组与执行顺序（见 {@link DemoUpgradeVersionRegistry}、
 *     {@link ComponentUpgradeVersionRegistry}）</li>
 *     <li>在 application.yml 配置 {@code orivone.dbupgrade.*}（数据源、锁、记录表名等）</li>
 * </ol>
 * {@code UpgradeRunner} 会自动在启动时接管升级，不需要写任何启动代码。
 * <p>
 * 本 demo 用 {@code @SpringBootApplication(scanBasePackages = "com.orivone.dbupgrade")}
 * 直接扫描升级模块，因此 {@code UpgradeAutoConfiguration}、{@code UpgradeRunner}
 * 与两个注册表都会生效；数据源走 Spring Boot 标准配置
 * （{@code src/main/resources/application.yml}），不做手工装配。
 * <p>
 * 运行方式：直接运行本类的 main 方法，或
 * {@code mvn -pl dbupgrade-demo/spring-boot-demo spring-boot:run}。
 * 运行前请按需修改 {@code dbupgrade-demo/spring-boot-demo/src/main/resources/application.yml}
 * 中的数据库地址与账号密码。
 * @author orivone
 */
@Configuration(proxyBeanMethods = false)
/*@Import({
        UpgradeAutoConfiguration.class,
        UpgradeDemoApplication.DemoDataSourceConfig.class,
        DemoUpgradeVersionRegistry.class,ComponentUpgradeVersionRegistry.class,
       UpgradeRunner.class
})*/
@SpringBootApplication(scanBasePackages="com.orivone.dbupgrade")
public class UpgradeDemoApplication {

    public static void main(String[] args) {
       /*new SpringApplicationBuilder(UpgradeDemoApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                // 只加载 demo 专属配置，不污染业务项目的 application.yml
                .properties("spring.config.name=demo-application")
                .run(args);*/
        SpringApplication.run(UpgradeDemoApplication.class, args);


    }

    /**
     * demo 专用数据源：无连接池，一次性命令行场景足够，且不依赖任何连接池实现。
     * <p>
     * 当前已注释停用：demo 改为使用 Spring Boot 自动配置的数据源（读取 test/resources/application.yml），
     * 这样与真实项目的接入方式保持一致，也能顺带验证 datasource-bean 的解析逻辑。
     * 若要在无 spring-boot-starter-jdbc 的环境下跑，可恢复下面的手工装配。
     */
    /*@Configuration(proxyBeanMethods = false)
     static class DemoDataSourceConfig {

        @Bean
        DataSource dataSource(Environment environment) {
            String url = environment.getRequiredProperty("spring.datasource.url");
            String username = environment.getProperty("spring.datasource.username", "root");
            String password = environment.getProperty("spring.datasource.password", "");
            String driver = environment.getProperty("spring.datasource.driver-class-name",
                    "com.mysql.cj.jdbc.Driver");
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
            dataSource.setDriverClassName(driver);
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }*/
}
