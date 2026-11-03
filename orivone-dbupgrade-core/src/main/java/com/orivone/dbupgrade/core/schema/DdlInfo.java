package com.orivone.dbupgrade.core.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单条 DDL 语句的解析结果，用于执行前的“是否已存在”判断
 * @author orivone
 */
public final class DdlInfo {

    private final DdlType type;

    private final String table;

    private final List<String> columns;

    private final String indexName;

    public DdlInfo(DdlType type, String table, List<String> columns, String indexName) {
        this.type = type;
        this.table = table;
        this.columns = columns == null || columns.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(columns));
        this.indexName = indexName;
    }

    /**
     * 未识别的语句
     */
    public static DdlInfo other() {
        return new DdlInfo(DdlType.OTHER, null, Collections.<String>emptyList(), null);
    }

    /**
     * 语句类型
     */
    public DdlType type() {
        return type;
    }

    /**
     * 目标表
     */
    public String table() {
        return table;
    }

    /**
     * 新增列名列表，仅 ADD_COLUMN 类型有值
     */
    public List<String> columns() {
        return columns;
    }

    /**
     * 索引名，仅 CREATE_INDEX 类型有值
     */
    public String indexName() {
        return indexName;
    }

    @Override
    public String toString() {
        return "DdlInfo{type=" + type + ", table='" + table + "', columns=" + columns
                + ", indexName='" + indexName + "'}";
    }
}
