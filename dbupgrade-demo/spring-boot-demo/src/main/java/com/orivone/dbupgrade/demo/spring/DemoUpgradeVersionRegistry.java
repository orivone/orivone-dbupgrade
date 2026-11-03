package com.orivone.dbupgrade.demo.spring;

import com.orivone.dbupgrade.core.model.UpgradeVersion;
import com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * 【demo】版本注册表的标准写法，业务方照此实现即可。
 * <p>
 * 这是接入升级能力唯一必须手写的代码。一个注册表 = 一条独立的升级线，
 * 用 {@link UpgradeVersionRegistry#group()} 区分，用 {@link UpgradeVersionRegistry#order()} 排序。
 * <p>
 * 脚本目录来自配置项 {@code demo.upgrade.script-dir}：每个数据库 profile 指向自己那一份，
 * 因为 DDL 方言不同，同一段建表语句没法跨库通用。注册表本身一份就够，换库只换配置。
 * <p>
 * 示例覆盖以下几点：
 * <ol>
 *     <li>1.1.0 的两个文件，注册表顺序是 {@code order} 先、{@code customer} 后，
 *     与文件名字母序相反 —— 证明文件名不参与排序，顺序完全由 filePaths 列表决定</li>
 *     <li>1.0.0 标记 install = true 且是版本号最小的那个 —— 首次安装基线</li>
 *     <li>1.2.0 的两个文件跨了目录：一个在 {@code upgrade/}，一个在 {@code hotfix/} ——
 *     目录名不参与任何逻辑，只要能拼出完整路径即可</li>
 *     <li>声明的每个 SQL 文件必须真实存在于 classpath，否则启动时校验即抛异常</li>
 *     <li>版本号只要求可比较，不要求连续；install 标志与目录名无关</li>
 * </ol>
 * 注意：这里用匿名类是为了让 demo 在一个文件里讲清楚，真实项目直接实现接口或写 lambda 均可。
 * <p>
 * 代码只用 Java 8 语法：{@code Arrays.asList} 而不是 {@code List.of}（后者是 Java 9+）。
 * 本文件要随 {@code -Pboot2} profile 一起用 release 8 编译，
 * 这样 Boot 2.7 + JDK 8 的用户可以把这份代码原样抄走。
 * @author orivone
 */
// 显式指定 Bean 名：@Configuration 类本身也是一个 Bean，默认名是类名首字母小写，
// 与下面的 @Bean 方法同名会触发 BeanDefinitionOverrideException（Boot 2.1+ 默认禁止覆盖）。
@Configuration(value = "demoUpgradeVersionRegistryService", proxyBeanMethods = false)
public class DemoUpgradeVersionRegistry {

    /**
     * 当前 profile 对应的脚本根目录，形如 {@code upgrade-scripts/mysql}。
     * <p>
     * 默认值兜底到 h2，这样即使不激活任何 profile 也不会因为缺配置而启动失败。
     */
    private final String scriptDir;

    /**
     * 脚本目录的最后一段，demo 里恰好就是数据库名（h2 / mysql / postgresql …）。
     * <p>
     * 真实项目里直接写成字符串常量即可。demo 这么写是为了用一份注册表跑六种库，
     * 省掉六个重复的注册表类。
     */
    private final String db;

    public DemoUpgradeVersionRegistry(
            @Value("${demo.upgrade.script-dir:upgrade-scripts/h2}") String scriptDir) {
        // 允许写成 upgrade-scripts/mysql/ 这种带尾斜杠的形式，拼路径前统一去掉
        String trimmed = scriptDir.endsWith("/")
                ? scriptDir.substring(0, scriptDir.length() - 1)
                : scriptDir;
        this.scriptDir = trimmed;
        int slash = trimmed.lastIndexOf('/');
        this.db = slash < 0 ? trimmed : trimmed.substring(slash + 1);
    }

    /**
     * 声明 demo 这一组升级版本。
     * <p>
     * 日常发版只需追加一个 {@link UpgradeVersion}：注册表就是版本的唯一声明处，
     * 框架会升到其中最高的版本，不需要再去改任何配置。
     */
    @Bean("demoUpgradeVersionRegistry")
    public UpgradeVersionRegistry demoUpgradeVersionRegistry() {

        final String base = scriptDir;
        final String name = db;

        return new UpgradeVersionRegistry() {
            /**
             * 版本清单。可返回任意顺序，框架会校验格式与唯一性后按版本号升序排序。
             */
            @Override
            public List<UpgradeVersion> versions() {
                return Arrays.asList(
                        // 首次安装基线：install = true，且必须是版本号最小的那个。
                        // 这里的 install/ 只是目录名，与 install 标志没有必然关系 ——
                        // 把文件挪到 hotfix/ 下，只要路径跟着改，语义完全一样。
                        new UpgradeVersion("1.0.0", true, Arrays.asList(
                                base + "/install/" + name + "-install-base.sql"
                        )),
                        // 增量版本：一个版本可以包含多个文件，列表顺序即执行顺序。
                        // order 在前、customer 在后，与文件名字母序正好相反。
                        new UpgradeVersion("1.1.0", false, Arrays.asList(
                                base + "/upgrade/" + name + "-upgrade-order.sql",
                                base + "/upgrade/" + name + "-upgrade-customer.sql"
                        )),
                        // 增量版本：演示 runOnChange、四类前置条件、自定义分隔符，
                        // 以及「同一个版本的文件可以分散在不同目录」
                        new UpgradeVersion("1.2.0", false, Arrays.asList(
                                base + "/upgrade/" + name + "-upgrade-advanced.sql",
                                base + "/hotfix/" + name + "-hotfix.sql"
                        ))
                );
            }

            /**
             * 分组标识。写入两张记录表的 group_code，决定版本水位与 changeset 记录的隔离边界。
             * <p>
             * 一旦上线不可改名：改名等于换一套全新水位，历史 changeset 会被重跑一遍。
             */
            @Override
            public String group() {
                return "dbbase_demo";
            }

            /**
             * 多个注册表之间的执行顺序，数值小的先执行。demo 排在框架自身（0）之后。
             */
            @Override
            public int order() {
                return 2;
            }

            // rewriteSql 不覆盖：默认实现原样返回入参，即「不做改写」。
            // 需要改写表名前缀时才覆盖，例如：
            //   @Override
            //   public String rewriteSql(String sql) {
            //       return sql.replace("demo_customer", tenant + "_demo_customer");
            //   }

            // 目标版本：不覆盖 targetVersion() 即为「升到 versions() 里的最高版本」。
            // 需要灰度时再覆盖，例如只放行到 1.1.0：
            //   @Override
            //   public String targetVersion() {
            //       return "1.1.0";
            //   }
            // 目标版本必须落在 versions() 已声明的版本里，否则启动直接失败。
        };
    }
}
