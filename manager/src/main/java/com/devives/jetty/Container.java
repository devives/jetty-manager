package com.devives.jetty;

import com.devives.commons.lang.ClassLoaderUtils;
import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.reflection.ProxyBuilder;
import com.devives.jetty.contract.Main;
import com.devives.jetty.contract.ShutdownMonitor;
import com.devives.jetty.contract.ShutdownService;
import com.devives.jetty.injection.LifeCycleListener;

import java.lang.reflect.Method;
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

    /**
     * Возвращает экземпляр обработчика событий жизненного цикла сервера Jetty для текущего потока.
     * <p>
     * Переменная устанавливается на время выполнения метода {@code org.eclipse.jetty.start.Main#main()}.
     * <p>
     * Запрашивается из конструктора {@code com.devives.jetty.injection.ServerEventListener}.
     *
     * @return Экземпляр обработчика событий.
     */
    private void setLifeCycleHandler(LifeCycleListener handler) {
        try {
            Class<?> classLifeCycleListener = classLoader_.loadClass("com.devives.jetty.injection.LifeCycleListener");
            Method method = classLoader_.loadClass("com.devives.jetty.injection.ContainerContext")
                    .getMethod("setLifeCycleHandler", classLifeCycleListener);
            Object oHandler = ProxyBuilder.forClasses(classLifeCycleListener).setTarget(handler).build();
            method.invoke(null, oHandler);
        } catch (Exception e) {
            throw ExceptionUtils.asUnchecked(e);
        }
    }

    private final ClassLoader classLoader_;
    private final String[] startArgs_;
    private volatile ServerAdapterImpl serverAdapter_;
    private volatile CompletableFuture<Void> stopFuture_;
    private volatile Integer stopPort_;
    private volatile String stopKey_;
    private volatile Integer stopTimeoutSeconds_;

    public Container(ClassLoader classLoader, String[] startArgs) {
        classLoader_ = Objects.requireNonNull(classLoader);
        startArgs_ = Objects.requireNonNull(startArgs);
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
     * @param stopPort новое значение.
     */
    void setStopPort(int stopPort) {
        stopPort_ = stopPort;
    }

    void setStopKey(String stopKey) {
        stopKey_ = stopKey;
    }

    void setStopTimeout(Integer timeoutSeconds) {
        stopTimeoutSeconds_ = timeoutSeconds;
    }

    public String getStopKey() {
        return stopKey_;
    }

    public Integer getStopPort() {
        return stopPort_;
    }

    public Integer getStopTimeoutSeconds() {
        return stopTimeoutSeconds_;
    }

    public synchronized void start() {
        if (serverAdapter_ != null) {
            throw new ContainerException("Server has been started already.");
        }
        final ServerAdapterImpl serverAdapter = new ServerAdapterImpl();
        synchronized (ENV_VARIABLES_LOCK) {
            this.setLifeCycleHandler(serverAdapter.lifeCycleHandler());
            try (AutoCloseable ignore = registerShutdownMonitorEnvVariables()) {
                Main.Static.at(classLoader_).main(startArgs_);
            } catch (Exception e) {
                throw ExceptionUtils.asUnchecked(e);
            } finally {
                this.setLifeCycleHandler(null);
            }
        }
        setupStopPortAndKey(serverAdapter);
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
        });
        serverAdapter_ = serverAdapter;
    }

    private void setupStopPortAndKey(ServerAdapterImpl serverAdapter) {
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

    public synchronized Future<Void> stopAsync() {
        CompletableFuture<Void> stopFuture = stopFuture_;
        if (stopFuture == null) {
            stopFuture = new CompletableFuture<>();
            stopFuture_ = stopFuture;
            ClassLoaderUtils.callProcInContextClassLoader(classLoader_, () -> {
                String[] args = buildJettyStopAsyncArgs();
                // Метод будет ожидать получения уведомления об остановке сервера (по Сокету) в течении stopTimeoutSeconds_ секунд.
                Main.Static.at(classLoader_).main(args);
            });
        }
        return stopFuture;
    }

    public synchronized void stop() {
        ((ServerAdapterImpl) getServerAdapter()).stop();
    }

    public ServerAdapter getServerAdapter() {
        ServerAdapter result = serverAdapter_;
        if (result == null) {
            throw new ContainerException("Server is not started.");
        }
        return result;
    }

    /**
     * @see "org.eclipse.jetty.server.ShutdownMonitor"
     */
    private AutoCloseable registerShutdownMonitorEnvVariables() {
        if (stopPort_ != null) {
            System.setProperty("STOP.PORT", String.valueOf(stopPort_));
        }
        if (stopKey_ != null) {
            System.setProperty("STOP.KEY", stopKey_);
        }
        System.setProperty("STOP.EXIT", "false");
        return () -> {
            if (stopPort_ != null) {
                System.clearProperty("STOP.PORT");
            }
            if (stopKey_ != null) {
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
        if (stopTimeoutSeconds_ != null && stopTimeoutSeconds_ > 0) {
            args.add("STOP.WAIT=" + stopTimeoutSeconds_);
        }
        args.add("--stop");
        return args.toArray(new String[0]);
    }

}
