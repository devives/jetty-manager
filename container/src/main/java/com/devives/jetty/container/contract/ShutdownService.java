package com.devives.jetty.container.contract;

@Contract(className = "org.eclipse.jetty.server.ShutdownService")
public interface ShutdownService {

    /**
     * Флаг указывает, при остановке Jetty-сервера будет вызван {@code System.exit(0);}, что остановит JVM.
     * <p>
     * Значение устанавливается через параметр окружения {@code System.getProperty("STOP.EXIT", true)}.
     *
     * @return true, если JVM будет остановлен, иначе false.
     */
    boolean isExitVm();

    /**
     * Возвращает значение ключа остановки, которое необходимо передать в сокет остановки Jetty-сервера.
     * <p>
     * Значение устанавливается через параметр окружения {@code System.getProperty("STOP.KEY", randomStr())}.
     *
     * @return значение ключа остановки.
     */
    String getKey();

    /**
     * Возвращает реальный номер порта запущенного сокета.
     *
     * @return номер порта. Если сокет не запущен: "-1".
     * @since Jetty12.
     */
    int getLocalPort();

    /**
     * Возвращает указанное при запуске имя хоста.
     * <p>
     * Значение устанавливается через параметр окружения {@code System.getProperty("STOP.HOST", "127.0.0.1")}.
     *
     * @return номер порта.
     */
    int getHost();

    /**
     * Возвращает указанный при запуске номер порта.
     * <p>
     * Значение устанавливается через параметр окружения {@code System.getProperty("STOP.PORT")}.
     *
     * @return номер порта.
     */
    int getPort();
}
