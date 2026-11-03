<!-- Keep this file in sync with README.md (Chinese); sections and conclusions should match. -->

# orivone-dbupgrade

> [中文版 README →](README.md)

<p align="center">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-Apache--2.0-blue.svg"></a>
  <img alt="Java" src="https://img.shields.io/badge/Java-8%20~%2025-orange.svg">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-2.7%20%7C%203.x%20%7C%204.x-6DB33F.svg">
  <img alt="Databases" src="https://img.shields.io/badge/Databases-6%20dialects-informational.svg">
  <img alt="Core size" src="https://img.shields.io/badge/core-~120KB-lightgrey.svg">
</p>

A lightweight **install and upgrade** component for database schemas: on application startup it brings the schema
up to the target version, and the first install runs through exactly the same path as any later incremental upgrade.
Suited to on-premise delivery, intranet deployments, and systems that must support several generations of Spring Boot
while covering databases as varied as MySQL / PostgreSQL / Oracle / SQL Server / DM.

Three words sum it up: **small**, **predictable**, **programmable**.

- **Small** — core depends on `slf4j-api` only (provided), ships as ~120 KB, and has no transitive dependencies by default.
- **Predictable** — three layers of idempotency, no downgrades, and multi-node mutual exclusion; repeated deployments, interrupted upgrades and edited scripts each have a defined outcome.
- **Programmable** — versions are declared in Java rather than inferred from file names, SQL can be rewritten before execution, and even "how a script runs" is replaceable.

> Core principle: **versions are declared in code; file names carry no semantics.**
> Versions define the range, the registry defines file ownership and order, changesets hold execution records and idempotency, the lock handles concurrency.

---

## Why this exists

If all you need is "create the tables at startup, then apply incremental changes by version", Flyway and Liquibase are
on the heavy side: extra dependencies, extra learning curve, and syntax constraints that some older environments
tolerate poorly. This project is a small engine written for exactly that niche.

| Aspect | orivone-dbupgrade | Typical alternative |
| --- | --- | --- |
| Core dependencies | `slf4j-api` only (provided), ~120 KB artifact | Usually brings a full ecosystem and transitive dependencies |
| Version definition | Declared in Java — **visible at compile time, refactorable** | Filename prefixes / directory conventions |
| Execution granularity | Each changeset records its own status and content fingerprint | Roughly equivalent |
| Multi-node concurrency | Single-table lock by default, no middleware; Redisson optional | Each solution's own locking strategy |
| Compatibility span | One source tree for Boot 2.7 / 3.x / 4.x, Java 8 bytecode | Usually maintained as per-major-version branches |
| Extension points | Registry, SQL rewrite hook, `ScriptExecutor`, dialect, lock — five replaceable seams | Varies per plugin model |

Three things that differ most from other tools:

1. **A version is code, not a file name.** `UpgradeVersion("1.1.0", false, files)` states exactly which files belong to a version — IDE navigation, refactoring and compile-time checks all work; there is no naming convention to memorise.
2. **"Install" and "upgrade" are not directory concepts.** `install` is a boolean argument on the `UpgradeVersion` constructor: it only decides whether the version runs against an empty database. Where the file lives is irrelevant.
3. **The upgrade process itself is programmable.** `rewriteSql` rewrites SQL and `ScriptExecutor` takes over execution — neither requires touching the framework.

---

## Quick start (30 seconds)

Five steps, no bootstrap class to write. This section covers Spring Boot; if you don't use Spring, see the next section.

**1. Add the dependency**

```xml
<dependency>
    <groupId>com.orivone</groupId>
    <artifactId>orivone-dbupgrade-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

All Spring dependencies in the starter are `provided`: they neither override your own Boot version nor pull in
Spring or a database driver on your behalf.

**2. Write a script** (anywhere under `src/main/resources`)

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

The file name is not parsed for a version number and plays no part in ordering — order comes entirely from the file
list in the registry.

**3. Declare the version registry** (the only Java code you hand-write to integrate)

```java
@Configuration(proxyBeanMethods = false)
public class MyUpgradeVersionConfig {

