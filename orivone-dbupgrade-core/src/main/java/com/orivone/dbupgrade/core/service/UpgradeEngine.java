package com.orivone.dbupgrade.core.service;

import com.orivone.dbupgrade.core.StrUtils;
import com.orivone.dbupgrade.core.UpgradeException;
import com.orivone.dbupgrade.core.VersionUtils;
import com.orivone.dbupgrade.core.config.UpgradeSettings;
import com.orivone.dbupgrade.core.dialect.UpgradeDialect;
import com.orivone.dbupgrade.core.dialect.UpgradeContext;
import com.orivone.dbupgrade.core.exec.ScriptExecutor;
import com.orivone.dbupgrade.core.exec.SqlScriptExecutor;
import com.orivone.dbupgrade.core.model.Changeset;
import com.orivone.dbupgrade.core.model.VersionedChangeset;
import com.orivone.dbupgrade.core.model.ChangesetStatus;
import com.orivone.dbupgrade.core.model.ParsedSqlFile;
import com.orivone.dbupgrade.core.model.Precondition;
import com.orivone.dbupgrade.core.model.UpgradeResult;
import com.orivone.dbupgrade.core.model.UpgradeVersion;
import com.orivone.dbupgrade.core.parser.FormattedSqlParser;
import com.orivone.dbupgrade.core.parser.ScriptResourceLoader;
import com.orivone.dbupgrade.core.parser.SqlStatementSplitter;
import com.orivone.dbupgrade.core.store.UpgradeRecord;
import com.orivone.dbupgrade.core.store.UpgradeRecordRepository;
import com.orivone.dbupgrade.core.store.UpgradeVersionRecord;
import com.orivone.dbupgrade.core.registry.UpgradeVersionRegistry;
import com.orivone.dbupgrade.core.registry.UpgradePlanBuilder;
import com.orivone.dbupgrade.core.schema.SchemaMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 升级主流程，一个实例服务一个分组。
 * <p>
 * 一次升级的顺序是固定的：加锁 → 建表 / 收拾上次中断的残局 → 读当前水位 → 算出要跑哪些版本
 * → 按注册表声明的文件顺序、文件内的 changeset 顺序执行 → 版本内全部成功才推水位 → 放锁。
 * <p>
 * 水位按版本整体推进：一个版本里只要有一个 changeset 失败，水位就停在上一版；下次重试靠执行记录
 * 与 checksum 跳过已完成的部分，不会从头再来。
 * <p>
 * 加锁有两种用法，用哪种由调用方决定，本类不自动选择：{@link #upgrade()} 自己加锁、执行、放锁，
 * 适合按分组加锁；{@link #upgradeWithLockHeld()} 假定调用方已经持锁，适合全局单例执行——
 * 此时锁必须覆盖整批注册表，否则组间放锁的空窗会让别的节点插进来，本节点后续分组就抢不到锁、静默漏执行。
 * @author orivone
 */

public class UpgradeEngine {

    private static final Logger log = LoggerFactory.getLogger(UpgradeEngine.class);

    private final UpgradeVersionRegistry registry;

    private final UpgradePlanBuilder validator;

    private final UpgradeSettings properties;

    private final UpgradeRecordRepository repository;

    private final FormattedSqlParser parser;

    private final ScriptResourceLoader scriptResourceLoader;

    private final ScriptExecutor scriptExecutor;

    private final SchemaMetadata schemaMetadata;

    private final UpgradeDialect dialect;

    private final UpgradeLockManager lockManager;

    /**
     * 脚本解析结果缓存，同一文件在一次升级中可能被多次访问（同版本补跑等场景）
     */
    private final Map<String, ParsedSqlFile> parsedFileCache = new ConcurrentHashMap<String, ParsedSqlFile>();

    /**
     * 执行人标识缓存，避免每个 changeset 都解析一次主机名
     */
    private volatile String cachedOperator;

    /**
     * 组装一个分组的升级服务（脚本加载器按默认 classpath 规则创建）。
     *
     * @param registry           本分组的版本注册表
     * @param properties         运行期配置快照
     * @param upgradeJdbcContext 连接来源/方言/元数据的统一上下文
     * @param lockManager        升级锁
     */
    public UpgradeEngine(UpgradeVersionRegistry registry,
                               UpgradeSettings properties,
                               UpgradeContext upgradeJdbcContext,
                               UpgradeLockManager lockManager) {
        this(registry, properties, new ScriptResourceLoader(), upgradeJdbcContext, lockManager);
    }

    /**
     * 组装一个分组的升级服务。
     * <p>
     * 记录表读写、方言、元数据检查器、脚本执行器全部从同一个 {@link UpgradeContext} 派生，
     * 这样「写版本记录」和「执行 DDL」一定落在同一个库上，多数据源不会串。
     * <p>
     * 解析器、校验器是无状态工具，内部直接 new，不占用 Bean 位置。
     *
     * @param registry            本分组的版本注册表
     * @param properties          运行期配置快照
     * @param scriptResourceLoader 脚本加载器（classpath / file / http）
     * @param upgradeJdbcContext  连接来源、方言与元数据的统一上下文
     * @param lockManager         升级锁
     */
    public UpgradeEngine(UpgradeVersionRegistry registry,
                               UpgradeSettings properties,
                               ScriptResourceLoader scriptResourceLoader,
                               UpgradeContext upgradeJdbcContext,
                               UpgradeLockManager lockManager) {
        this(registry, properties, scriptResourceLoader, upgradeJdbcContext, lockManager, null);
    }

    /**
     * 同上，另外允许自带脚本执行器。
     * <p>
     * 传 null 就用内置的 SQL 执行器。想跑 SQL 以外的东西——Shell、HTTP 调用、一段数据修复代码——
     * 实现 {@link ScriptExecutor} 从这里传进来就行，主流程不关心执行的到底是哪种脚本。
     *
     * @param scriptExecutor 自定义执行器，null 表示使用内置 SQL 执行器
     */
    public UpgradeEngine(UpgradeVersionRegistry registry,
                               UpgradeSettings properties,
                               ScriptResourceLoader scriptResourceLoader,
                               UpgradeContext upgradeJdbcContext,
                               UpgradeLockManager lockManager,
                               ScriptExecutor scriptExecutor) {
        if (registry == null) {
            throw new UpgradeException("版本注册表不能为 null");
        }
        if (StrUtils.isBlank(registry.group())) {
            // group() 是水位与执行记录的隔离边界，空值会让所有分组退化到同一行，直接拒绝
            throw new UpgradeException("注册表[" + registry.getClass().getName() + "]的 group() 不能为空");
        }
        this.registry = registry;
        this.scriptResourceLoader = scriptResourceLoader == null ? new ScriptResourceLoader() : scriptResourceLoader;
        this.validator = new UpgradePlanBuilder(this.scriptResourceLoader);
        this.properties = properties;
        this.repository = new UpgradeRecordRepository(upgradeJdbcContext.jdbcExecutor(),
                upgradeJdbcContext.dialect(), properties);
        this.parser = new FormattedSqlParser();

        this.scriptExecutor = scriptExecutor != null ? scriptExecutor
                : new SqlScriptExecutor(upgradeJdbcContext.connectionProvider(),
                        properties.getQueryTimeoutSeconds());
        this.schemaMetadata = upgradeJdbcContext.schemaMetadata();
        this.dialect = upgradeJdbcContext.dialect();
        this.lockManager = lockManager;
    }

    /**
     * 自行加锁并升级到目标版本。
     * <p>
     * 目标版本取自 {@link UpgradeVersionRegistry#targetVersion()}；该方法返回空时取 {@code versions()}
     * 里的最高版本。没有独立的配置项——目标版本与版本清单同源，避免两处状态不一致。
     * 锁作用域按 {@code orivone.dbupgrade.global-lock} 决定：
     * true 用 {@link UpgradeLockManager#GLOBAL_SCOPE}，false 用本分组的 {@code group()}。
     * <p>
     * 方法本身不做异常吞掉：注册表非法、脚本缺失、SQL 执行失败、禁止降级等都会抛出 {@link UpgradeException}，
     * 由调用方决定是否阻断启动。
     *
     * @return 升级结果
     */
    public UpgradeResult upgrade() {
        return doUpgrade(lockScope(), true);
    }

    /**
     * 在调用方已持锁的前提下执行升级，本方法不加锁、也不释放锁。
     * <p>
     * 用于全局单例执行（{@code global-lock=true}）：调用方在遍历所有注册表之前取一次全局锁，
     * 保证整批升级过程中没有其他节点插入。
     *
     * @return 升级结果
     */
    public UpgradeResult upgradeWithLockHeld() {
        return doUpgrade(lockScope(), false);
    }

    /**
     * 当前生效的锁作用域
     */
    private String lockScope() {
        return properties.isGlobalLock() ? UpgradeLockManager.GLOBAL_SCOPE : registry.group();
    }

    /**
     * 升级主流程
     *
     * @param scope       锁作用域，仅用于日志与加锁
     * @param acquireLock 是否由本方法负责加锁/释放锁
     */
    private UpgradeResult doUpgrade(String scope, boolean acquireLock) {
        if (!properties.isEnabled()) {
            log.info("数据库自动升级已关闭（orivone.dbupgrade.enabled=false），跳过");
            return UpgradeResult.skipped(VersionUtils.INITIAL_VERSION, "自动升级已关闭");
        }

        List<UpgradeVersion> versions = validator.validateAndSort(registry.versions());
        if (versions.isEmpty()) {
            log.info("版本注册表为空，不执行任何数据库升级");
            return UpgradeResult.skipped(VersionUtils.INITIAL_VERSION, "版本注册表为空");
        }
        if (properties.isCheckUndeclaredFiles()) {
            validator.warnUndeclaredFiles(versions, properties.getScriptRoot());
        }

        // 目标版本跟着注册表走：声明了就按声明的，没声明就取最高版本。
        // 不放在配置文件里，是因为注册表已经是「有哪些版本」的唯一真相来源，
        // 拆成两处就会出现「代码里加了版本、配置里忘了放开」。
        String declaredTarget = registry.targetVersion();
        String target = resolveTarget(declaredTarget, versions);
        log.info("数据库升级检查开始：分组={}，目标版本={}{}，注册表版本={}",
                registry.group(), target,
                StrUtils.isBlank(declaredTarget) ? "（取注册表最高版本）" : "（注册表显式指定）",
                versions.stream().map(UpgradeVersion::version).collect(Collectors.toList()));

        if (acquireLock && !lockManager.tryLock(scope)) {
            log.warn("未获取到升级锁[{}]，其他任务正在执行升级，本节点跳过", scope);
            return UpgradeResult.skipped("-", "其他任务正在升级（锁 " + scope + "），本节点跳过");
        }
        try {
            return runUpgrade(versions, target);
        } finally {
            if (acquireLock) {
                lockManager.unlock(scope);
            }
        }
    }

    /**
     * 算出这次要升到哪一版。
     * <p>
     * 注册表声明了就用声明值，没声明就取 {@code versions} 的末位（调用前已按版本号升序排好）。
     * 声明值不合规直接抛异常，不「就近取一个版本」——否则日志写着一个版本、水位停在另一个版本，
     * 排查时看不出是笔误。低于最高版本是允许的，那是灰度发布的正规用法，只提示一句。
     *
     * @param declaredTarget 注册表声明的目标版本，可能为 null 或空白
     * @param versions       已校验并升序排序的版本清单，不允许为空
     * @return 最终目标版本号
     */
    private String resolveTarget(String declaredTarget, List<UpgradeVersion> versions) {
        String maxVersion = versions.get(versions.size() - 1).version();
        if (StrUtils.isBlank(declaredTarget)) {
            return maxVersion;
        }
        String target = StrUtils.strip(declaredTarget);
        VersionUtils.requireValid(target, "注册表[" + registry.group() + "]的 targetVersion()");
        if (findVersion(versions, target) == null) {
            // 不在清单里就必须报错：如果放任它参与比较，实际会「就近停在上一个已声明版本」，
            // 日志写着目标 1.0.5、水位停在 1.0.0，排查时根本看不出是笔误
            List<String> declared = new ArrayList<String>(versions.size());
            for (UpgradeVersion version : versions) {
                declared.add(version.version());
            }
            throw new UpgradeException("注册表[" + registry.group() + "]声明的目标版本[" + target
                    + "]不在版本清单" + declared + "中；目标版本只能取已声明的版本号，"
                    + "或让 targetVersion() 返回 null 表示升到最高版本");
        }
        if (VersionUtils.isGreater(maxVersion, target)) {
            log.info("注册表[{}]显式指定目标版本[{}]，低于最高版本[{}]，本次不执行的版本留待下次发布",
                    registry.group(), target, maxVersion);
        }
        return target;
    }

    /**
     * 锁内执行的升级主流程
     */
    private UpgradeResult runUpgrade(List<UpgradeVersion> versions, String target) {
        if (properties.isAutoCreateTable()) {
            repository.ensureTables();
        } else {
            repository.assertTables();
        }
        String operator = operator();
        repository.recoverStaleExecuting(registry.group(), operator);

        String current = repository.findCurrentVersion(registry.group())
                .map(UpgradeVersionRecord::version)
                .orElse(VersionUtils.INITIAL_VERSION);
        VersionUtils.requireValid(current, "当前版本记录");
        log.info("当前数据库版本：{}，目标版本：{}", current, target);

        if (VersionUtils.isGreater(current, target)) {
            throw new UpgradeException("禁止降级：数据库当前版本[" + current + "]高于目标版本[" + target
                    + "]，框架不支持自动降级，请人工处理");
        }
        checkInstallBaseline(versions, current, target);

        List<UpgradeVersion> pending = versions.stream()
                .filter(version -> VersionUtils.isGreater(version.version(), current))
                .filter(version -> VersionUtils.isLessOrEqual(version.version(), target))
                .collect(Collectors.toList());

        if (pending.isEmpty()) {
            return handleNoPendingVersion(versions, current, target);
        }

        preloadAndValidate(pending);

        List<String> applied = new ArrayList<String>(pending.size());
        for (UpgradeVersion version : pending) {
            log.info("===== 开始执行版本 {}（脚本文件 {} 个）=====", version.version(), version.filePaths().size());
            applyVersion(version);
            repository.saveCurrentVersion(registry.group(), version.version(), operator);
            applied.add(version.version());
            log.info("===== 版本 {} 执行完成，版本记录已推进 =====", version.version());
        }
        return UpgradeResult.success(current, applied.get(applied.size() - 1), applied);
    }

    /**
     * 首次安装时校验目标版本不低于 install 基线版本
     */
    private void checkInstallBaseline(List<UpgradeVersion> versions, String current, String target) {
        if (!VersionUtils.isEqual(current, VersionUtils.INITIAL_VERSION)) {
            return;
        }
        UpgradeVersion installVersion = null;
        for (UpgradeVersion candidate : versions) {
            if (candidate.install()) {
                installVersion = candidate;
                break;
            }
        }
        if (installVersion == null) {
            log.warn("未检测到版本记录，且注册表未声明 install 基线版本，将按版本号顺序执行目标版本之前的全部版本");
            return;
        }
        if (VersionUtils.isGreater(installVersion.version(), target)) {
            throw new UpgradeException("首次安装需要至少升级到基线版本[" + installVersion.version()
                    + "]，当前目标版本[" + target + "]过低");
        }
        log.info("未检测到版本记录，按首次安装处理，基线版本：{}", installVersion.version());
    }

    /**
     * 没有待执行版本时的处理：当前版本与目标一致时，补跑该版本内未完成的 changeset
     */
    private UpgradeResult handleNoPendingVersion(List<UpgradeVersion> versions, String current, String target) {
        int retried = 0;
        if (VersionUtils.isEqual(current, target)) {
            UpgradeVersion currentVersion = findVersion(versions, current);
            if (currentVersion != null) {
                log.info("当前版本[{}]与目标版本一致，检查该版本内是否有未完成的 changeset", current);
                retried = applyVersion(currentVersion);
            }
        } else if (VersionUtils.isGreater(target, current)) {
            // 水位落后于目标却没有待执行版本，说明当前水位并不是注册表声明过的版本
            // （例如别的分支发布过更高版本、或注册表被回退），靠「按版本推进」补不齐
            String maxRegistered = versions.get(versions.size() - 1).version();
            log.warn("当前水位[{}]到目标版本[{}]之间没有任何已声明的版本，注册表最高版本为[{}]；"
                    + "通常说明当前水位不是注册表声明过的版本，请检查注册表是否漏声明了该版本",
                    current, target, maxRegistered);
        } else {
            log.info("当前版本[{}]已高于注册表可升级范围（目标版本[{}]），无需升级", current, target);
        }
        if (retried > 0) {
            log.info("版本号未变，但补跑了 {} 个未完成的 changeset", retried);
            return UpgradeResult.retried(current, retried);
        }
        log.info("无需升级：当前版本 {}，目标版本 {}", current, target);
        return UpgradeResult.skipped(current, "当前版本已是最新（" + current + "），无需升级");
    }

    /**
     * 执行前预加载并校验：changesetId 必须跨文件、跨版本唯一。
     * <p>
     * 放在执行前统一校验，避免执行到一半才发现重复，导致版本停留在中间状态。
     */
    private void preloadAndValidate(List<UpgradeVersion> versions) {
        Map<String, String> owners = new LinkedHashMap<String, String>();
        for (UpgradeVersion version : versions) {
            for (String filePath : version.filePaths()) {
                ParsedSqlFile parsedFile = loadFile(filePath);
                for (Changeset body : parsedFile.changesets()) {
                    String location = version.version() + "/" + filePath;
                    String owner = owners.put(body.changesetId(), location);
                    if (owner != null) {
                        throw new UpgradeException("changesetId[" + body.changesetId() + "]重复出现：" + owner
                                + " 与 " + location + "，author:id 必须全局唯一");
                    }
                }
            }
        }
        log.debug("待执行 changeset 预校验通过，共 {} 个", owners.size());
    }

    /**
     * 执行一个版本下的所有脚本文件与 changeset，按注册表声明顺序
     *
     * @return 本次真正「补跑」的 changeset 数量。
     * runAlways 的例行重跑不计入：它本来就每次都要执行，不代表有未完成的工作，
     * 若计入会让「同版本重跑」被误报成一次升级。
     */
    private int applyVersion(UpgradeVersion version) {
        int executed = 0;
        for (String filePath : version.filePaths()) {
            ParsedSqlFile parsedFile = loadFile(filePath);
            if (parsedFile.isEmpty()) {
                log.warn("脚本文件[{}]中没有可执行的 changeset", filePath);
                continue;
            }
            log.info("脚本文件[{}]包含 {} 个 changeset", filePath, parsedFile.changesets().size());
            for (Changeset body : parsedFile.changesets()) {
                boolean ran = executeChangeset(new VersionedChangeset(body, version.version()));
                if (ran && !body.runAlways()) {
                    executed++;
                }
            }
        }
        return executed;
    }

    /**
     * 执行单个 changeset，包含幂等判断、checksum 校验、前置条件评估、DDL 存在性判断与状态记录
     *
     * @return true 表示本次真正执行了 SQL；false 表示按规则跳过（已成功、前置条件不满足、DDL 已存在）
     */
    private boolean executeChangeset(VersionedChangeset meta) {
        Changeset body = meta.body();
        String changesetId = body.changesetId();

        Optional<UpgradeRecord> existing = repository.findRecord(registry.group(), changesetId);
        if (existing.isPresent() && ChangesetStatus.SUCCESS.code().equals(existing.get().status())) {
            if (!shouldExecute(body, existing.get())) {
                log.debug("changeset[{}] 已执行成功且内容未变，跳过", changesetId);
                return false;
            }
        } else if (existing.isPresent()) {
            // 走到这里说明上次没跑成功（FAILED，或上一轮刚被 recoverStaleExecuting 从 EXECUTING 改回来）。
            // 框架无法判断崩溃前哪些语句真的落库了：DDL 会隐式提交，DML 也不在一个事务里，
            // 所以这里只提示风险，不擅自改变「重跑」这个既定语义。
            log.warn("changeset[{}] 上次执行未成功（状态={}，结束于 {}），本次重新执行；"
                            + "若上次已提交了部分 INSERT/UPDATE，重复执行可能造成数据重复，"
                            + "请确认该 changeset 内的 DML 可重跑（例如用 MERGE、先删后插或唯一键兜底）",
                    changesetId, existing.get().status(), existing.get().endTime());
        }

        if (!evaluatePreconditions(meta)) {
            return false;
        }

        List<String> statements = SqlStatementSplitter.split(body.sql(), body.endDelimiter(), body.splitStatements(),
                dialect.supportsHashComment());
        if (statements.isEmpty()) {
            log.warn("changeset[{}] 拆分后没有有效语句，跳过", changesetId);
            markSkipped(meta, "拆分后没有有效语句");
            return false;
        }

        List<String> pending = new ArrayList<String>(statements.size());
        int skippedDdl = 0;
        for (String statement : statements) {
            // 交给注册表做最后一次改写（表前缀、schema 替换之类）。
            // 没覆盖 rewriteSql 时拿到的就是入参本身；返回 null 或空白算「不改写」，由下面兜住。
            String statementTemp = this.registry.rewriteSql(statement);
            // StrUtils.isBlank 对 null 返回 true，所以 null 与空串一起走「沿用原语句」分支；
            // 不能写成 statementTemp.isBlank()，null 时会 NPE
            if (!StrUtils.isBlank(statementTemp)) {
                statement = statementTemp;
            }
            // 存在性判断与执行用的都是改写后的语句，落库的 checksum 仍是改写前的原文。
            // 改写结果不进 checksum：它随环境变化，算进去会让换前缀就全量重判。
            if (schemaMetadata.alreadyApplied(statement)) {
                skippedDdl++;
                log.debug("changeset[{}] 中 DDL 已生效，跳过：{}", changesetId, abbreviate(statement));
            } else {
                pending.add(statement);
            }
        }
        if (pending.isEmpty()) {
            log.info("changeset[{}] 的 {} 条 DDL 全部已生效，标记为已执行", changesetId, skippedDdl);
            markSkipped(meta, "DDL 已存在，跳过执行（" + skippedDdl + " 条）");
            return false;
        }
        if (skippedDdl > 0) {
            log.info("changeset[{}] 有 {} 条 DDL 已生效，本次执行剩余 {} 条", changesetId, skippedDdl, pending.size());
        }

        String operator = operator();
        LocalDateTime start = LocalDateTime.now();
        long recordId = repository.beginChangeset(registry.group(), meta, operator, start);
        log.info("执行 changeset[{}]：版本={}，文件={}，语句数={}", changesetId, meta.version(), meta.filePath(), pending.size());
        try {
            // 整批交给执行器：一个连接里顺序跑完这个 changeset，
            // SET、临时表这类会话级语句因此能对后面的语句生效，连接也只借还一次。
            // 不包事务——DDL 自己会隐式提交，重跑安全还是靠 changeset 幂等与存在性判断。
            scriptExecutor.executeAll(pending, meta.version(), changesetId);
            repository.markSuccess(recordId, null);
            log.info("changeset[{}] 执行成功", changesetId);
            return true;
        } catch (UpgradeException e) {
            repository.markFailed(recordId, e.getMessage());
            throw e;
        } catch (Exception e) {
            repository.markFailed(recordId, e.getMessage());
            throw new UpgradeException("changeset 执行失败：" + e.getMessage(), meta.version(), changesetId, e);
        }
    }

    /**
     * 判断已成功的 changeset 是否需要重跑
     *
     * @return true 需要执行，false 跳过；内容被静默修改且未声明 runOnChange 时抛异常
     */
    private boolean shouldExecute(Changeset body, UpgradeRecord record) {
        if (body.runAlways()) {
            log.info("changeset[{}] 声明 runAlways:true，本次重新执行", body.changesetId());
            return true;
        }
        if (checksumSame(record.checksum(), body.checksum())) {
            return false;
        }
        if (StrUtils.isBlank(record.checksum())) {
            log.warn("changeset[{}] 的历史记录缺少 checksum（可能是旧版本遗留），按内容未变处理并补齐", body.changesetId());
            repository.updateChecksum(record.id(), body.checksum());
            return false;
        }
        if (body.runOnChange()) {
            log.info("changeset[{}] 声明 runOnChange:true 且内容已变化，重新执行", body.changesetId());
            return true;
        }
        throw new UpgradeException("changeset 内容已被修改但未声明 runOnChange:true，拒绝执行；"
                + "如确需变更请新增 changeset，或显式声明 runOnChange:true",
                record.version(), body.changesetId(), null);
    }

    /**
     * 评估前置条件
     *
     * @return true 继续执行该 changeset，false 表示已按 MARK_RAN 处理完毕
     */
    private boolean evaluatePreconditions(VersionedChangeset meta) {
        Changeset body = meta.body();
        Precondition unsatisfied = schemaMetadata.firstUnsatisfied(body.preconditions());
        if (unsatisfied == null) {
            return true;
        }
        String description = describe(unsatisfied);
        switch (unsatisfied.onFail()) {
            case HALT:
                throw new UpgradeException("前置条件不满足，升级中止：" + description,
                        meta.version(), body.changesetId(), null);
            case MARK_RAN:
                log.warn("changeset[{}] 前置条件不满足（{}），按 onFail:MARK_RAN 标记为已执行",
                        body.changesetId(), description);
                markSkipped(meta, "前置条件不满足，onFail:MARK_RAN 跳过：" + description);
                return false;
            case CONTINUE:
            default:
                log.warn("changeset[{}] 前置条件不满足（{}），按 onFail:CONTINUE 继续执行",
                        body.changesetId(), description);
                return true;
        }
    }

    /**
     * 记录一条“执行跳过”的成功状态，保证下次不再重复判断
     */
    private void markSkipped(VersionedChangeset meta, String remark) {
        String operator = operator();
        long recordId = repository.beginChangeset(registry.group(), meta, operator, LocalDateTime.now());
        repository.markSuccess(recordId, remark);
    }

    /**
     * 加载并缓存脚本解析结果
     */
    private ParsedSqlFile loadFile(final String filePath) {
        ParsedSqlFile cached = parsedFileCache.get(filePath);
        if (cached != null) {
            return cached;
        }
        String content = scriptResourceLoader.read(filePath);
        ParsedSqlFile parsed = parser.parse(filePath, content);
        ParsedSqlFile existing = parsedFileCache.putIfAbsent(filePath, parsed);
        return existing == null ? parsed : existing;
    }

    /**
     * 执行人标识，本地缓存一次
     */
    private String operator() {
        String cached = this.cachedOperator;
        if (cached == null) {
            cached = properties.resolveOperator();
            this.cachedOperator = cached;
        }
        return cached;
    }

    private static UpgradeVersion findVersion(List<UpgradeVersion> versions, String version) {
        for (UpgradeVersion candidate : versions) {
            if (VersionUtils.isEqual(candidate.version(), version)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean checksumSame(String left, String right) {
        return !StrUtils.isBlank(left) && left.equalsIgnoreCase(right);
    }

    private static String describe(Precondition precondition) {
        if (precondition == null) {
            return "unknown";
        }
        String target = precondition.sqlCheckSql() != null
                ? precondition.sqlCheckSql() : String.valueOf(precondition.args());
        return precondition.type() + " " + target + "（onFail:" + precondition.onFail() + "）";
    }

    private static String abbreviate(String sql) {
        return StrUtils.abbreviate(sql, 160);
    }
}
