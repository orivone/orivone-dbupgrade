package com.orivone.dbupgrade.core.schema;

/**
 * DDL 语句类型，仅覆盖可安全做存在性判断的加法类 DDL
 * @author orivone
 */
public enum DdlType {

    /**
     * 建表：表已存在则跳过
     */
    CREATE_TABLE,

    /**
     * 加列：所有目标列均已存在则跳过
     */
    ADD_COLUMN,

    /**
     * 建索引：索引已存在则跳过
     */
    CREATE_INDEX,

    /**
     * 其它语句（DROP / RENAME / MODIFY / DML 等），不做自动判断，原样执行
     */
    OTHER
}
