package com.orivone.dbupgrade.core.exec;

import com.orivone.dbupgrade.core.model.ScriptType;

import java.util.List;

/**
 * 脚本执行器，决定一段 changeset 里的语句怎么跑。
 * <p>
 * 内置实现是 {@link SqlScriptExecutor}。想跑 SQL 以外的东西——Shell、HTTP、一段数据修复代码——
 * 自己实现这个接口就行：Boot 环境下声明成 Bean 会自动顶掉默认实现，纯 JDBC 环境从
 * {@code UpgradeEngine} 的构造器传进去。执行链路只管「什么时候跑」，不关心「跑的是什么」。
 * <p>
 * 粒度是 changeset：{@link #executeAll(List, String, String)} 一次拿到该 changeset 的全部语句，
 * 默认逐条转调 {@link #execute(String, String, String)}，实现类可以改成「一个连接跑完整批」——
 * 这样 {@code SET @x}、{@code SET FOREIGN_KEY_CHECKS=0}、临时表这类会话级语句才能对后续语句生效。
 * @author orivone
 */
public interface ScriptExecutor {

    /**
     * 执行单条语句
     *
     * @param statement   单条语句（已由拆分器拆分）
     * @param version     所属版本号，用于异常信息
     * @param changesetId 所属 changesetId，用于异常信息
     */
    void execute(String statement, String version, String changesetId);

    /**
     * 执行一个 changeset 内的全部语句，顺序与传入顺序一致。
     * <p>
     * 默认实现逐条转调 {@link #execute(String, String, String)}，语义是「每条语句独立连接」；
     * 需要让会话状态在 changeset 内延续的实现（例如 SQL 执行器）应覆盖本方法。
     * <p>
     * 失败约定：任意一条语句失败立即中断，后续语句不再执行，异常向上抛出由调用方记录失败状态。
     *
     * @param statements  该 changeset 内待执行的语句，已按脚本顺序排列
     * @param version     所属版本号，用于异常信息
     * @param changesetId 所属 changesetId，用于异常信息
     */
    default void executeAll(List<String> statements, String version, String changesetId) {
        if (statements == null || statements.isEmpty()) {
            return;
        }
        for (String statement : statements) {
            execute(statement, version, changesetId);
        }
    }

    /**
     * 支持的脚本类型
     */
    default ScriptType scriptType() {
        return ScriptType.UPGRADE;
    }
}
