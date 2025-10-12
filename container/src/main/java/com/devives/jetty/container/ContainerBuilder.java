package com.devives.jetty.container;

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Строитель контейнера Jetty-сервера.
 *
 * @see <a href="https://jetty.org/docs/jetty/12.1/operations-guide/start/index.html"></a>
 */
public class ContainerBuilder {
    private final ClassLoader parentClassLoader_;
    private final Path jettyHome_;
    private Path jettyBase_;
    private Path includeJettyDir_;
    private Integer httpPort_ = 8080;
    private Integer httpsPort_ = 8443;
    private Integer stopPort_;
    private String stopKey_;
    private Long stopTimeoutSeconds_;
    private final Map<String, String> argumentMap_ = new HashMap<>();
    private final Set<URL> startJarSet_ = new HashSet<>();
    private final Set<Path> librarySet_ = new HashSet<>();

    /**
     * Создаёт экземпляр строителя контейнера Jetty-сервера.
     *
     * @param parentClassLoader родительский загрузчик классов для загрузчика стартовой библиотеки `jetty-home/start.jar`.
     * @param jettyHome         путь к домашнему каталогу Jetty (каталог с дистрибутивом).
     */
    public ContainerBuilder(ClassLoader parentClassLoader, Path jettyHome) {
        parentClassLoader_ = Objects.requireNonNull(parentClassLoader);
        jettyHome_ = Objects.requireNonNull(jettyHome);
    }

    /**
     * Добавляет библиотеку в стартовый загрузчик классов Jetty (где находится `{JETTY_HOME}/start.jar`).
     *
     * @param jarPath путь к jar-файлу.
     * @return текущий строитель.
     */
    public ContainerBuilder addStartJar(Path jarPath) {
        Objects.requireNonNull(jarPath, "jarPath");
        startJarSet_.add(ExceptionUtils.passChecked(jarPath.toUri()::toURL));
        return this;
    }

    /**
     * Добавляет библиотеку в основной загрузчик классов Jetty (где находится библиотеки `{JETTY_HOME}/lib/*`).
     *
     * @param jarPath путь к jar-файлу.
     * @return текущий строитель.
     */
    public ContainerBuilder addLibrary(Path jarPath) {
        Objects.requireNonNull(jarPath, "jarPath");
        librarySet_.add(jarPath);
        return this;
    }

    /**
     * Устанавливает значение параметра запуска "{@code --include-jetty-dir=<path>}".
     * <p>
     * Includes the specified directory as a configuration source.
     * This directory behaves similarly to ${jetty.base} but sits
     * at a layer between ${jetty.home} and ${jetty.base}.
     * Useful when you want to apply a common "corporate"
     * configuration to all specific ${jetty.base} directories
     * without having to modify ${jetty.home}.
     *
     * @param includeJettyDir путь к jar-файлу.
     * @return текущий строитель.
     */
    public ContainerBuilder setIncludeJettyDir(Path includeJettyDir) {
        Objects.requireNonNull(includeJettyDir, "includeJettyDir");
        includeJettyDir_ = includeJettyDir;
        return this;
    }

    /**
     * @param jettyBase путь к jar-файлу.
     * @return текущий строитель.
     */
    public ContainerBuilder setJettyBase(Path jettyBase) {
        Objects.requireNonNull(jettyBase, "jettyBase");
        jettyBase_ = jettyBase;
        return this;
    }

    /**
     * Устанавливает HTTP-порт.
     * <p>
     * По умолчанию: 8080
     *
     * @param httpPort номер порта.
     * @return текущий строитель.
     */
    public ContainerBuilder setHttpPort(int httpPort) {
        httpPort_ = httpPort;
        return this;
    }

    /**
     * Устанавливает HTTP-порт.
     * <p>
     * По умолчанию: 8443
     *
     * @param httpsPort номер порта.
     * @return текущий строитель.
     */
    public ContainerBuilder setHttpsPort(int httpsPort) {
        httpsPort_ = httpsPort;
        return this;
    }

