package com.orivone.dbupgrade.core.model;

import com.orivone.dbupgrade.core.StrUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 解析出来的一个 changeset，不可变，靠 {@link Builder} 组装。
 * <p>
 * Java 8 没有 record，用 final 字段加读取方法代替；方法名照 record 的风格写
 * （{@code changesetId()}、{@code sql()}），读起来顺，将来换 record 调用方也不用改。
 * @author orivone
 */
public final class Changeset {

    private final String changesetId;

    private final String filePath;

    private final int line;

    private final boolean runAlways;

    private final boolean runOnChange;

    private final String endDelimiter;

    private final boolean splitStatements;

    private final String sql;

    private final String checksum;

    private final List<Precondition> preconditions;

    private final List<String> rollbackSqls;

    /**
     * @param changesetId     changeset 唯一标识，格式 author:id
     * @param filePath        所属文件（classpath 路径）
     * @param line            在文件中的起始行号，便于日志定位
     * @param runAlways       每次升级都重新执行（注意：已生效的 CREATE/ADD COLUMN/CREATE INDEX
     *                        仍会被存在性判断跳过，所以要“每次必跑”请写 INSERT/UPDATE 这类 DML）
     * @param runOnChange     内容变化时才重跑
     * @param endDelimiter    语句分隔符，默认 ;
     * @param splitStatements 是否按分隔符拆分语句，默认 true
     * @param sql             changeset 正文 SQL（已剔除指令行）
     * @param checksum        正文 SQL 的 SHA-256，用于检测静默修改
     * @param preconditions   前置条件
     * @param rollbackSqls    反向 SQL，当前版本仅收集不执行
     */
    public Changeset(String changesetId, String filePath, int line, boolean runAlways, boolean runOnChange,
                         String endDelimiter, boolean splitStatements, String sql, String checksum,
                         List<Precondition> preconditions, List<String> rollbackSqls) {
        this.changesetId = changesetId;
        this.filePath = filePath;
        this.line = line;
        this.runAlways = runAlways;
        this.runOnChange = runOnChange;
        this.endDelimiter = (endDelimiter == null || endDelimiter.isEmpty()) ? ";" : endDelimiter;
        this.splitStatements = splitStatements;
        this.sql = sql == null ? "" : sql;
        this.checksum = checksum;
        this.preconditions = preconditions == null || preconditions.isEmpty()
                ? Collections.<Precondition>emptyList()
                : Collections.unmodifiableList(new ArrayList<Precondition>(preconditions));
        this.rollbackSqls = rollbackSqls == null || rollbackSqls.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(rollbackSqls));
    }

    public String changesetId() {
        return changesetId;
    }

    public String filePath() {
        return filePath;
    }

    public int line() {
        return line;
    }

    public boolean runAlways() {
        return runAlways;
    }

    public boolean runOnChange() {
        return runOnChange;
    }

    public String endDelimiter() {
        return endDelimiter;
    }

    public boolean splitStatements() {
        return splitStatements;
    }

    public String sql() {
        return sql;
    }

    public String checksum() {
        return checksum;
    }

    public List<Precondition> preconditions() {
        return preconditions;
    }

    public List<String> rollbackSqls() {
        return rollbackSqls;
    }

    /**
     * 正文是否为空（无有效 SQL）
     */
    public boolean isEmpty() {
        return StrUtils.isBlank(sql);
    }

    /**
     * 日志摘要，避免大 SQL 刷屏
     */
    public String summary() {
        String flat = sql.replaceAll("\\s+", " ").trim();
        if (flat.length() > 160) {
            flat = flat.substring(0, 160) + "...";
        }
        return changesetId + " -> " + flat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Changeset)) {
            return false;
        }
        Changeset other = (Changeset) o;
        return line == other.line
                && runAlways == other.runAlways
                && runOnChange == other.runOnChange
                && splitStatements == other.splitStatements
                && equalsNullable(changesetId, other.changesetId)
                && equalsNullable(filePath, other.filePath)
                && endDelimiter.equals(other.endDelimiter)
                && sql.equals(other.sql)
                && equalsNullable(checksum, other.checksum)
                && preconditions.equals(other.preconditions)
                && rollbackSqls.equals(other.rollbackSqls);
    }

    @Override
    public int hashCode() {
        int result = changesetId == null ? 0 : changesetId.hashCode();
        result = 31 * result + (filePath == null ? 0 : filePath.hashCode());
        result = 31 * result + line;
        result = 31 * result + (runAlways ? 1 : 0);
        result = 31 * result + (runOnChange ? 1 : 0);
        result = 31 * result + endDelimiter.hashCode();
        result = 31 * result + (splitStatements ? 1 : 0);
        result = 31 * result + sql.hashCode();
        result = 31 * result + (checksum == null ? 0 : checksum.hashCode());
        result = 31 * result + preconditions.hashCode();
        result = 31 * result + rollbackSqls.hashCode();
        return result;
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    @Override
    public String toString() {
        return "Changeset{changesetId='" + changesetId + "', filePath='" + filePath + "', line=" + line
                + ", runAlways=" + runAlways + ", runOnChange=" + runOnChange + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * changeset 构建器，仅解析阶段使用
     */
    public static final class Builder {

        private String changesetId;

        private String filePath;

        private int line;

        private boolean runAlways;

        private boolean runOnChange;

        private String endDelimiter = ";";

        private boolean splitStatements = true;

        private final StringBuilder sql = new StringBuilder();

        private String checksum;

        private final List<Precondition> preconditions = new ArrayList<Precondition>();

        private final List<String> rollbackSqls = new ArrayList<String>();

        public Builder changesetId(String changesetId) {
            this.changesetId = changesetId;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder line(int line) {
            this.line = line;
            return this;
        }

        public Builder runAlways(boolean runAlways) {
            this.runAlways = runAlways;
            return this;
        }

        public Builder runOnChange(boolean runOnChange) {
            this.runOnChange = runOnChange;
            return this;
        }

        public Builder endDelimiter(String endDelimiter) {
            if (endDelimiter != null && !endDelimiter.isEmpty()) {
                this.endDelimiter = endDelimiter;
            }
            return this;
        }

        public Builder splitStatements(boolean splitStatements) {
            this.splitStatements = splitStatements;
            return this;
        }

        public Builder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder appendLine(String textLine) {
            sql.append(textLine).append('\n');
            return this;
        }

        public Builder addPrecondition(Precondition precondition) {
            if (precondition != null) {
                preconditions.add(precondition);
            }
            return this;
        }

        public Builder addRollbackSql(String rollbackSql) {
            if (!StrUtils.isBlank(rollbackSql)) {
                rollbackSqls.add(StrUtils.strip(rollbackSql));
            }
            return this;
        }

        public String changesetId() {
            return changesetId;
        }

        public String rawSql() {
            return sql.toString();
        }

        public Changeset build() {
            return new Changeset(changesetId, filePath, line, runAlways, runOnChange,
                    endDelimiter, splitStatements, StrUtils.strip(sql.toString()), checksum,
                    preconditions, rollbackSqls);
        }
    }
}
