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
import com.devives.commons.lang.call.Try;
import com.devives.commons.lang.function.FailableConsumer;
import com.devives.commons.lang.ref.Ref;
import com.devives.commons.lifecycle.AbstractLifeCycle;
import com.devives.commons.lifecycle.FailureAction;
import com.devives.commons.lifecycle.LifeCycle;
import com.devives.commons.lifecycle.LifeCycleStates;
import com.devives.jetty.container.contract.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Причины запуска Jetty через рефлекшен:
 * 1. Нет необходимости добавлять зависимость от библиотек Jetty.
 * 2. При запуске от jetty.start.Main происходит парсинг параметров командной строки,
 * и обработка всех xml-конфигов jetty. Если запускать embedded jetty, создавая
 * экземпляр jetty.server.Server, будут проигнорированы все конфиги.
 */
public final class Container extends AbstractLifeCycle implements AutoCloseable {

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

    private final int version_;
    private final ClassLoader classLoader_;
    private final ContainerProperties properties_;
    private final String[] startArgs_;
    private final FailableConsumer<Container> closeCallback_;
    private volatile ServerAdapter serverAdapter_;
    private volatile CompletableFuture<Void> stopFuture_;
    /**
     * Флаг: получен первый lifecycle-евент Jetty-сервера ({@code handleStarting}),
     * т.е. сервер сконструирован и классы из start-classloader используются живым сервером.
     */
    private volatile boolean serverConstructed_ = false;
    /**
     * Actual stop port after container start.
     */
    private volatile Integer stopPort_;
    /**
     * Actual stop key after container start.
     */
    private volatile String stopKey_;

    Container(int version, ClassLoader classLoader, ContainerProperties properties, String[] startArgs, FailableConsumer<Container> closeCallback) {
        version_ = version;
        classLoader_ = Objects.requireNonNull(classLoader);
        properties_ = Objects.requireNonNull(properties);
        startArgs_ = Objects.requireNonNull(startArgs);
        closeCallback_ = Objects.requireNonNull(closeCallback);
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
    @Override
    protected void onStart() throws Exception {
        synchronized (ENV_VARIABLES_LOCK) {
            final ServerAdapter serverAdapter = new ServerAdapter(version_);
            ContainerContext.Statics context = ContainerContext.statics(classLoader_);
            // Установка ссылки на листенер необходима только в момент запуска Jetty.
            // Ссылка на объект будет запомнена в com.devives.jetty.container.injection.ServerEventListenerAdapter.serverEventListener_
            context.setServerEventListener(new ServerEventListenerWrapper(serverAdapter.getServerEventListener()));
            serverAdapter.getStopFuture().whenComplete((ignored, stopError) -> handleServerAdapterClose(stopError));
            try {
                try (AutoCloseable ignore = registerShutdownMonitorEnvVariables()) {
                    // This will start a ServerAdapter.
                    Main.statics(classLoader_).main(startArgs_);
                    validateServerAdapterStarted();
                    setupStopPortAndKey(serverAdapter);
                } catch (Exception e) {
                    throw ExceptionUtils.asUnchecked(e);
                }
            } finally {
                context.setServerEventListener(null);
            }
            serverAdapter_ = serverAdapter;
        }
    }

    /**
     * Реакция на сбой жизненного цикла контейнера.
     * <p>
     * Если запуск завершился ошибкой до конструирования Jetty-сервера, событий его жизненного
     * цикла контейнер уже никогда не получит и штатная очистка ({@code handleServerAdapterClose})
     * не выполнится никогда — поэтому освобождаем ресурсы сразу: закрываем start-classloader
     * и удаляем временные файлы инъекций через {@code closeCallback_}. Ошибки очистки не
     * подменяют собой причину сбоя — добавляются в неё как suppressed.
     * <p>
     * Если сервер уже сконструирован (события пошли), ресурсы НЕ освобождаются: живой сервер
     * может использовать классы из start-classloader, а остановка пойдёт своим путём
     * (см. {@link #handleServerAdapterClose(Throwable)}).
     *
     * @param thRef ссылка на причину сбоя
     * @return {@link FailureAction#RETHROW} — сбой пробрасывается наружу из {@code start()}/{@code stop()}
     */
    @Override
    protected FailureAction onFailure(Ref<Throwable> thRef) {
        if (getStateHolder().get() == LifeCycleStates.STARTING && !serverConstructed_) {
            try {
                ExceptionUtils.collectAndThrow(
                        ((java.io.Closeable) classLoader_)::close,
                        () -> closeCallback_.accept(this));
            } catch (Exception cleanupError) {
                Throwable cause = thRef.get();
                if (cause != null && cleanupError != cause) {
                    cause.addSuppressed(cleanupError);
                }
            }
        }
        return FailureAction.RETHROW;
    }

    @Override
    public void addListener(LifeCycle.Listener listener) {
        super.addListener(listener);
    }

    @Override
    public void removeListener(LifeCycle.Listener listener) {
        super.removeListener(listener);
    }

    /**
     * Закрывает ресурсы контейнера после остановки Jetty-сервера.
     * <p>
     * Вызывается на любом исходе остановки: при ошибке остановки (в т.ч. самоостановке с ошибкой)
     * закрывает start-classloader и выполняет {@link #close()}, если контейнер ещё {@code STARTED}.
     *
     * @param stopError {@code null} при штатной остановке; причина — при завершении future ошибкой.
     */
    private void handleServerAdapterClose(Throwable stopError) {
        final CompletableFuture<Void> stopFuture = stopFuture_;
        Exception cleanupError = null;
        try {
            ExceptionUtils.collectAndThrow(
                    ((java.io.Closeable) classLoader_)::close,
                    () -> {
                        // Jetty Server can stop after JVM shutdown.
                        if (getStateHolder().get() == LifeCycleStates.STARTED) {
                            this.close();
                        }
                    }
            );
        } catch (Exception th) {
            cleanupError = th;
        } finally {
            serverAdapter_ = null;
            if (stopFuture != null) {
                try {
                    if (stopError != null) {
                        // Причина сбоя остановки передаётся ожидающим (stopAsync().get()).
                        if (cleanupError != null && cleanupError != stopError) {
                            stopError.addSuppressed(cleanupError);
                        }
                        stopFuture.completeExceptionally(stopError);
                    } else if (cleanupError != null) {
                        stopFuture.completeExceptionally(cleanupError);
                    } else {
                        stopFuture.complete(null);
                    }
                } finally {
                    stopFuture_ = null;
                }
            } else if (cleanupError != null) {
                Logger.getLogger(Container.class.getCanonicalName()).log(Level.WARNING,
                        "Jetty container cleanup error after server stop", cleanupError);
            }
        }
    }

    /**
     * Проверяет, что Jetty-сервер сконструирован: получено первое событие жизненного цикла
     * ({@code handleStarting}, см. {@code serverConstructed_}).
     * <p>
     * Причина несконструированного сервера: {@link ContainerBuilder#disableInjections()} без ручной
     * разводки инъекционных ресурсов, либо аргументы запуска, при которых {@code Main.main}
     * возвращается без запуска сервера ({@code --list-modules}, {@code --dry-run} и т.п.).
     *
     * @throws ContainerException если сервер не сконструирован
     */
    private void validateServerAdapterStarted() {
        if (!serverConstructed_) {
            throw new ContainerException("Jetty server was not constructed: no lifecycle events were received."
                    + " If ContainerBuilder.disableInjections() is used, the injection resources (context jar,"
                    + " injection jar, container-injection.xml) must be added to the start arguments manually.");
        }
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
            ShutdownMonitor shutdownMonitor = ShutdownMonitor.statics(serverAdapter.getClassLoader()).getInstance();
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
            Main.statics(classLoader_).main(args);
        }
        return stopFuture;
    }

