package com.orivone.dbupgrade.core.dialect;

import com.orivone.dbupgrade.core.StrUtils;

import com.orivone.dbupgrade.core.dialect.impl.*;
import com.orivone.dbupgrade.core.UpgradeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Optional;

/**
 * 方言解析器，负责把配置项或运行时元数据翻译成具体的 {@link UpgradeDialect}。
 * <p>
 * 优先级：
 * <ol>
 *     <li>orivone.dbupgrade.db-type 显式指定（推荐生产环境使用，避免探测失败）</li>
 *     <li>从 {@link DatabaseMetaData#getDatabaseProductName()} 自动探测</li>
 * </ol>
 * 探测不到时抛异常而不是猜默认值，避免在不支持的库上按 MySQL 语法执行、把失败拖到运行期。
 * @author orivone
 */
public final class DialectResolver {

    private static final Logger log = LoggerFactory.getLogger(DialectResolver.class);

    private DialectResolver() {
    }

    /**
     * 解析方言
     *
     * @param dataSource 目标数据源，用于探测数据库产品名
     * @param configured 配置的 db-type，为空或 auto 时走自动探测
     */
    public static UpgradeDialect resolve(DataSource dataSource, String configured) {
        if (configured != null && !StrUtils.isBlank(configured) && !"auto".equalsIgnoreCase(StrUtils.strip(configured))) {
            DbType type = DbType.parse(configured).orElseThrow(() -> new UpgradeException(
                    "配置项[orivone.dbupgrade.db-type]取值[" + configured + "]无法识别，"
                            + "可选值：auto/mysql/postgresql/oracle/dm/sqlserver/h2"));
            log.info("数据库升级方言由配置指定：{}", type.description());
            return of(type);
        }
        DbType detected = detect(dataSource);
        if (detected == DbType.OTHER) {
            throw new UpgradeException("无法自动识别数据库类型，请显式配置 orivone.dbupgrade.db-type"
                    + "（auto/mysql/postgresql/oracle/dm/sqlserver/h2）");
        }
        log.info("数据库升级方言自动探测结果：{}", detected.description());
        return of(detected);
    }

    /**
     * 按类型创建方言实例
     */
    public static UpgradeDialect of(DbType type) {
        switch (type) {
            case MYSQL:
                return new MysqlDialect();
            case POSTGRESQL:
                return new PostgreSqlDialect();
            case ORACLE:
                return new OracleDialect(DbType.ORACLE);
            case DM:
                return new OracleDialect(DbType.DM);
            case SQLSERVER:
                return new SqlServerDialect();
            case H2:
                return new H2Dialect();
            case OTHER:
            default:
                throw new UpgradeException("不支持的数据库类型：" + type);
        }
    }

    /**
     * 从数据源探测数据库产品名
     */
    public static DbType detect(DataSource dataSource) {
        if (dataSource == null) {
            return DbType.OTHER;
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData == null ? null : metaData.getDatabaseProductName();
            DbType type = DbType.fromProductName(productName);
            log.debug("数据库产品名[{}] -> {}", productName, type);
            return type;
        } catch (Exception e) {
            log.warn("探测数据库类型失败，将要求显式配置 orivone.dbupgrade.db-type：{}", e.getMessage());
            return DbType.OTHER;
        }
    }

    /**
     * 解析配置的方言类型，供外部校验使用
     */
    public static Optional<DbType> parseType(String configured) {
        return DbType.parse(configured);
    }
}
