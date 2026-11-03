<!-- 改动本文件时同步更新 README.en.md（英文版），两边章节与结论保持一致。 -->

# orivone-dbupgrade

> [English version →](README.en.md)

<p align="center">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-Apache--2.0-blue.svg"></a>
  <img alt="Java" src="https://img.shields.io/badge/Java-8%20~%2025-orange.svg">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-2.7%20%7C%203.x%20%7C%204.x-6DB33F.svg">
  <img alt="Databases" src="https://img.shields.io/badge/Databases-6%20dialects-informational.svg">
  <img alt="Core size" src="https://img.shields.io/badge/core-~120KB-lightgrey.svg">
</p>

轻量级数据库**安装与升级**组件：应用启动时把库结构对齐到目标版本，首次安装与后续增量升级走同一条链路。
适合私有化交付、内网部署，以及需要同时兼容多代 Spring Boot、数据库涵盖 MySQL / PostgreSQL / Oracle / SQL Server / 达梦等多种产品的系统。

三个词概括：**小**、**稳**、**可编程**。

- **小** —— core 只依赖 `slf4j-api`（provided），产物约 120 KB，默认零传递依赖；
- **稳** —— 三层幂等 + 禁止降级 + 多节点互斥，重复部署、升级中断、脚本被改都有对应处理；
- **可编程** —— 版本由 Java 声明而不是文件名约定，SQL 可在执行前改写，连「怎么执行脚本」都能替换。

> 核心设计：**版本由代码声明，文件名不承担语义。**
> 版本管范围，注册表管文件归属与顺序，changeset 管执行记录与幂等，锁管并发。

---

## 为什么是它

如果你只需要「启动时把表建好、以后按版本增量升级」，Flyway 和 Liquibase 都偏重：额外的依赖、
额外的学习曲线，以及某些老环境里并不友好的语法约束。这个项目是给这类场景写的小引擎。

| 维度 | orivone-dbupgrade | 常见做法 |
| --- | --- | --- |
| 核心依赖 | 仅 `slf4j-api`（provided），产物约 120 KB | 通常带一套完整生态与传递依赖 |
| 版本定义 | Java 代码里声明，**编译期可见、可重构** | 靠文件名前缀 / 目录层级约定 |
| 执行粒度 | 每个 changeset 独立记录状态与内容指纹 | 基本一致 |
| 多节点并发 | 默认单表锁，无需中间件；可选 Redisson | 依赖各自的锁策略 |
| 兼容跨度 | 一份源码兼容 Boot 2.7 / 3.x / 4.x，字节码 Java 8 | 通常按大版本分支维护 |
| 扩展方式 | 注册表、SQL 改写钩子、`ScriptExecutor`、方言、锁，五处可换 | 各有插件模型 |

三条与其它方案最不一样的地方：

1. **版本是代码，不是文件名。** `UpgradeVersion("1.1.0", false, files)` 明确了哪一版包含哪些文件，
   IDE 能跳转、能重构、能编译期检查，不需要背命名规则。
2. **「安装」和「升级」不是目录概念。** `install` 只是 `UpgradeVersion` 构造函数里的一个布尔参数，
   决定「库为空时要不要跑它」，与文件放在哪个目录毫无关系。
3. **升级过程可以写自己的代码。** `rewriteSql` 改 SQL，`ScriptExecutor` 接管执行，两者都不需要改框架。

---

## 30 秒上手

五步跑通，不用写启动类。这一节是 Spring Boot 的接法；不用 Spring 的看下一节。

**① 加依赖**

```xml
<dependency>
    <groupId>com.orivone</groupId>
    <artifactId>orivone-dbupgrade-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

starter 的 Spring 依赖全是 `provided`，不会顶掉你自己的 Boot 版本，也不会替你引 Spring 与数据库驱动。

**② 写一个脚本**（放在 `src/main/resources` 下任意位置）

```sql
--dbupgrade formatted sql

--changeset zhangsan:create-user
CREATE TABLE t_user (
  id   BIGINT       NOT NULL,
  name VARCHAR(64)  NOT NULL,
  PRIMARY KEY (id)
);
--rollback DROP TABLE t_user;
```

文件名不解析版本号、不参与排序，顺序完全由注册表的文件列表决定。

**③ 声明版本注册表**（接入时唯一需要手写的 Java 代码）

```java
@Configuration(proxyBeanMethods = false)
public class MyUpgradeVersionConfig {

