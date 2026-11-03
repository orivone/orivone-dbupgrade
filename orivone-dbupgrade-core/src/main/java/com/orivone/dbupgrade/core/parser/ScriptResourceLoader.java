package com.orivone.dbupgrade.core.parser;

import com.orivone.dbupgrade.core.StrUtils;
import com.orivone.dbupgrade.core.UpgradeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 脚本加载器，把注册表里写的路径读成字符串。
 * <p>
 * 四种写法：
 * <ul>
 *     <li>{@code classpath:xxx}，或干脆不写前缀——走 classpath，IDE 目录与 jar 内资源都认</li>
 *     <li>{@code file:/abs/path}——文件系统绝对路径</li>
 *     <li>{@code http(s)://...}——远程脚本，<b>默认禁止</b>，需显式开启 {@code allowRemote}</li>
 * </ul>
 * 远程脚本落在一个能连生产库的进程里，等于把「执行任意 SQL」的开关交给了那个 URL：
 * 断网、DNS 劫持、仓库被改、CDN 返回一个 302 到别处，都能让升级流程跑出没人审过的 DDL。
 * 所以默认只认本地资源，确实需要时才由使用方显式打开。
 * <p>
 * 只用 JDK 自带的 {@code ClassLoader#getResource} 和 {@link URL#openStream()}，
 * 不引 Spring，也不带任何第三方依赖。
 * @author orivone
 */
public class ScriptResourceLoader {

    private static final Logger log = LoggerFactory.getLogger(ScriptResourceLoader.class);

    private static final String CLASSPATH_PREFIX = "classpath:";

    private final ClassLoader classLoader;

    /**
     * 是否允许从 http(s) 加载脚本，默认 false
     */
    private final boolean allowRemote;

    public ScriptResourceLoader() {
        this(defaultClassLoader(), false);
    }

    /**
     * @param classLoader 类加载器，null 表示用线程上下文类加载器
     */
    public ScriptResourceLoader(ClassLoader classLoader) {
        this(classLoader, false);
    }

    /**
     * @param classLoader 类加载器，null 表示用线程上下文类加载器
     * @param allowRemote 是否允许 http(s) 远程脚本
     */
    public ScriptResourceLoader(ClassLoader classLoader, boolean allowRemote) {
        this.classLoader = classLoader == null ? defaultClassLoader() : classLoader;
        this.allowRemote = allowRemote;
    }

    /**
     * 脚本是否存在。
     * <p>
     * 路径本身不合法（比如远程脚本被禁用）属于配置错误，直接抛出而不是返回 false——
     * 否则调用方只会看到「文件不存在」，得排查半天才知道是开关没开。
     */
    public boolean exists(String path) {
        if (StrUtils.isBlank(path)) {
            return false;
        }
        try {
            return resolve(path) != null;
        } catch (UpgradeException e) {
            throw e;
        } catch (Exception e) {
            log.debug("检测升级脚本[{}]是否存在时异常：{}", path, e.getMessage());
            return false;
        }
    }

    /**
     * 读取脚本内容（UTF-8）
     */
    public String read(String path) {
        URL url;
        try {
            url = resolve(path);
        } catch (Exception e) {
            throw new UpgradeException("升级脚本定位失败：" + path + "；原因：" + e.getMessage(), e);
        }
        if (url == null) {
            throw new UpgradeException("升级脚本文件不存在：" + path);
        }
        try (InputStream in = url.openStream()) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UpgradeException("升级脚本文件读取失败：" + path + "；原因：" + e.getMessage(), e);
        }
    }

    /**
     * 扫描脚本根目录下所有 .sql 文件，返回相对该目录的路径（去重、保持发现顺序）。
     * <p>
     * 必须返回相对路径而不是文件名：{@code mysql/upgrade/a.sql} 与 {@code oracle/upgrade/a.sql}
     * 文件名相同，只比文件名会把「已声明」误判成「存在但未声明」，反过来也会漏掉真正没声明的文件。
     * <p>
     * 仅用于提示「存在但未在注册表声明」的脚本，不参与执行。
     *
     * @param rootDir classpath 相对目录，如 upgrade-scripts
     */
    public List<String> listSqlPaths(String rootDir) {
        Set<String> paths = new LinkedHashSet<>();
        if (StrUtils.isBlank(rootDir)) {
            return new ArrayList<>(paths);
        }
        String base = normalize(rootDir);
        try {
            Enumeration<URL> roots = classLoader.getResources(base);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                if ("file".equals(url.getProtocol())) {
                    collectFromDirectory(new File(url.toURI()), "", paths);
                } else if ("jar".equals(url.getProtocol())) {
                    collectFromJar(url, base, paths);
                }
            }
        } catch (Exception e) {
            log.debug("扫描升级脚本目录[{}]失败：{}", base, e.getMessage());
        }
        return new ArrayList<>(paths);
    }

    /**
     * 定位脚本，返回 null 表示不存在
     */
    private URL resolve(String location) throws IOException {
        String text = StrUtils.strip(location);
        if (text.startsWith(CLASSPATH_PREFIX)) {
            return classLoader.getResource(normalize(text.substring(CLASSPATH_PREFIX.length())));
        }
        if (text.startsWith("file:")) {
            File file = new File(URI.create(text));
            return file.exists() ? file.toURI().toURL() : null;
        }
        if (text.startsWith("http:") || text.startsWith("https:")) {
            if (!allowRemote) {
                throw new UpgradeException("升级脚本[" + text + "]是远程地址，默认禁止加载；"
                        + "远程脚本会在目标库上执行任意 SQL，确认来源可信后把 allow-remote-scripts 置为 true 再试");
            }
            URL url = URI.create(text).toURL();
            return remoteExists(url) ? url : null;
        }
        return classLoader.getResource(normalize(text));
    }

    private static boolean remoteExists(URL url) {
        try {
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            if (connection instanceof java.net.HttpURLConnection) {
                java.net.HttpURLConnection http = (java.net.HttpURLConnection) connection;
                http.setRequestMethod("HEAD");
                return http.getResponseCode() < 400;
            }
            return true;
        } catch (Exception e) {
            log.debug("远程脚本[{}]探测失败：{}", url, e.getMessage());
            return false;
        }
    }

    /**
     * 递归收集目录下的 .sql，{@code prefix} 是相对根目录的已走过路径（以 / 结尾）
     */
    private static void collectFromDirectory(File directory, String prefix, Set<String> paths) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectFromDirectory(child, prefix + child.getName() + "/", paths);
            } else if (child.getName().endsWith(".sql")) {
                paths.add(prefix + child.getName());
            }
        }
    }

    private static void collectFromJar(URL url, String base, Set<String> paths) {
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            connection.setUseCaches(false);
            JarFile jarFile = connection.getJarFile();
            String prefix = base.endsWith("/") ? base : base + "/";
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(".sql")) {
                    paths.add(name.substring(prefix.length()));
                }
            }
        } catch (Exception e) {
            log.debug("扫描 jar 内脚本目录[{}]失败：{}", base, e.getMessage());
        }
    }

    /**
     * 统一路径：反斜杠转正斜杠、去掉前缀斜杠与末尾斜杠
     */
    private static String normalize(String path) {
        String normalized = StrUtils.strip(path).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Java 8 没有 {@code InputStream#readAllBytes}，这里手写一份
     */
    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static ClassLoader defaultClassLoader() {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        return contextLoader == null ? ScriptResourceLoader.class.getClassLoader() : contextLoader;
    }
}
