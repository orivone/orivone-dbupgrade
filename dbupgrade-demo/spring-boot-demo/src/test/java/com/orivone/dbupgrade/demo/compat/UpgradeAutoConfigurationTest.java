package com.orivone.dbupgrade.demo.compat;

import com.orivone.dbupgrade.core.dialect.UpgradeContext;
import com.orivone.dbupgrade.spring.start.UpgradeAutoConfiguration;
import com.orivone.dbupgrade.spring.start.UpgradeRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配的生效条件。
 * <p>
 * 守的是一条边界：<b>没接数据库的应用引入 starter 不能起不来</b>。
 * {@code javax.sql.DataSource} 是 JDK 自带的类，拿它做 {@code @ConditionalOnClass} 条件永远成立，
 * 于是纯 Web 应用也会被拉进装配流程，然后在「找不到数据源」那里抛异常阻断启动。
 * 这里用没有 DataSource 的空上下文验证条件确实拦住了。
 * <p>
 * 「有数据源时正常装配」由 {@link BootCompatibilityTest} 覆盖：它启动完整应用并断言升级真的执行了。
 * @author orivone
 */
class UpgradeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UpgradeAutoConfiguration.class));

    @Test
    @DisplayName("没有 DataSource 时不装配任何升级组件")
    void skippedWithoutDataSource() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(UpgradeContext.class);
            assertThat(context).doesNotHaveBean(UpgradeRunner.class);
        });
    }

    @Test
    @DisplayName("enabled=false 时不装配")
    void skippedWhenDisabled() {
        runner.withPropertyValues("orivone.dbupgrade.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(UpgradeContext.class));
    }
}
