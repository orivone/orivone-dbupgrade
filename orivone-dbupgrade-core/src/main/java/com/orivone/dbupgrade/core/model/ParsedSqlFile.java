package com.orivone.dbupgrade.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个 SQL 文件的解析结果
 * @author orivone
 */
public final class ParsedSqlFile {

    private final String filePath;

    private final List<Changeset> changesets;

    public ParsedSqlFile(String filePath, List<Changeset> changesets) {
        this.filePath = filePath;
        this.changesets = changesets == null || changesets.isEmpty()
                ? Collections.<Changeset>emptyList()
                : Collections.unmodifiableList(new ArrayList<Changeset>(changesets));
    }

    /**
     * 文件路径
     */
    public String filePath() {
        return filePath;
    }

    /**
     * 文件内 changeset 列表，顺序即文件中出现顺序
     */
    public List<Changeset> changesets() {
        return changesets;
    }

    public boolean isEmpty() {
        return changesets.isEmpty();
    }

    @Override
    public String toString() {
        return "ParsedSqlFile{filePath='" + filePath + "', changesets=" + changesets.size() + "}";
    }
}