    @Bean
    public UpgradeVersionRegistry myRegistry() {
        return new UpgradeVersionRegistry() {

            @Override
            public List<UpgradeVersion> versions() {
                return Arrays.asList(
                    // 安装基线：install = true，且必须是版本号最小的那个
                    new UpgradeVersion("1.0.0", true, Arrays.asList(
                            "upgrade-scripts/install/base.sql")),
                    // 增量版本：列表顺序即执行顺序
                    new UpgradeVersion("1.1.0", false, Arrays.asList(
                            "upgrade-scripts/upgrade/order.sql",
                            "upgrade-scripts/upgrade/user.sql")));
            }

            @Override public String group() { return "myapp"; }

            @Override public int order() { return 100; }
        };
    }
}
```

**④ 最小配置**（三项，都有默认值）

```yaml
orivone:
  dbupgrade:
    enabled: true      # 总开关
    fail-fast: true    # 升级失败是否阻断启动
    lock-type: db      # 单表锁，多节点可用，无需中间件
```

**⑤ 启动**

`UpgradeRunner` 是最高优先级的 `ApplicationRunner`，容器就绪后自动执行。首次启动会创建两张记录表与一张锁表：

```
数据库升级检查开始：分组=myapp，目标版本=1.1.0（取注册表最高版本），注册表版本=[1.0.0, 1.1.0]
当前数据库版本：0.0.0，目标版本：1.1.0
===== 开始执行版本 1.0.0（脚本文件 1 个）=====
===== 版本 1.0.0 执行完成，版本记录已推进 =====
===== 开始执行版本 1.1.0（脚本文件 2 个）=====
===== 版本 1.1.0 执行完成，版本记录已推进 =====
```

再次启动时 `UpgradeResult.executed()` 返回 `false`，表示已是最新、没有 changeset 需要执行。

完整步骤与每个参数的语义见 **[快速开始](http://dbupgrade.orivone.com/start/quick-start.html)**。

---

## 不用 Spring 的接法

`orivone-dbupgrade-core` 不认识 Spring，`UpgradeSettings` 是普通 POJO，装配全靠 `new`。
定时任务、命令行工具、Quarkus / Micronaut 这类非 Spring 框架，或者只想在集成测试里跑一次升级，
都可以直接用 core，不引 starter。

**依赖只要两个**：core + 一个日志实现（core 的 `slf4j-api` 是 `provided`，不替你决定版本）。

```xml
<dependency>
    <groupId>com.orivone</groupId>
    <artifactId>orivone-dbupgrade-core</artifactId>
    <version>1.0.1</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>1.7.36</version>
</dependency>
```

（工程里已经有 logback / log4j2 的话，第二个依赖不用加。）

**六步装配**，注册表沿用上面第 ③ 步那个，不用改：

```java
// ① 连接来源：core 只认这个接口，底层是 DriverManager 还是连接池它不关心
ConnectionProvider connections = new DriverManagerConnectionProvider(url, user, password);

// ② 方言：直接指定。手上有 DataSource 的话可以让它自己探测
UpgradeDialect dialect = DialectResolver.of(DbType.MYSQL);
// 或者：UpgradeDialect dialect = DialectResolver.resolve(dataSource, null);

// ③ 连接 + 方言 + 元数据检查器收在一个上下文里，多数据源不会串库
UpgradeContext ctx = new UpgradeContext(connections, dialect);

// ④ 配置：没有 application.yml，直接 set
UpgradeSettings settings = new UpgradeSettings();
settings.setAutoCreateTable(true);    // 记录表、锁表不存在时自动建
settings.setOperator("nightly-job");  // 写进 record.executed_by，便于追溯

// ⑤ 引擎：锁管理器自己选，这里用数据库单表锁
UpgradeEngine engine = new UpgradeEngine(new MyRegistry(), settings, ctx,
        new DbUpgradeLockManager(ctx.jdbcExecutor(), dialect, settings));

// ⑥ 执行：result.executed() 为 false 表示已是最新，重复调用安全
UpgradeResult result = engine.upgrade();
```

**换成连接池**只需要一行——`ConnectionProvider` 只有一个方法：

```java
ConnectionProvider connections = dataSource::getConnection;
```

core 不认识 HikariCP、Druid、Tomcat JDBC，也就不需要为它们维护适配；连接什么时候关、池里怎么配，
全归你自己管。

**单机可以不要锁表**：把 `DbUpgradeLockManager` 换成 `LocalUpgradeLockManager`，走 JVM 内
`ReentrantLock`，不建表、不占连接。多节点部署别这么用。

**锁由你传进去的那个管理器决定**，没有「配置项切换算法」这回事。`lockType` 是 starter 的配置项，
Spring 环境按它挑 Bean；纯 JDBC 场景根本不读它，所以上面那段装配里没有 `setLockType`。
从 Spring 配置搬过来时最常见的误解，是把 YAML 里的 `lock-type: redis` 一起抄进来，然后发现锁没生效。

整条链路不依赖 Spring、不依赖连接池、不依赖 Redis。多节点互斥用的是数据库单表锁，
几台机器连同一个库就够，不必为了升级额外部署中间件。

六种数据库的原生装配完整代码在 `dbupgrade-demo/jdbc-demo`，H2 内存版一条命令能跑
（或者在 IDE 里直接运行 `H2JdbcDemo` 的 `main`）：

```bash
mvn -pl dbupgrade-demo/jdbc-demo -am compile exec:java \
    -Dexec.mainClass=com.orivone.dbupgrade.demo.jdbcdemo.H2JdbcDemo
