package com.devives.jetty;

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.StringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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

    public ContainerBuilder(ClassLoader parentClassLoader, Path jettyHome) {
        parentClassLoader_ = Objects.requireNonNull(parentClassLoader);
        jettyHome_ = Objects.requireNonNull(jettyHome);
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

    public ContainerBuilder setStopPort(Integer stopPort) {
        stopPort_ = stopPort;
        return this;
    }

    public ContainerBuilder setStopKey(String stopKey) {
        stopKey_ = stopKey;
        return this;
    }

    public ContainerBuilder setStopTimeout(Integer timeoutSeconds) {
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
        container.setStopPort(stopPort_);
        container.setStopKey(stopKey_);
        container.setStopTimeout(stopTimeoutSeconds_);
        return container;
    }

    private String[] buildStartArgs() {
        Map<String, String> startArgMap = new HashMap<>(argumentMap_);
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
        if (!Files.exists(jettyHome)) {
            throw ExceptionUtils.asUnchecked(new IOException(String.format("Directory '%s' is not exits.", jettyHome.toAbsolutePath())));
        }
        Path startJarPath = jettyHome.resolve("start.jar");
        if (!Files.exists(startJarPath)) {
            throw ExceptionUtils.asUnchecked(new IOException(String.format("File '%s' is not exits.", startJarPath.toAbsolutePath())));
        }
        try {
            return new URLClassLoader(new URL[]{startJarPath.toUri().toURL()}, parentClassLoader_);
        } catch (MalformedURLException e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

}
