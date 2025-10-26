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
import com.devives.jetty.container.contract.ContainerContext;
import com.devives.jetty.container.contract.Main;
import com.devives.jetty.container.contract.ShutdownMonitor;
import com.devives.jetty.container.contract.ShutdownService;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Причины запуска Jetty через рефлекшен:
 * 1. Нет необходимости добавлять зависимость от библиотек Jetty.
 * 2. При запуске от jetty.start.Main происходит парсинг параметров командной строки,
 * и обработка всех xml-конфигов jetty. Если запускать embedded jetty, создавая
 * экземпляр jetty.server.Server, будут проигнорированы все конфиги.
 */
public final class Container {

    /**
     * Блокировка решает проблему.
     * <p>
     * При запуске экземпляра Jetty создаётся статический экземпляр {@code org.eclipse.jetty.server.ShutdownMonitor#ShutdownMonitor}.
     * В конструкторе считываются переменные окружения:
     * <pre>{@code
     *   this.debug = System.getProperty("DEBUG") != null;
     *   this.host = System.getProperty("STOP.HOST", "127.0.0.1");
     *   this.port = Integer.getInteger(System.getProperty("STOP.PORT", 0));
     *   this.key = System.getProperty("STOP.KEY", null);
     *   //only change the default exitVm setting if STOP.EXIT is explicitly set
     *   this.exitVm = Boolean.valueOf(System.getProperty("STOP.EXIT", "true"));
     * }</pre>
     * Т.к. переменные окружения {@link System#getProperty(String)} едины для всех потоков.
     * При одновременном запуске нескольких Jetty, значение "STOP.PORT" для всех потоков будет одинаково.
     * Что приведёт к ошибке открытия сокета во всех экземпляра кроме первого.
     */
    private static final Object ENV_VARIABLES_LOCK = new Object();

    private final ClassLoader classLoader_;
    private final ContainerProperties properties_;
    private final String[] startArgs_;
    private volatile ServerAdapter serverAdapter_;
    private volatile CompletableFuture<Void> stopFuture_;
    /**
     * Actual stop port after container start.
     */
    private volatile Integer stopPort_;
    /**
     * Actual stop key after container start.
     */
    private volatile String stopKey_;

    public Container(ClassLoader classLoader, ContainerProperties properties, String[] startArgs) {
        classLoader_ = Objects.requireNonNull(classLoader);
        properties_ = Objects.requireNonNull(properties);
        startArgs_ = Objects.requireNonNull(startArgs);
    }

    /**
     * Флаг указывает на необходимость вызова {@link System#exit(int)} после остановки Jetty-сервера.
     *
     * @return true, если вызывать, иначе false.
     */
    public boolean isExitVmOnStop() {
        return properties_.isExitVmOnStop();
    }

    /**
     * Возвращает номер HTTP-порта.
     * <p>
     * Наличие значение порта, не означает, что коннектор запущен.
     *
     * @return номер порта.
     */
    public Integer getHttpPort() {
        return properties_.getHttpPort();
    }

    /**
     * Возвращает номер HTTPS-порта.
     * <p>
     * Наличие значение порта, не означает, что SSL-коннектор запущен.
     *
     * @return номер порта.
     */
    public Integer getHttpsPort() {
        return properties_.getHttpsPort();
    }

    /**
     * Return actual stop port after container start.
     *
     * @return номер порта.
     */
    public Integer getStopPort() {
        return stopPort_;
    }

    /**
     * Return actual stop key after container start.
     *
     * @return значение ключа.
     */
    public String getStopKey() {
        return stopKey_;
    }

    /**
     * @return таймаут, секунды.
     */
    public Long getStopTimeout() {
        return properties_.getStopTimeout();
    }

    /**
     * Запускает контейнер и содержащийся в нём Jetty-сервер.
     */
    public synchronized void start() {
        if (serverAdapter_ != null) {
            throw new ContainerException("Server has been started already.");
        }
        final ServerAdapter serverAdapter = new ServerAdapter();
        synchronized (ENV_VARIABLES_LOCK) {
            ContainerContext.Static context = ContainerContext.Static.at(classLoader_);
            // Установка ссылки на листенер необходима только в момент запуска Jetty.
            context.setServerEventListener(serverAdapter.getServerEventListener());
            try {
                try (AutoCloseable ignore = registerShutdownMonitorEnvVariables()) {
                    Main.Static.at(classLoader_).main(startArgs_);
                } catch (Exception e) {
                    throw ExceptionUtils.asUnchecked(e);
                }
            } finally {
                context.setServerEventListener(null);
            }
        }
        setupStopPortAndKey(serverAdapter);
        final ClassLoader serverClassLoader = serverAdapter.getClassLoader();
        serverAdapter.getStopFuture().thenAccept((x) -> {
            serverAdapter_ = null;
            final CompletableFuture<Void> stopFuture = stopFuture_;
            if (stopFuture != null) {
                try {
                    stopFuture.complete(null);
                } finally {
                    stopFuture_ = null;
                }
            }
            ExceptionUtils.collectAndThrow(
                    ((Closeable) serverClassLoader)::close,
                    ((Closeable) classLoader_)::close
            );
        });
        serverAdapter_ = serverAdapter;
    }