```

扩展点（自定义 `ScriptExecutor`、改写 SQL、新增方言）见[扩展与自定义](http://dbupgrade.orivone.com/reference/extensions.html)。

---

## 兼容矩阵

| 项 | 支持范围 | 说明 |
| --- | --- | --- |
| **JDK** | **8 ~ 25** | core / starter / lock-redisson 字节码均为 major 52（Java 8） |
| **Spring Boot** | **3.x / 4.x（完整支持）**，**2.7（尽力兼容）** | 最低支持 2.7.18，一份源码兼容三代；2.7 已于 2023-11 EOL，新项目不建议选 |
| **MySQL** | **5.7 ~ 8.4** | 驱动固定 8.0.33：Connector/J 9.x 只支持 server 8.0+，连不上 5.7 |
| **其它数据库** | PostgreSQL 系、Oracle 12c+、达梦 DM、SQL Server 2016+、H2 | 见 [多数据库支持](http://dbupgrade.orivone.com/reference/databases.html) |
| **升级锁** | 默认数据库单表锁（无需中间件）；可选 Redisson / 单机内存锁 | 见 [并发与锁](http://dbupgrade.orivone.com/reference/locking.html) |

上表是**产物**的运行范围。构建整个仓库需要 JDK 17+（示例工程默认跑 Boot 4）。

同一个测试类 `BootCompatibilityTest` 在 Boot 2.7.18 / 3.4.9 / 4.1.0 上各启动一次完整容器并断言升级结果，
结论来自实际运行而非推断。

**明确不支持**：自动降级、DDL 整体回滚、按分组配置目标版本。理由见 [已知限制](http://dbupgrade.orivone.com/project/faq.html)。

---

## 它怎么工作

```
启动
 └─ 取注册表 → 算目标版本 → 比对 dbupgrade_version 水位
      └─ 有更高版本？
           ├─ 否 → executed=false，结束
           └─ 是 → 加锁（单表锁 / Redisson / 内存锁）
                └─ 逐版本、逐 changeset：
                     ├─ 查 dbupgrade_record：已成功且 checksum 未变 → 跳过
                     ├─ 判断 DDL 对象是否已存在 → 已存在且声明幂等 → 跳过
                     ├─ 记录状态为 EXECUTING → 执行 → 成功改写 SUCCESS
                     └─ 失败 → 记 FAILED，按 fail-fast 决定是否阻断启动
                └─ 全部成功 → 推进水位 → 释放锁
```

三层幂等（记录表 / DDL 存在性 / changeset 声明）、状态迁移、`checksum` 覆盖范围等细节，
见 [执行流程与幂等](http://dbupgrade.orivone.com/reference/execution.html)。

---

## 模块

| 模块 | 职责 | 依赖 | 字节码 |
| --- | --- | --- | --- |
| `orivone-dbupgrade-core` | 引擎本体：解析、计划、执行、记录、六种方言 | 仅 `slf4j-api`（provided） | Java 8 |
| `orivone-dbupgrade-spring-boot-starter` | Boot 自动装配 + `UpgradeRunner` | core + `spring-boot(-autoconfigure)`（provided） | Java 8 |
| `orivone-dbupgrade-lock-redisson` | 可选：Redis 分布式锁 | core + Redisson | Java 8 |
| `dbupgrade-demo` | 两个可运行示例（不参与生产依赖，也不发布） | — | Java 17+ |

三个发布模块的 `src/test` 是空的，所以推到中央仓库的 pom 里 `<dependencies>` 只剩真正需要的
运行时依赖（core 仅 `slf4j-api`），不会出现 junit、h2 这类只有测试才用的条目 —— 虽然 test scope
本来就不传递，但对外声明「零框架依赖」的库，pom 与这句话应当完全一致。

Spring Boot 项目引 `orivone-dbupgrade-spring-boot-starter`；不用 Spring 的直接引 `orivone-dbupgrade-core`；
要 Redis 锁再加 `orivone-dbupgrade-lock-redisson`。这几个依赖**互相不是包含关系**——
starter 的传递依赖只有 core，core 的依赖全是 `provided`，所以一路跟下来，
不会有 Redisson、netty、kryo 或数据库驱动被顺带引进你的工程。

模块内部包结构与分层说明见 [模块结构](http://dbupgrade.orivone.com/start/quick-start.html)。

---

## 文档

完整文档在 **<http://dbupgrade.orivone.com>**

脚本规范、全部配置项、六种方言差异、锁的实现、扩展点、常见问题都在那边。
本页只回答「能不能用、怎么跑起来」；细节以文档站为准，两边不一致时按文档站处理。

---

## 示例工程

仓库里有两个可直接运行的示例，覆盖六种数据库、三个 Boot 版本、两种接入方式：

```bash
# Spring Boot 接入（默认 H2 内存库，零依赖直接跑）
mvn -pl dbupgrade-demo/spring-boot-demo -am spring-boot:run

