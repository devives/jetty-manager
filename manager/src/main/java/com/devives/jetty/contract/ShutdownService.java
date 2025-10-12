package com.devives.jetty.contract;

public interface ShutdownService extends Contract {

    String CLASS_NAME = "org.eclipse.jetty.server.ShutdownService";

    boolean isExitVm();

    String getKey();

    /**
     * Возвращает реальный номер порта запущенного сокета.
     *
     * @return номер порта. Если сокет не запущен: "-1".
     * @since Jetty12.
     */
    int getLocalPort();

    /**
     * Возвращает указанны при запуске номер порта.
     *
     * @return номер порта.
     */
    int getPort();
}