    /**
     * Получает актуальные порт и ключ остановки из запущенного Jetty-сервера.
     *
     * @param serverAdapter адаптер сервера
     */
    private void setupStopPortAndKey(ServerAdapter serverAdapter) {
        boolean isSetup = false;
        try {
            // Since Jetty 12
            for (ShutdownService shutdownService : serverAdapter.getBeans(ShutdownService.class)) {
                stopPort_ = shutdownService.getLocalPort();
                stopKey_ = shutdownService.getKey();
                isSetup = true;
            }
        } catch (ClassNotFoundException e) {
            isSetup = false;
        }
        if (!isSetup) {
            // Old style Jetty 9.x
            ShutdownMonitor shutdownMonitor = ShutdownMonitor.Static.at(serverAdapter.getClassLoader()).getInstance();
            stopPort_ = shutdownMonitor.getPort();
            stopKey_ = shutdownMonitor.getKey();
        }
    }

    /**
     * Останавливает Jetty-сервер асинхронно.
     * <p>
     * Метод вызывает {@code org.eclipse.jetty.start.Main#main(String [])} с параметрами остановки сервера.
     * На порт сокета остановки Jetty-сервера отправляется команда остановки.
     * <p>
     * Для возможности использования этого метода, при запуске Jetty-сервера должен быть указано значение номера
     * порта для сокета остановки.
     * @return фьючерс завершения остановки.
     * @see ContainerBuilder#setStopPort(int)
     */
    public synchronized Future<Void> stopAsync() {
        CompletableFuture<Void> stopFuture = stopFuture_;
        if (stopFuture == null) {
            stopFuture = new CompletableFuture<>();
            stopFuture_ = stopFuture;
            String[] args = buildJettyStopAsyncArgs();
            // Метод будет ожидать получения уведомления об остановке сервера (по Сокету) в течении stopTimeoutSeconds_ секунд.
            Main.Static.at(classLoader_).main(args);
        }
        return stopFuture;
    }

    /**
     * Останавливает Jetty-сервер синхронно.
     */
    public synchronized void stop() {
        ((ServerAdapter) getServer()).stop();
    }

    /**
     * Возвращает
     *
     * @return Адаптер Jetty-сервера.
     */
    public Server getServer() {
        Server result = serverAdapter_;
        if (result == null) {
            throw new ContainerException("Server is not started.");
        }
        return result;
    }

    /**
     * @see "org.eclipse.jetty.server.ShutdownMonitor"
     */
    private AutoCloseable registerShutdownMonitorEnvVariables() {
        if (properties_.getStopPort() != null) {
            System.setProperty("STOP.PORT", String.valueOf(properties_.getStopPort()));
        }
        if (properties_.getStopKey() != null) {
            System.setProperty("STOP.KEY", properties_.getStopKey());
        }
        System.setProperty("STOP.EXIT", Boolean.toString(properties_.isExitVmOnStop()));
        return () -> {
            if (properties_.getStopPort() != null) {
                System.clearProperty("STOP.PORT");
            }
            if (properties_.getStopKey() != null) {
                System.clearProperty("STOP.KEY");
            }
            System.clearProperty("STOP.EXIT");
        };
    }

    private String[] buildJettyStopAsyncArgs() {
        List<String> args = new ArrayList<>();
        if (stopPort_ == null || stopPort_ < 0) {
            throw new ContainerException("Can't stop server asynchronously. Stop port not set.");
        }
        args.add("STOP.PORT=" + stopPort_);
        args.add("STOP.KEY=" + stopKey_);
        if (properties_.getStopTimeout() != null && properties_.getStopTimeout() > 0) {
            args.add("STOP.WAIT=" + properties_.getStopTimeout());
        }
        args.add("--stop");
        return args.toArray(new String[0]);
    }

}
