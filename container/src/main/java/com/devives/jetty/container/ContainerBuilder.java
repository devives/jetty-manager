/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.devives.jetty.container;

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.Manifest;
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
    private Path containerTempDir_;
    private Integer httpPort_ = 8080;
    private Integer httpsPort_ = 8443;
    private Integer stopPort_;
    private String stopKey_;
    private Long stopTimeoutSeconds_ = 15L;
    private boolean injectionsEnabled_ = true;
    private Version injectionsVersion_ = Version.Auto;
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

    public ContainerBuilder setTempPath(Path tempPath) {
        Objects.requireNonNull(tempPath, "tempPath");
        containerTempDir_ = tempPath;
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
     * Добавляет аргумент запуска Jetty-сервера со значением.
     *
     * @param name  имя аргумента.
     * @param value значение аргумента.
     * @return текущий строитель.
     */
    public ContainerBuilder addStartArgument(String name, String value) {
        argumentMap_.put(normalizeArgName(name), value);
        return this;
    }

    /**
     * Добавляет аргумент запуска Jetty-сервера.
     *
     * @param name имя аргумента
     * @return текущий строитель.
     */
    public ContainerBuilder addStartArgument(String name) {
        argumentMap_.put(normalizeArgName(name), null);
        return this;
    }

    /**
     * Отключает автоматическое добавление инъекций, связывающих экземпляр Jetty-сервера с управляющим контейнером,
     * в загрузчики Jetty-классов. Если отключить добавление инъекций, добавление конфигурационного xml-файла
     * и jar-файла с иньекцией, необходимо через аргументы запуска контейнера.
     *
     * @return текущий строитель.
     */
    public ContainerBuilder disableInjections() {
        injectionsEnabled_ = false;
        return this;
    }

    /**
     * Устанавливает версию инжектируемых ресурсов, которые необходимо использовать.
     * <p>
     * Инжектируемые jar-файлы и xml-файлы конфигурации содержатся в ресурсах текущего jar-файла:
     * <ul>
     *     <li>{@code com/devives/jetty/container/lib/container-context-0.1.0.jar}
     *     <li>{@code com/devives/jetty/container/jetty9/etc/container-injection.xml}
     *     <li>{@code com/devives/jetty/container/jetty9/lib/devive-jetty9-container-injection-0.1.0.jar}
     *     <li>{@code com/devives/jetty/container/jetty12/etc/container-injection.xml}
     *     <li>{@code com/devives/jetty/container/jetty12/lib/devive-jetty12-container-injection-0.1.0.jar}
     * </ul>
     *
     * @param version новое значение.
     * @return текущий строитель.
     */
    public ContainerBuilder setInjectionsVersion(Version version) {
        injectionsVersion_ = version;
        return this;
    }

    /**
     * Строит новый экземпляр {@link Container}.
     *
     * @return новый экземпляр {@link Container}.
     */
    public Container build() {
        try {
            validateJettyHome(jettyHome_);
            if (injectionsEnabled_) {
                int version = injectionsVersion_ == Version.Auto
                        ? determineJettyMajorVersion(jettyHome_)
                        : injectionsVersion_.getVersion();
                prepareContainerResources(version);
            }
            String[] args = buildStartArgs();
            URLClassLoader classLoader = buildJettyClassLoader(jettyHome_);
            ContainerProperties properties = new ContainerProperties();
            Optional.ofNullable(httpPort_).ifPresent(properties::setHttpPort);
            Optional.ofNullable(httpsPort_).ifPresent(properties::setHttpsPort);
            Optional.ofNullable(stopPort_).ifPresent(properties::setStopPort);
            Optional.ofNullable(stopKey_).ifPresent(properties::setStopKey);
            Optional.ofNullable(stopTimeoutSeconds_).ifPresent(properties::setStopTimeout);
            Container container = new Container(classLoader, properties, args);
            return container;
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }

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

    private void validateJettyHome(Path jettyHome) {
        try {
            if (!Files.exists(jettyHome)) {
                throw new IOException(String.format("Directory '%s' is not exits.", jettyHome.toAbsolutePath()));
            }
            Path startJarPath = jettyHome.resolve("start.jar");
            if (!Files.exists(startJarPath)) {
                throw new IOException(String.format("File '%s' is not exits.", startJarPath.toAbsolutePath()));
            }
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    private void prepareContainerResources(int jettyVersion) throws IOException {

        Path tempDir = containerTempDir_ != null ? containerTempDir_ : Paths.get(System.getProperty("java.io.tmpdir"));
        Path tempPath = Files.createTempDirectory(tempDir, "jetty-container-");
        tempPath.toFile().deleteOnExit();

        String jarName = "devive-jetty-container-context.jar";
        Path dest = tempPath.resolve(jarName);
        copyResource("lib/" + jarName, dest);
        dest.toFile().deleteOnExit();
        addStartJar(dest);

        jarName = "devive-jetty" + jettyVersion + "-container-injection.jar";
        dest = tempPath.resolve(jarName);
        copyResource("jetty" + jettyVersion + "/lib/" + jarName, dest);
        dest.toFile().deleteOnExit();
        addLibrary(dest);

        dest = tempPath.resolve("container-injection.xml");
        copyResource("jetty" + jettyVersion + "/etc/container-injection.xml", dest);
        dest.toFile().deleteOnExit();
        addStartArgument(dest.toString());

    }

    private void copyResource(String name, Path dest) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private URLClassLoader buildJettyClassLoader(Path jettyHome) throws IOException {
        List<URL> urls = new ArrayList<>();
        Path startJarPath = jettyHome.resolve("start.jar");
        urls.add(startJarPath.toUri().toURL());
        urls.addAll(startJarSet_);
        return new URLClassLoader(urls.toArray(new URL[0]), parentClassLoader_);
    }

    private static int determineJettyMajorVersion(Path jettyHome) throws Exception {
        Path startJarPath = jettyHome.resolve("start.jar");
        List<URL> urls = new ArrayList<>();
        urls.add(startJarPath.toUri().toURL());
        try (URLClassLoader urlClassLoader = new URLClassLoader(urls.toArray(new URL[0]), null)) {
            URL manifestUrl = urlClassLoader.findResource("META-INF/MANIFEST.MF");
            try (InputStream in = manifestUrl.openStream()) {
                Manifest manifest = new Manifest(in);
                String sVersion = manifest.getMainAttributes().getValue("Implementation-Version");
                return Integer.parseInt(sVersion.split("\\.", 2)[0]);
            }
        }
    }

}
