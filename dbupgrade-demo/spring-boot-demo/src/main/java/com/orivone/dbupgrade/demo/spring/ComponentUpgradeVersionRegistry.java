package com.orivone.dbupgrade.demo.spring;

import com.orivone.dbupgrade.core.model.UpgradeVersion;
import com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 【demo】第二种写法：直接实现 {@link UpgradeVersionRegistry} 并声明为组件。
 * <p>
 * 与 {@link DemoUpgradeVersionRegistry} 的差别有三处，都是有意的：
 * <ul>
 *     <li>写法：一个用 {@code @Bean} + 匿名类，一个用 {@code @Component} 实现类，语义完全对等</li>
 *     <li>位置：{@code order = 0}，先于业务分组执行，保证基础表先建</li>
 *     <li>脚本：指向 {@code upgrade-scripts/common}，一份脚本通吃所有数据库</li>
 * </ul>
 * 为什么这份不用按库分？因为它只用 CREATE TABLE / VARCHAR / INT / PRIMARY KEY
 * 这类各库都认的写法，没有自增、没有时间类型、没有引擎与字符集。
 * 换句话说，脚本要不要按库分，取决于脚本里写了什么，不取决于它是框架表还是业务表。
 * <p>
 * 分组 {@code framework} 与业务分组 {@code dbbase_demo} 互不干扰：
 * 同一个库里两条升级线各自记水位，版本号也不必对齐（这边走到 1.3.0，
 * 业务组走到 1.2.0，互不影响）。changeset 的唯一性只在分组内校验，
 * 所以换个分组复用同一个 {@code author:id} 也不会被误判成「已执行」。
 * <p>
 * 脚本放在 {@code common/system/} 而不是 {@code {库}/} 下，也是顺带说明
 * 「目录随便放」：框架只按注册表里的完整路径读取，不解析层级含义。
 * @author orivone
 */

// 实现类本身就是 Bean，无需 @Configuration；这里保留注释形式的原注解便于对比两种写法
//@Configuration(proxyBeanMethods = false)
// Bean 名带包路径只是为了避免与其他组的同名类冲突，值本身是容器中的 Bean 名
@Component("com.orivone.dbupgrade.demo.ComponentUpgradeVersionRegistry")
public final class ComponentUpgradeVersionRegistry implements UpgradeVersionRegistry {

    /**
     * 版本清单。注意 1.3.0 直接接在 1.0.0 之后：中间版本无需占位，
     * 框架只按版本号比较大小，不要求版本号连续。
     */
    @Override
    public List<UpgradeVersion> versions() {
        return Arrays.asList(
                // 首次安装基线：install = true，且必须是版本号最小的那个
                new UpgradeVersion("1.0.0", true, Arrays.asList(
                        "upgrade-scripts/common/system/install/system.sql"
                )),
                // 增量版本：一个版本可以包含多个文件，列表顺序即执行顺序
                new UpgradeVersion("1.3.0", false, Arrays.asList(
                        "upgrade-scripts/common/system/upgrade/system-130.sql"
                ))
        );
    }

    /**
     * 分组标识，框架自身的表结构用 "framework"，与业务分组隔离。
     */
    @Override
    public String group() {
        return "framework";
    }

    /**
     * 执行顺序 0，保证框架基础表先于业务表建立。
     */
    @Override
    public int order() {
        return 0;
    }

    // rewriteSql 不覆盖：默认实现原样返回入参，即「不做改写」。
}
