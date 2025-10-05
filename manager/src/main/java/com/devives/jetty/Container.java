package com.devives.jetty;

import com.devives.commons.lang.ClassLoaderUtils;
import com.devives.jetty.adapters.ServerAdapter;
import com.devives.jetty.adapters.ShutdownMonitorAdapter;
import com.devives.jetty.injection.LifeCycleListener;
import com.devives.jetty.interfaces.Main;
import com.devives.jetty.interfaces.ShutdownMonitor;

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
public class Container {

    /**
     * Блокировка решает проблему.
     * <p>
     * При запуске экземпляра Jetty создаётся статический экземпляр {@code org.eclipse.jetty.server.ShutdownMonitor#ShutdownMonitor}.
     * В конструкторе считываются переменные окружения:
     * <pre>{@code
     *   this.debug = System.getProperty("DEBUG") != null;
     *   this.host = System.getProperty("STOP.HOST", "127.0.0.1");
     *   this.port = Integer.getInteger("STOP.PORT", -1);
     *   this.key = System.getProperty("STOP.KEY", null);
     *   //only change the default exitVm setting if STOP.EXIT is explicitly set
     *   this.exitVm = Boolean.valueOf(System.getProperty("STOP.EXIT", "true"));
     * }</pre>
     * Т.к. переменные окружения {@link System#getProperty(String)} едины для всех потоков.
     * При одновременном запуске нескольких Jetty, значение "STOP.PORT" для всех потоков будет одинаково.
     * Что приведёт к ошибке открытия сокета во всех экземпляра кроме первого.
     */
    private static final Object ENV_VARIABLES_LOCK = new Object();
    private static final ThreadLocal<LifeCycleListener> LISTENER_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * Возвращает экземпляр обработчика событий жизненного цикла сервера Jetty для текущего потока.
     * <p>
     * Переменная устанавливается на время выполнения метода {@code org.eclipse.jetty.start.Main#main()}.
     * <p>
     * Запрашивается из конструктора {@code com.devives.jetty.injection.ServerEventListener}.
     *
     * @return Экземпляр обработчика событий.
     */
    public static LifeCycleListener getLifeCycleListener() {
        return LISTENER_THREAD_LOCAL.get();
    }

    private static void setLifeCycleHandler(LifeCycleListener handler) {
        LISTENER_THREAD_LOCAL.set(handler);
    }

    private final ClassLoader classLoader_;
    private final String[] startArgs_;
    private volatile ServerAdapterImpl serverAdapter_;
    private volatile CompletableFuture<Void> stopFuture_;
    private Integer stopPort_;
    private String stopKey_;
    private Integer stopTimeoutSeconds_;

    public Container(ClassLoader classLoader, String[] startArgs) {
        classLoader_ = Objects.requireNonNull(classLoader);
        startArgs_ = Objects.requireNonNull(startArgs);
    }

    void setStopPort(Integer stopPort) {
        stopPort_ = stopPort;
    }

    void setStopKey(String stopKey) {
        stopKey_ = stopKey;
    }

    void setStopTimeout(Integer timeoutSeconds) {
        stopTimeoutSeconds_ = timeoutSeconds;
    }

    public synchronized void start() {
        if (serverAdapter_ != null) {
            throw new ContainerException("Server has been started already.");
        }
        final ServerAdapterImpl serverAdapter = new ServerAdapterImpl();
        ClassLoaderUtils.callProcInContextClassLoader(classLoader_, () -> {
            Class<?> classMain = classLoader_.loadClass(Main.CLASS);
            Method mainMethod = classMain.getMethod("main", String[].class);
            synchronized (ENV_VARIABLES_LOCK) {
                Container.setLifeCycleHandler(serverAdapter.lifeCycleHandler());
                try (AutoCloseable ignore = registerShutdownMonitorEnvVariables()) {
                    mainMethod.invoke(null, (Object) startArgs_);
                } finally {
                    Container.setLifeCycleHandler(null);
                }
            }
            ShutdownMonitor shutdownMonitor = ShutdownMonitorAdapter.getShutdownMonitor(serverAdapter);
            stopPort_ = shutdownMonitor.getPort();
            stopKey_ = shutdownMonitor.getKey();
        });
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

    public synchronized Future<Void> stopAsync() {
        CompletableFuture<Void> stopFuture = stopFuture_;
        if (stopFuture == null) {
            stopFuture = new CompletableFuture<>();
            stopFuture_ = stopFuture;
            ClassLoaderUtils.callProcInContextClassLoader(classLoader_, () -> {
                Class<?> classMain = classLoader_.loadClass(Main.CLASS);
                Method mainMethod = classMain.getMethod("main", String[].class);
                String[] args = buildJettyStopAsyncArgs(stopTimeoutSeconds_);
                // Метод будет ожидать получения уведомления об остановке сервера (по Сокету) в течении stopTimeoutSeconds_ секунд.
                mainMethod.invoke(null, (Object) args);
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

    private String[] buildJettyStopAsyncArgs(Integer timeoutSeconds) {
        List<String> args = new ArrayList<>();
        if (stopPort_ == null || stopPort_ <= 0) {
            throw new ContainerException("Can't stop server asynchronously. Stop port not set.");
        }
        args.add("STOP.PORT=" + stopPort_);
        args.add("STOP.KEY=" + stopKey_);
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            args.add("STOP.WAIT=" + timeoutSeconds);
        }
        args.add("--stop");
        return args.toArray(new String[0]);
    }

}
