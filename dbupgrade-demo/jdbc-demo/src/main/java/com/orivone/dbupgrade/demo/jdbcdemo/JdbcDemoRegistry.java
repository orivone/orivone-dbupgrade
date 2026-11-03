package com.orivone.dbupgrade.demo.jdbcdemo;

import com.orivone.dbupgrade.core.model.UpgradeVersion;
import com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 各数据库精简 demo 共用的版本注册表，按数据库名拼出脚本路径与分组。
 * <p>
 * 两个版本足够说明升级机制：
 * <ol>
 *     <li>1.0.0（install = true）：建表 + 两行种子数据，只在库是空白时执行；</li>
 *     <li>1.1.0：加一列 + 回填，演示「版本推进」以及重复启动不会再执行。</li>
 * </ol>
 * 脚本按数据库分目录（{@code upgrade-scripts/{db}/}），因为 DDL 方言不同，
 * Oracle 的 {@code VARCHAR2} 不能拿去 MySQL 跑。
 * <p>
 * 分组串 {@code jdbc_{db}} 决定水位：同一个库里跑多个 demo 时互不影响。
 * @author orivone
 */
class JdbcDemoRegistry implements UpgradeVersionRegistry {

    private final String db;

    JdbcDemoRegistry(String db) {
        this.db = db;
    }

    @Override
    public List<UpgradeVersion> versions() {
        return Arrays.asList(
                new UpgradeVersion("1.0.0", true,
                        Collections.singletonList(script("install", "install-base"))),
                new UpgradeVersion("1.1.0", false,
                        Collections.singletonList(script("upgrade", "upgrade-customer")))
        );
    }

    @Override
    public String group() {
        return "jdbc_" + db;
    }

    @Override
    public int order() {
        return 10;
    }

    // rewriteSql / targetVersion 都不覆盖：
    // 前者默认原样返回（不改写），后者默认取 versions() 最高版本。

    /**
     * 示例：({@code mysql}, {@code upgrade}, {@code upgrade-customer})
     * → {@code upgrade-scripts/mysql/upgrade/mysql-upgrade-customer.sql}
     */
    private String script(String stage, String name) {
        return "upgrade-scripts/" + db + "/" + stage + "/" + db + "-" + name + ".sql";
    }
}
