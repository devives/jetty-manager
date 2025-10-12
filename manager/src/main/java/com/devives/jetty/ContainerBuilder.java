package com.devives.jetty;

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

public class ContainerBuilder {
    private final ClassLoader parentClassLoader_;
    private final Path jettyHome_;
    private Path jettyBase_;
    private Path includeJettyDir_;
    private Integer httpPort_;
    private Integer httpsPort_;
    private Integer stopPort_;
    private String stopKey_;
    private Integer stopTimeoutSeconds_;
    private final Map<String, String> argumentMap_ = new HashMap<>();
    private final Set<URL> startJarSet_ = new HashSet<>();
    private final Set<Path> librarySet_ = new HashSet<>();

    public ContainerBuilder(ClassLoader parentClassLoader, Path jettyHome) {
        parentClassLoader_ = Objects.requireNonNull(parentClassLoader);
        jettyHome_ = Objects.requireNonNull(jettyHome);
    }

    public ContainerBuilder addStartJar(Path jarPath) {
        startJarSet_.add(ExceptionUtils.passChecked(Objects.requireNonNull(jarPath, "jarPath").toUri()::toURL));
        return this;
    }

    public ContainerBuilder addLibrary(Path jarPath) {
        librarySet_.add(Objects.requireNonNull(jarPath, "jarPath"));
        return this;
    }

    public ContainerBuilder setIncludeJettyDir(Path includeJettyDir) {
        includeJettyDir_ = includeJettyDir;
        return this;
    }

    public ContainerBuilder setJettyBase(Path jettyBase) {
        jettyBase_ = jettyBase;
        return this;
    }

    public ContainerBuilder setHttpPort(Integer httpPort) {
        httpPort_ = httpPort;
        return this;
    }

    public ContainerBuilder setHttpsPort(Integer httpsPort) {
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

    public ContainerBuilder setStopKey(String stopKey) {
        stopKey_ = stopKey;
        return this;
    }

    public ContainerBuilder setStopTimeout(int timeoutSeconds) {
        stopTimeoutSeconds_ = timeoutSeconds;
        return this;
    }

    public ContainerBuilder addStartArgument(String name, String value) {
        argumentMap_.put(normalizeArgName(name), value);
        return this;
    }

    public ContainerBuilder addStartArgument(String name) {
        argumentMap_.put(normalizeArgName(name), null);
        return this;
    }

    public Container build() {
        URLClassLoader classLoader = buildJettyClassLoader(jettyHome_);
        String[] args = buildStartArgs();
        Container container = new Container(classLoader, args);
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