    /**
     * Устанавливает порт сокета, для прослушивания команды остановки.
     * <ul>
     *     <li> Значение не установлено - сокет не запускается.
     *     <li> "{@code stopPort < 0}" - Jetty9: сокет не запускается. Jetty12: ошибка.
     *     <li> "{@code stopPort == 0}" - сокет запускается на произвольном порту.
     *     <li> "{@code stopPort > 0}" - сокет запускается на указанном порту.
     * </ul>
     *
     * @param stopPort номер порта
     * @return текущий строитель.
     */
    public ContainerBuilder setStopPort(int stopPort) {
        stopPort_ = stopPort;
        return this;
    }

    /**
     * Устанавливает значение ключа остановки сервера Jetty через сокет, открытый на порту {@link #setStopPort(int)}.
     *
     * @param stopKey значение ключа.
     * @return текущий строитель.
     */
    public ContainerBuilder setStopKey(String stopKey) {
        stopKey_ = stopKey;
        return this;
    }

    /**
     * Устанавливает таймаут ожидания остановки Jetty-сервера после отправки команды остановки а сокет.
     *
     * @param timeoutSeconds таймаут, в миллисекундах.
     * @return текущий строитель.
     */
    public ContainerBuilder setStopTimeout(long timeoutSeconds) {
        stopTimeoutSeconds_ = timeoutSeconds;
        return this;
    }

    /**
     * @param name
     * @param value
     * @return текущий строитель.
     */
    public ContainerBuilder addStartArgument(String name, String value) {
        argumentMap_.put(normalizeArgName(name), value);
        return this;
    }

    /**
     * @param name
     * @return текущий строитель.
     */
    public ContainerBuilder addStartArgument(String name) {
        argumentMap_.put(normalizeArgName(name), null);
        return this;
    }

    /**
     * Строит новый экземпляр {@link Container}.
     *
     * @return новый экземпляр {@link Container}.
     */
    public Container build() {
        URLClassLoader classLoader = buildJettyClassLoader(jettyHome_);
        String[] args = buildStartArgs();
        Container container = new Container(classLoader, args);
        Optional.ofNullable(httpPort_).ifPresent(container::setHttpPort);
        Optional.ofNullable(httpsPort_).ifPresent(container::setHttpsPort);
        Optional.ofNullable(stopPort_).ifPresent(container::setStopPort);
        Optional.ofNullable(stopKey_).ifPresent(container::setStopKey);
        Optional.ofNullable(stopTimeoutSeconds_).ifPresent(container::setStopTimeout);
        return container;
    }

    private String[] buildStartArgs() {
        Map<String, String> startArgMap = new LinkedHashMap<>(argumentMap_);
        startArgMap.put("jetty.home", jettyHome_.toAbsolutePath().toString());
        if (jettyBase_ != null) {
            startArgMap.put("jetty.base", jettyBase_.isAbsolute()
                    ? jettyBase_.toString()
                    : jettyHome_.resolve(jettyBase_).toAbsolutePath().toString());
        }
        if (httpPort_ != null) {
            startArgMap.put("jetty.http.port", httpPort_.toString());
        }
        if (httpsPort_ != null) {
            startArgMap.put("jetty.ssl.port", httpsPort_.toString());
        }
        if (includeJettyDir_ != null) {
            startArgMap.put("--include-jetty-dir", includeJettyDir_.toString());
        }
        if (!librarySet_.isEmpty()) {
            startArgMap.put("--lib", librarySet_.stream().map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        }
        return startArgMap
                .entrySet().stream()
                .map(entry -> entry.getValue() == null
                        ? entry.getKey()
                        : entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private String normalizeArgName(String name) {
        StringUtils.requireNonEmpty(name);
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("The argument name cannot consist only of whitespaces.");
        }
        return trimmedName.toLowerCase();
    }

    private URLClassLoader buildJettyClassLoader(Path jettyHome) {
        try {
            if (!Files.exists(jettyHome)) {
                throw new IOException(String.format("Directory '%s' is not exits.", jettyHome.toAbsolutePath()));
            }
            List<URL> urls = new ArrayList<>();
            Path startJarPath = jettyHome.resolve("start.jar");
            if (!Files.exists(startJarPath)) {
                throw new IOException(String.format("File '%s' is not exits.", startJarPath.toAbsolutePath()));
            }
            urls.add(startJarPath.toUri().toURL());
            urls.addAll(startJarSet_);
            return new URLClassLoader(urls.toArray(new URL[0]), parentClassLoader_);
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

}
