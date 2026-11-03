package com.orivone.dbupgrade.core.dialect;

import com.orivone.dbupgrade.core.StrUtils;

import java.util.Locale;
import java.util.Optional;

/**
 * 升级模块支持的数据库类型。
 * <p>
 * 识别来源有两个，优先级从高到低：
 * <ol>
 *     <li>配置项 orivone.dbupgrade.db-type 显式指定</li>
 *     <li>运行时从 {@link java.sql.DatabaseMetaData#getDatabaseProductName()} 自动探测</li>
 * </ol>
 * 新增一种数据库时需要同时补充：本枚举常量、{@link UpgradeDialect} 实现、{@link DialectResolver} 分支。
 * @author orivone
 */
public enum DbType {

    /**
     * MySQL 与 MariaDB（语法一致，MariaDB 归入此类）
     */
    MYSQL("MySQL"),

    /**
     * PostgreSQL 及其国产衍生库：人大金仓 KingbaseES、openGauss、华为 GaussDB、瀚高 HighGo、Vastbase
     */
    POSTGRESQL("PostgreSQL"),

    /**
     * Oracle
     */
    ORACLE("Oracle"),

    /**
     * 达梦 DM（语法高度兼容 Oracle，复用 Oracle 方言实现）
     */
    DM("达梦 DM"),

    /**
     * Microsoft SQL Server
     */
    SQLSERVER("SQL Server"),

    /**
     * H2（主要用于单元测试与本地验证）
     */
    H2("H2"),

    /**
     * 未识别的数据库，此时必须显式配置 db-type，否则启动抛异常
     */
    OTHER("未识别");

    private final String description;

    DbType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /**
     * 是否 MySQL 语法族（决定 # 行注释、反引号、ENGINE 等特性是否可用）
     */
    public boolean isMysqlFamily() {
        return this == MYSQL;
    }

    /**
     * 解析配置项文本，不识别返回空
     */
    public static Optional<DbType> parse(String text) {
        if (text == null || StrUtils.isBlank(text)) {
            return Optional.empty();
        }
        String value = StrUtils.strip(text).toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        switch (value) {
            case "mysql":
            case "mariadb":
            case "maria":
                return Optional.of(MYSQL);
            case "postgresql":
            case "postgres":
            case "pg":
            case "pgsql":
            case "kingbase":
            case "kingbasees":
            case "opengauss":
            case "gaussdb":
            case "highgo":
            case "vastbase":
                return Optional.of(POSTGRESQL);
            case "oracle":
            case "orcl":
                return Optional.of(ORACLE);
            case "dm":
            case "dm8":
            case "dameng":
            case "dmdbms":
                return Optional.of(DM);
            case "sqlserver":
            case "mssql":
            case "microsoftsqlserver":
                return Optional.of(SQLSERVER);
            case "h2":
                return Optional.of(H2);
            case "other":
            case "unknown":
                return Optional.of(OTHER);
            default:
                return Optional.empty();
        }
    }

    /**
     * 根据数据库产品名推断类型
     *
     * @param productName DatabaseMetaData#getDatabaseProductName() 的返回值
     */
    public static DbType fromProductName(String productName) {
        if (productName == null || StrUtils.isBlank(productName)) {
            return OTHER;
        }
        String name = StrUtils.strip(productName).toLowerCase(Locale.ROOT);
        if (name.contains("mysql") || name.contains("mariadb")) {
            return MYSQL;
        }
        if (name.contains("postgres") || name.contains("kingbase") || name.contains("opengauss")
                || name.contains("gauss") || name.contains("highgo") || name.contains("vastbase")) {
            return POSTGRESQL;
        }
        if (name.contains("oracle")) {
            return ORACLE;
        }
        // 达梦的产品名形如 "DM DBMS"
        if (name.startsWith("dm ") || name.equals("dm") || name.contains("dameng")) {
            return DM;
        }
        if (name.contains("sql server") || name.contains("mssql")) {
            return SQLSERVER;
        }
        if (name.equals("h2") || name.startsWith("h2 ")) {
            return H2;
        }
        return OTHER;
    }
}
