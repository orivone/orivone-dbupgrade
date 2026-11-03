package com.orivone.dbupgrade.demo.compat;

import com.orivone.dbupgrade.core.model.UpgradeVersion;
import com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;

/**
 * 兼容性测试专用的最小应用。
 * <p>
 * 不放在 demo 主源码里，是因为它只服务测试：不连 MySQL、不需要 application.yml、
 * 也不参与打包。测试进程用 {@code SpringApplicationBuilder} 直接拉起它，
 * 走的是和真实业务完全一样的路径 —— 自动配置 + 注册表 Bean + ApplicationRunner 触发。
 * <p>
 * 扫描范围限定在 {@code com.orivone.dbupgrade.demo.compat} 包，避免把 demo 主程序
 * （连 MySQL 的 {@code UpgradeDemoApplication}）也扫进来。
 * @author orivone
 */
@SpringBootApplication
public class CompatUpgradeApplication {

    /**
     * 测试注册表：两个版本，目标版本 1.1.0。
     * <p>
     * 脚本用最朴素的 SQL，H2 / MySQL 都能跑，测试固定用 H2 内存库。
     */
    @Bean
    public UpgradeVersionRegistry compatUpgradeVersionRegistry() {
        return new UpgradeVersionRegistry() {
            @Override
            public List<UpgradeVersion> versions() {
                return Arrays.asList(
                        new UpgradeVersion("1.0.0", true, Arrays.asList(
                                "compat-scripts/install/compat-base.sql"
                        )),
                        new UpgradeVersion("1.1.0", false, Arrays.asList(
                                "compat-scripts/upgrade/compat-110.sql"
                        ))
                );
            }

            @Override
            public String group() {
                return "compat_test";
            }

            @Override
            public int order() {
                return 1;
            }

            // rewriteSql 不覆盖：默认实现原样返回，不改写。
            // targetVersion() 不覆盖：升到 versions() 最高版本（此处即 1.1.0）。
        };
    }
}
