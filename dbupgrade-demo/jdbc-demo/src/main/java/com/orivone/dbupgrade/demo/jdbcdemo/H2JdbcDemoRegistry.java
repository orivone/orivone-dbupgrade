package com.orivone.dbupgrade.demo.jdbcdemo;

import com.orivone.dbupgrade.core.model.UpgradeVersion;
import com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * H2 讲解版专用的注册表，比其余五个库多演示两件事。
 * <p>
 * 三个版本各承担一个观察点：
 * <ol>
 *     <li>1.0.0（install = true）：建表 + 种子数据，只在库是空白时执行；</li>
 *     <li>1.1.0：两个文件，声明顺序与文件名字母序相反 —— 顺序只看这里，与文件名无关；</li>
 *     <li>1.2.0：带 {@code sqlCheck} 前置条件的建索引，条件不满足时按 {@code MARK_RAN} 跳过。</li>
 * </ol>
 * 其余五个库共用 {@link JdbcDemoRegistry}，够说明方言差异即可，不必把演示点铺满。
 * @author orivone
 */
final class H2JdbcDemoRegistry implements UpgradeVersionRegistry {

    @Override
    public List<UpgradeVersion> versions() {
        return Arrays.asList(
                new UpgradeVersion("1.0.0", true,
                        Collections.singletonList("upgrade-scripts/h2/install/h2-install-base.sql")),
                // 按字母序 customer 在前，这里偏偏先声明 order：证明排序只认声明顺序
                new UpgradeVersion("1.1.0", false, Arrays.asList(
                        "upgrade-scripts/h2/upgrade/h2-upgrade-order.sql",
                        "upgrade-scripts/h2/upgrade/h2-upgrade-customer.sql")),
                new UpgradeVersion("1.2.0", false,
                        Collections.singletonList("upgrade-scripts/h2/upgrade/h2-upgrade-conditional.sql"))
        );
    }

    @Override
    public String group() {
        return "jdbc_h2";
    }

    @Override
    public int order() {
        return 10;
    }
}
