package com.devives.jetty.container;

import com.devives.commons.lang.ClassLoaderUtils;
import com.devives.commons.lang.ExceptionUtils;
import com.devives.jetty.container.contract.ContainerContext;
import com.devives.jetty.container.contract.Main;
import com.devives.jetty.container.contract.ShutdownMonitor;
import com.devives.jetty.container.contract.ShutdownService;

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
    private final String[] startArgs_;
    private volatile ServerAdapter serverAdapter_;
    private volatile CompletableFuture<Void> stopFuture_;
    private volatile Integer httpPort_;
    private volatile Integer httpsPort_;
    private volatile Integer stopPort_;
    private volatile String stopKey_;
    private volatile Long stopTimeout_;

    public Container(ClassLoader classLoader, String[] startArgs) {
        classLoader_ = Objects.requireNonNull(classLoader);
        startArgs_ = Objects.requireNonNull(startArgs);
    }

    /**
     * Метод для установки значения из строителя.
     *
     * @param httpPort
     */
    void setHttpPort(Integer httpPort) {
        httpPort_ = httpPort;
    }

    void setHttpsPort(Integer httpsPort) {
        httpsPort_ = httpsPort;
    }

    void setStopPort(int stopPort) {
        stopPort_ = stopPort;
    }

    void setStopKey(String stopKey) {
        stopKey_ = stopKey;
    }

    void setStopTimeout(Long timeoutSeconds) {
        stopTimeout_ = timeoutSeconds;
    }

    /**
     * Возвращает номер HTTP-порта.
     * <p>
     * Наличие значение порта, не означает, что коннектор запущен.
     *
     * @return номер порта.
     */
    public Integer getHttpPort() {
        return httpPort_;
    }

    /**
     * Возвращает номер HTTPS-порта.
     * <p>
     * Наличие значение порта, не означает, что SSL-коннектор запущен.
     *
     * @return номер порта.
     */
    public Integer getHttpsPort() {
        return httpsPort_;
    }

    /**
     * Возвращает значение ключа остановки Jetty-сервера.
     *
     * @return значение ключа.
     */
    public String getStopKey() {
        return stopKey_;
    }

    /**
     * @return номер порта остановки.
     */
    public int getStopPort() {
        return stopPort_ != null ? stopPort_ : -1;
    }

    /**
     * @return таймаут, секунды.
     */
    public Long getStopTimeout() {
        return stopTimeout_;
    }

    public synchronized void start() {
        if (serverAdapter_ != null) {
            throw new ContainerException("Server has been started already.");
        }
        final ServerAdapter serverAdapter = new ServerAdapter();
        synchronized (ENV_VARIABLES_LOCK) {
            ContainerContext.Static context = ContainerContext.Static.at(classLoader_);
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
     *
     * @see ContainerBuilder#setStopPort(int)
     */
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
        if (stopTimeout_ != null && stopTimeout_ > 0) {
            args.add("STOP.WAIT=" + stopTimeout_);
        }
        args.add("--stop");
        return args.toArray(new String[0]);
    }

}
