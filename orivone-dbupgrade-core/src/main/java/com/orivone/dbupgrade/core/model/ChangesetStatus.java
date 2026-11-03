package com.orivone.dbupgrade.core.model;

/**
 * changeset 执行状态，与 dbupgrade_record.status 列一一对应。
 * <p>
 * 做成枚举而不是若干个 String 常量，是因为这组值要同时满足三个约束，
 * 用 String 只能靠人记住：
 * <ol>
 *     <li>只能取这三个值 —— 枚举之外写不出第四个状态；</li>
 *     <li>要能直接拼进 SQL 当参数 —— 由 {@link #code()} 给出字面量；</li>
 *     <li>要从库里的字符串还原回枚举 —— 由 {@link #ofCode(String)} 承担，
 *         遇到未知值返回 {@link #FAILED} 而不是抛异常：一行脏数据不该让整个应用起不来。</li>
 * </ol>
 * @author orivone
 */
public enum ChangesetStatus {

    /**
     * 执行中，进程中断后会残留该状态，启动时统一恢复为 FAILED
     */
    EXECUTING,

    /**
     * 执行成功
     */
    SUCCESS,

    /**
     * 执行失败
     */
    FAILED;

    /**
     * 写库用的字面量。
     * <p>
     * 目前等于 {@link #name()}，但仍然显式提供：将来枚举常量想改名
     * （比如 EXECUTING 改成 RUNNING）时，只要这里不动，库里的存量数据就不受影响。
     */
    public String code() {
        return name();
    }

    /**
     * 从库里读到的字符串还原成枚举，空值或未知值一律按 {@link #FAILED} 处理。
     * <p>
     * 未知值按失败处理是保守选择：把不认识的状态当成功，等于允许重跑被跳过；
     * 当失败则最多多跑一次，而重跑本身是幂等的。
     */
    public static ChangesetStatus ofCode(String code) {
        if (code != null) {
            for (ChangesetStatus status : values()) {
                if (status.name().equalsIgnoreCase(code.trim())) {
                    return status;
                }
            }
        }
        return FAILED;
    }
}