    @Bean
    public UpgradeVersionRegistry myRegistry() {
        return new UpgradeVersionRegistry() {

            @Override
            public List<UpgradeVersion> versions() {
                return Arrays.asList(
                    // install baseline: install = true, and it must be the lowest version
                    new UpgradeVersion("1.0.0", true, Arrays.asList(
                            "upgrade-scripts/install/base.sql")),
                    // incremental versions: list order is execution order
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

**4. Minimal configuration** (three keys, all with defaults)

```yaml
orivone:
  dbupgrade:
    enabled: true      # master switch
    fail-fast: true    # block startup when an upgrade fails
    lock-type: db      # single-table lock, multi-node safe, no middleware
```

**5. Start the application**

`UpgradeRunner` is a top-priority `ApplicationRunner` and runs as soon as the context is ready. The first startup
creates the two record tables plus one lock table:

```
数据库升级检查开始：分组=myapp，目标版本=1.1.0（取注册表最高版本），注册表版本=[1.0.0, 1.1.0]
当前数据库版本：0.0.0，目标版本：1.1.0
===== 开始执行版本 1.0.0（脚本文件 1 个）=====
===== 版本 1.0.0 执行完成，版本记录已推进 =====
===== 开始执行版本 1.1.0（脚本文件 2 个）=====
===== 版本 1.1.0 执行完成，版本记录已推进 =====
```

*Sample output shown verbatim — log messages are currently emitted in Chinese.*

On the next startup `UpgradeResult.executed()` returns `false`, meaning the schema is already current and no
changeset needed to run.

For the full walkthrough and the meaning of every parameter, see **[Quick start](http://dbupgrade.orivone.com/start/quick-start.html)**.

---

## Using it without Spring

`orivone-dbupgrade-core` knows nothing about Spring; `UpgradeSettings` is a plain POJO and wiring is all `new`.
Scheduled jobs, CLI tools, non-Spring frameworks such as Quarkus / Micronaut, or simply running an upgrade once
inside an integration test — all can use core directly, without the starter.

**Two dependencies**: core plus a logging backend (core's `slf4j-api` is `provided`, so it won't pick a version for you).

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

(If the project already uses logback or log4j2, skip the second one.)

**Six lines of wiring**, reusing the registry from step 3 above unchanged:

```java
// 1) connection source: core only knows this interface, DriverManager or a pool is up to you
ConnectionProvider connections = new DriverManagerConnectionProvider(url, user, password);

// 2) dialect: pick it explicitly. With a DataSource at hand, let it detect
UpgradeDialect dialect = DialectResolver.of(DbType.MYSQL);
// or: UpgradeDialect dialect = DialectResolver.resolve(dataSource, null);

// 3) connection + dialect + metadata inspector live in one context, so multiple data sources never cross over
UpgradeContext ctx = new UpgradeContext(connections, dialect);

// 4) settings: no application.yml involved, just setters
UpgradeSettings settings = new UpgradeSettings();
settings.setAutoCreateTable(true);    // create record and lock tables when missing
settings.setOperator("nightly-job");  // written to record.executed_by for traceability

// 5) engine: you choose the lock manager; a single database table lock here
UpgradeEngine engine = new UpgradeEngine(new MyRegistry(), settings, ctx,
        new DbUpgradeLockManager(ctx.jdbcExecutor(), dialect, settings));

// 6) run: result.executed() == false means already current; repeated calls are safe
UpgradeResult result = engine.upgrade();
```

**Switching to a connection pool** is a single line — `ConnectionProvider` has exactly one method:

```java
ConnectionProvider connections = dataSource::getConnection;
```

core doesn't know HikariCP, Druid or Tomcat JDBC, so there are no adapters to maintain: when connections close and
how the pool is tuned is entirely your call.

**A single node can skip the lock table**: swap `DbUpgradeLockManager` for `LocalUpgradeLockManager`, which uses an
in-JVM `ReentrantLock` — no table, no connection held. Don't do this on multi-node deployments.

**The lock is whatever manager you pass in** — there is no "configuration switch that changes the algorithm".
`lockType` is a starter setting; the Spring environment picks a bean from it. Pure JDBC code never reads it, which is
why the wiring above has no `setLockType`. The most common mistake when migrating from the Spring setup is copying
`lock-type: redis` into a non-Spring app and then discovering the lock never took effect.

The whole path depends on neither Spring, nor a connection pool, nor Redis. Multi-node exclusion uses a single database
table lock — several machines pointing at the same database is enough, no extra middleware required just to upgrade.

Complete native wiring for all six databases lives in `dbupgrade-demo/jdbc-demo`; the H2 in-memory variant runs with
one command (or run `H2JdbcDemo`'s `main` from the IDE):

```bash
mvn -pl dbupgrade-demo/jdbc-demo -am compile exec:java \
    -Dexec.mainClass=com.orivone.dbupgrade.demo.jdbcdemo.H2JdbcDemo
```

Extension points (custom `ScriptExecutor`, SQL rewriting, adding a dialect) are covered in
[Extensions](http://dbupgrade.orivone.com/reference/extensions.html).

---

## Compatibility

| Item | Supported range | Notes |
| --- | --- | --- |
| **JDK** | **8 ~ 25** | core / starter / lock-redisson are all major version 52 (Java 8) bytecode |
| **Spring Boot** | **3.x / 4.x (fully supported)**, **2.7 (best effort)** | 2.7.18 as the floor, one source tree across three generations; 2.7 reached EOL in Nov 2023, not recommended for new projects |
| **MySQL** | **5.7 ~ 8.4** | Driver pinned to 8.0.33: Connector/J 9.x only supports server 8.0+, so it cannot connect to 5.7 |
| **Other databases** | PostgreSQL family, Oracle 12c+, DM, SQL Server 2016+, H2 | See [Multi-database support](http://dbupgrade.orivone.com/reference/databases.html) |
| **Upgrade lock** | Single-table database lock by default (no middleware); Redisson / in-process lock optional | See [Concurrency and locking](http://dbupgrade.orivone.com/reference/locking.html) |

The table above describes the **artifacts'** runtime range. Building the whole repository needs JDK 17+
(the demo runs Boot 4 by default).

The same test class, `BootCompatibilityTest`, boots a full container once each on Boot 2.7.18 / 3.4.9 / 4.1.0 and
asserts the upgrade result — conclusions come from actual runs, not inference.

**Explicitly not supported**: automatic downgrades, whole-DDL rollback, per-group target versions.
Rationale in [Known limitations](http://dbupgrade.orivone.com/project/faq.html).

---

## How it works

```
Startup
 └─ load registry → compute target version → compare against dbupgrade_version watermark
      └─ is there a newer version?
           ├─ no  → executed=false, done
           └─ yes → acquire lock (table lock / Redisson / in-process lock)
                └─ per version, per changeset:
                     ├─ look up dbupgrade_record: SUCCESS and checksum unchanged → skip
                     ├─ check whether the DDL object already exists → exists and declared idempotent → skip
                     ├─ mark record EXECUTING → run → on success set SUCCESS
                     └─ on failure → set FAILED, block startup if fail-fast
                └─ all succeeded → advance watermark → release lock
```

The three idempotency layers (record table / DDL existence / changeset declaration), state transitions and what
`checksum` covers are detailed in [Execution and idempotency](http://dbupgrade.orivone.com/reference/execution.html).

---

## Modules

| Module | Responsibility | Dependencies | Bytecode |
| --- | --- | --- | --- |
| `orivone-dbupgrade-core` | The engine: parsing, planning, execution, records, six dialects | `slf4j-api` only (provided) | Java 8 |
| `orivone-dbupgrade-spring-boot-starter` | Boot auto-configuration + `UpgradeRunner` | core + `spring-boot(-autoconfigure)` (provided) | Java 8 |
| `orivone-dbupgrade-lock-redisson` | Optional: Redis distributed lock | core + Redisson | Java 8 |
| `dbupgrade-demo` | Two runnable examples (not a production dependency, not published) | — | Java 17+ |

The three published modules have an empty `src/test`, so the `<dependencies>` block in the POM uploaded to Maven
Central contains only genuine runtime dependencies (core: `slf4j-api` alone). No junit or h2 entries appear there —
test scope is not transitive in the first place, but for a library that advertises "zero framework dependencies",
the POM and the claim ought to match exactly.

Spring Boot projects add `orivone-dbupgrade-spring-boot-starter`; non-Spring code adds `orivone-dbupgrade-core`
directly; add `orivone-dbupgrade-lock-redisson` for the Redis lock. These dependencies **do not include one another** —
the starter's only transitive dependency is core, and all of core's dependencies are `provided`, so nothing drags
Redisson, netty, kryo or a database driver into your project along the way.

Internal package structure and layering are described under
[Module structure](http://dbupgrade.orivone.com/start/quick-start.html).

---

## Documentation

Full documentation: **<http://dbupgrade.orivone.com>** *(currently Chinese only)*

Script syntax, every configuration key, dialect differences across the six databases, lock implementations,
extension points and FAQ all live there. This page only answers "does it fit, and how do I run it"; the documentation
site is authoritative on the details, including where the two disagree.

---

## Demos

The repository ships two runnable examples covering six databases, three Boot versions and two integration styles:

```bash
# Spring Boot integration (H2 in-memory by default, runs with no external setup)
mvn -pl dbupgrade-demo/spring-boot-demo -am spring-boot:run

# Against a real MySQL: edit the URL in application-mysql.yml, then switch profile
mvn -pl dbupgrade-demo/spring-boot-demo -am spring-boot:run -Dspring-boot.run.profiles=mysql
```

For the plain JDBC demo see "Using it without Spring" above; the scripts and scenario list for both demos are in
[Demos](http://dbupgrade.orivone.com/usage/demo.html).

---

## FAQ

**Q: Why not Flyway / Liquibase?**
If you already use one and it works for you, there is no reason to switch. This project targets the case where you
don't want to bring in a whole ecosystem for one job: core has a single `slf4j-api` dependency, and every Spring
dependency in the starter is `provided`.

**Q: What happens if the process is killed mid-upgrade?**
On startup, leftover `EXECUTING` records are reset to `FAILED` and that changeset runs again. Re-running is safe for
DDL, not necessarily for DML: an `INSERT` that was committed before the crash will be applied twice, so seed data
should either be idempotent (delete-then-insert / `MERGE`) or protected by a unique key.

**Q: I edited a historical changeset and startup reports a checksum mismatch.**
The right move is to **add a new changeset** rather than edit history. If the object is "definition is state"
(a view, a stored procedure), give it `runOnChange:true`.

**Q: Several modules use this component — will they interfere?**
No. Each registry has its own `group()`; watermarks and changeset records are isolated per group, so different modules
may even reuse the same `author:id`.

More questions in [Known limitations and FAQ](http://dbupgrade.orivone.com/project/faq.html).

---

## Project status

- **Current version**: `1.0.1`
- **Maintenance**: single maintainer; issues are read, but no response time is promised
- **Stability**: core interfaces (`UpgradeVersionRegistry`, `UpgradeVersion`, SQL script directives) are settled; no breaking changes within `1.x`
- **Tests**: 58 cases in `core`; 5 cases in `demo` run once on each of the Boot 2.7 / 3.x / 4.x lines — all passing
- **Known boundaries**: non-MySQL dialects have no regression against real servers yet; verify per database before production use (see [Known limitations](http://dbupgrade.orivone.com/project/faq.html))

---

## License

[Apache License 2.0](LICENSE), an OSI-approved open source license.

**Adding it to your dependencies completes the grant.** No fee, no registration, no need to contact us, and no
obligation to open source your own code.

**What you need to do, by usage:**

| Your usage | What is required |
| --- | --- |
| Internal systems, on-premise deployments, SaaS offered to customers | Nothing. No limit on nodes, cluster size, QPS or number of end customers |
| Modifying the source for your own use | Nothing |
| Distributing modified code | Note in the modified files that they were changed |
| Bundling into a product you sell | Ship [LICENSE](LICENSE) and [NOTICE](NOTICE) with the product; mention orivone-dbupgrade in your docs or about page |
| Using the `orivone` name or logo in marketing | Not covered by the grant (section 6); do not imply official endorsement |

Beyond that table there are no further conditions: no node limits, no "commercial use requires payment", no separate
supplementary agreement.

**What you get:**

- **An explicit patent grant.** Under section 3, contributors have already granted you the relevant patent rights, and those rights are not revoked unless you initiate patent litigation.
- **Terms that don't tighten over time.** The whole `1.x` line stays Apache-2.0: whatever is permitted today remains permitted in future releases.
- **No features locked behind payment.** What is in the repository is what ships in the artifact.
- **You can audit it yourself.** core's only compile-time dependency is `slf4j-api`, and the published artifacts bundle no third-party code; the full dependency and license list is in [NOTICE](NOTICE).

**What it does not provide:** per sections 7 and 8, the software is provided "as is" — without warranty, and the
author is not liable for consequences of use. Please run your own tests and validation in production.

---

<p align="center"><sub>Small by design, stable by default, programmable where you need it.</sub></p>