# 连真实 MySQL：改 application-mysql.yml 里的连接串后切 profile
mvn -pl dbupgrade-demo/spring-boot-demo -am spring-boot:run -Dspring-boot.run.profiles=mysql
```

纯 JDBC 那个 demo 的跑法见前面「不用 Spring 的接法」一节，两个 demo 的脚本与场景清单见
[示例工程](http://dbupgrade.orivone.com/usage/demo.html)。

---

## 常见问题

**Q：为什么不用 Flyway / Liquibase？**
已经在用且用得顺手，没必要换。这个项目面向「不想为一件事引入一整套生态」的场景：core 只有一个
`slf4j-api` 依赖，starter 的 Spring 依赖全是 `provided`。

**Q：升级到一半进程被杀了会怎样？**
启动时把残留的 `EXECUTING` 记录恢复为 `FAILED` 并重跑该 changeset。重跑对 DDL 安全、对 DML 不一定：
崩溃前已提交的 `INSERT` 会被再插一遍，所以种子数据要么写成幂等（先删后插 / `MERGE`），要么靠唯一键兜底。

**Q：改了历史 changeset 的内容，启动报 checksum 不一致？**
正确做法是**新增一个 changeset**，而不是改历史。如果对象本身是「定义即状态」（视图、存储过程），
给它加 `runOnChange:true`。

**Q：多个模块都用这个组件，会互相干扰吗？**
不会。每个注册表一个 `group()`，水位与 changeset 记录按分组隔离，甚至可以用相同的 `author:id`。

更多问题见 [已知限制与常见问题](http://dbupgrade.orivone.com/project/faq.html)。

---

## 项目状态

- **当前版本**：`1.0.1`
- **维护方式**：单人维护，issue 会看，但不承诺响应时限
- **稳定性**：核心接口（`UpgradeVersionRegistry`、`UpgradeVersion`、SQL 脚本指令）已定型；
  `1.x` 内不破坏兼容
- **测试**：`core` 58 个用例；`demo` 5 个用例在 Boot 2.7 / 3.x / 4.x 三条线上各跑一遍，全部通过
- **已知边界**：非 MySQL 方言尚无真实库回归；生产使用前请逐库验证（见 [已知限制](http://dbupgrade.orivone.com/project/faq.html)）

---

## 许可证

[Apache License 2.0](LICENSE)，OSI 认可的开源协议。

**把它加进依赖，就算完成了授权。** 不收费、不报备、不用联系我们，也不用公开你的代码。

**按你的用法查，需要做的事只有这些：**

| 你的用法 | 需要做的事 |
| --- | --- |
| 内部系统、私有化部署、对外提供 SaaS 服务 | 什么都不用做。节点数、集群规模、QPS、服务客户数量都不受限 |
| 修改源码自己用 | 什么都不用做 |
| 修改后再把代码分发出去 | 在改过的文件里注明「此文件已被修改」 |
| 打包进对外销售的产品 | 随产品附带 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)；在文档或关于页面写一句用到了 orivone-dbupgrade |
| 用 `orivone` 名称或 Logo 做宣传 | 不在授权范围内（第 6 条），请勿暗示官方背书 |

除上表之外没有别的条件：没有限制节点数，没有「商用须付费」，没有第二份补充协议。

**你会得到的保障：**

- **专利授权是明确的。** 按第 3 条，贡献者已就相关专利授权给你，除你主动发起专利诉讼外不会被收回。
- **授权不会随版本变严。** `1.x` 全系列保持 Apache-2.0：今天能用的用法，将来出新版本后依然成立。
- **没有需要付费解锁的功能。** 仓库里有什么，发布出去的包里就是什么。
- **你可以自己审计。** core 唯一的编译期依赖是 `slf4j-api`，发布产物不打包任何第三方代码，
  全部依赖与许可证清单见 [NOTICE](NOTICE)。

**它不提供什么：** 按第 7、8 条，软件按「现状」提供——不附担保，作者不为使用后果承担责任。
生产环境请自行完成测试与验证。

---

<p align="center"><sub>小是刻意的，稳是底线，可编程是留给你的余地。</sub></p>