    /**
     * Останавливает Jetty-сервер синхронно.
     */
    @Override
    protected void onStop() throws Exception {
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

    /**
     * Останавливает контейнер и удаляет временные файлы инъекций.
     * <p>
     * {@link #stop()} вызывается безусловно: в состояниях, отличных от {@code STARTED}
     * (уже остановлен, запуск не выполнялся или был прерван ошибкой), он игнорируется
     * (см. {@code LifeCycleBase#stop()}). Если Jetty-сервер остановился самостоятельно
     * ({@code handleServerAdapterClose}), контейнер переводится в {@code STOPPED} этим методом.
     */
    @Override
    public void close() throws Exception {
        Try.runnable(this::stop)
                .doFinally(() -> closeCallback_.accept(this))
                .run();
    }


    private class ServerEventListenerWrapper implements ServerEventListener {

        private final ServerEventListener delegate_;

        public ServerEventListenerWrapper(ServerEventListener delegate) {
            delegate_ = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public void handleStarting(Object oServer) {
            serverConstructed_ = true;
            delegate_.handleStarting(oServer);
            getPublisher().publish(listener -> ((Container.Listener) listener).onServerConstructed(oServer));
        }

        @Override
        public void handleStarted(Object oServer) {
            delegate_.handleStarted(oServer);
        }

        @Override
        public void handleFailure(Object oServer, Throwable cause) {
            delegate_.handleFailure(oServer, cause);
        }

        @Override
        public void handleStopping(Object oServer) {
            delegate_.handleStopping(oServer);
        }

        @Override
        public void handleStopped(Object oServer) {
            delegate_.handleStopped(oServer);
        }
    }

    public interface Listener extends LifeCycle.Listener {

        /**
         * Обработчик вызывается после {@link #onStarting()}, но перед {@link #onStarted()}.
         *
         * @param jettyServer ссылка на объект {@code org.eclipse.jetty.server.Server}.
         */
        default void onServerConstructed(Object jettyServer) {

        }
    }
}
