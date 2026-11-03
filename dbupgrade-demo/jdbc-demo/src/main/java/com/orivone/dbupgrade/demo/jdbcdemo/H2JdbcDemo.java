package com.orivone.dbupgrade.demo.jdbcdemo;

/**
 * 【jdbc-demo】H2 版，其余五个数据库示例的样板。
 * <p>
 * 内存库，不依赖任何外部服务：在 IDE 里直接运行 main，或者
 * <pre>
 *   mvn -pl dbupgrade-demo/jdbc-demo exec:java -Dexec.mainClass=com.orivone.dbupgrade.demo.jdbcdemo.H2JdbcDemo
 * </pre>
 * 就能把一次完整升级看全。{@link JdbcDemoSupport} 会连着跑两次 ——
 * 第二次版本号没动、也没有任何 changeset 被执行，这就是幂等。
 * <p>
 * 连接串里的 {@code DB_CLOSE_DELAY=-1} 不能省：框架一个 changeset 用一个连接，
 * 内存库如果在最后一个连接关闭时被销毁，下一个 changeset 会看到一个空库。
 * <p>
 * 换成自己的库不必改代码：
 * <pre>
 *   java -Ddb.url="jdbc:h2:file:./data/demo" -Ddb.username=sa -Ddb.password= \
 *        -cp "target/classes:&lt;deps&gt;" com.orivone.dbupgrade.demo.jdbcdemo.H2JdbcDemo
 * </pre>
 * 其余五个（mysql / postgresql / oracle / dm / sqlserver）与这里只差类名、默认连接串与注册表：
 * 它们共用 {@link JdbcDemoRegistry} 的两个版本，这里用 {@link H2JdbcDemoRegistry} 多演示一个版本。
 * 脚本各自放在 {@code upgrade-scripts/{db}/} 下。
 * @author orivone
 */
public final class H2JdbcDemo {

    private H2JdbcDemo() {
    }

    public static void main(String[] args) {
        JdbcDemoSupport.run("h2",
                "jdbc:h2:mem:jdbc_upgrade_demo;DB_CLOSE_DELAY=-1",
                "sa", "", new H2JdbcDemoRegistry());
    }
}
