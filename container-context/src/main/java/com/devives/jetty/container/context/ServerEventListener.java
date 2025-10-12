package com.devives.jetty.container.context;

/**
 * Обработчик событий жизненного цикла Jetty сервера.
 * <p>
 * Методы экземпляра обработчика событий вызываются из {@code com.devives.jetty.injection.ServerEventListener},
 * инжектированного в загрузчик классов Jetty.
 * <p>
 * Интерфейс используется для создания Proxy-объекта, декорирующего обработчик событий из
 */
public interface ServerEventListener {

    /**
     * Вызывается перед стартом сервера.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     */
    void handleStarting(Object oServer);

    /**
     * Вызывается после старта сервера.
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     */
    void handleStarted(Object oServer);

    /**
     * Вызывается при возникновении исключения на этапах старта или остановки, слушаемого Jetty-бина.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     * @param cause исключение. Будет выброшено в поток, после вызова всех обработчиков.
     */
    void handleFailure(Object oServer, Throwable cause);

    /**
     * Вызывается перед остановкой сервера.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     */
    void handleStopping(Object oServer);

    /**
     * Вызывается после остановки сервера.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     */
    void handleStopped(Object oServer);
}
