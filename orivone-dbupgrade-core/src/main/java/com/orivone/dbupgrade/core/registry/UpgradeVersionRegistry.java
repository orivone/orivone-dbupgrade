package com.orivone.dbupgrade.core.registry;

import com.orivone.dbupgrade.core.model.UpgradeVersion;

import java.util.List;

/**
 * 版本注册表，一个实现类 = 一条独立的升级线。
 * <p>
 * 接入时要手写的 Java 代码只有这里：有哪些版本、谁先谁后、每个版本包含哪些脚本、文件在版本内的执行顺序、
 * 首次安装的基线是哪一版，全部写在这个接口里。SQL 文件名不承担任何语义，不解析版本号，也不参与排序。
 * <p>
 * 一个应用可以声明多个注册表，隔离维度是 {@link #group()}：水位各推各的，changeset 记录各记各的，
 * 因此两个模块都用 {@code orivone:init} 也不会互相误判成「已执行」。执行顺序由 {@link #order()} 决定。
 * <p>
 * 框架不提供默认注册表。一个都没声明时只打一行日志就结束——宁可什么都不做，也不凭空执行没人声明过的 SQL。
 * @author orivone
 */
public interface UpgradeVersionRegistry {

    /**
     * 返回本分组下的所有版本定义。
     * <p>
     * 实现方可按任意顺序返回，框架会做格式校验、版本号唯一性校验、install 基线校验、
     * 文件归属唯一性校验，并按版本号升序排序后再执行。
     *
     * @return 版本定义列表，不允许返回 null
     */
    List<UpgradeVersion> versions();

    /**
     * 本次升到哪一版，可选，默认升到 {@link #versions()} 里的最高版本。
     * <p>
     * 绝大多数场景不用管它：版本清单在哪，目标就在哪，不会出现「代码里加了版本、配置里忘了放开」。
     * 需要把某个环境扣在中间某一版时才覆盖，典型用途是灰度发布、分批上线，或者某个版本还在验证、先压住不放。
     * <p>
     * 返回非空时会被严格校验，任何一条不满足都直接抛 {@code UpgradeException} 阻断启动：版本号得合法、
     * 必须出现在 {@link #versions()} 的清单里（否则日志写 1.0.5、水位停在 1.0.0，排查时根本看不出是笔误）、
     * 且不得低于数据库当前版本。低于最高版本是允许的，剩下的版本留到下次发布再升。
     * <p>
     * 每个分组升级时各调一次，返回值要稳定——别在这里读一个随时可能变的远程开关，
     * 同一进程内两次结果不同会让日志失真。
     *
     * @return 目标版本号；null 或空白表示取 {@link #versions()} 的最高版本
     */
    default String targetVersion() {
        return null;
    }

    /**
     * 分组标识，写进两张记录表的 {@code group_code}，也是水位与执行记录的隔离边界。
     * <p>
     * 同一个库里必须唯一（版本表对 group_code 建了唯一键）。用模块名或应用名即可，例如
     * {@code framework}、{@code crm}。
     * <p>
     * <b>上线之后不要改</b>：改名等于换了一套全新水位，历史 changeset 会被重跑一遍。
     *
     * @return 分组标识，不允许返回 null 或空串
     */
    String group();

    /**
     * 多个注册表之间的执行顺序，数值小的先执行。
     * <p>
     * 典型用法：框架自身的表结构用 0，业务模块依次 10、20，保证基础表先于业务表建立。
     * 注意这只是一个建议顺序，真正决定成败的仍是各自脚本的依赖关系。
     */
    int order();

    /**
     * 逐条 SQL 的改写钩子，默认原样返回，也就是不改写。
     * <p>
     * 只有全局表前缀、多租户 schema 替换这类「解析阶段拿不到」的改写才需要覆盖它，其余情况别管。
     * 框架在语句拆分完、DDL 存在性判断之前调用：返回什么就执行什么，返回 null 或空白等同于不改写，
     * 调用点会兜住，不会 NPE。
     * <p>
     * 改写结果不计入 checksum。改写通常绑着运行环境，一旦算进去，换套前缀、换个 schema 就会让
     * 全部历史 changeset 被判成「内容变了」，而那批 SQL 其实一条都不用重跑。
     *
     * @param sql 拆分后的单条原始语句，不会为 null
     * @return 最终要执行的 SQL；null 或空白表示沿用原语句
     */
    default String rewriteSql(String sql) {
        return sql;
    }

    // 升级前后、changeset 前后、单条语句前后的拦截回调，是下一步要做的事。
    // 现在改语句内容有 rewriteSql，但「在升级过程中插一段自己的代码」还缺一个正式落点；
    // 回调时机、返回值语义与失败处理已经定好，见 README 的「扩展与自定义」一章。
}
