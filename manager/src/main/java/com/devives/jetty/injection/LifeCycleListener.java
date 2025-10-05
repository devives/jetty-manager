package com.devives.jetty.injection;

/**
 * Обработчик событий жизненного цикла Jetty-бинов.
 * <p>
 * Методы экземпляра обработчика событий вызываются из {@code com.devives.jetty.injection.ServerEventListener},
 * инжектированного в загрузчик классов Jetty.
 */
public interface LifeCycleListener {

    /**
     * Вызывается перед стартом сервера.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     */
    void handleServerStarting(Object oServer);

    /**
     * Вызывается после старта сервера.
     */
    void handleServerStarted();

    /**
     * Вызывается при возникновении исключения на этапах старта или остановки, слушаемого Jetty-бина.
     *
     * @param cause исключение. Будет выброшено в поток, после вызова всех обработчиков.
     */
    void handleServerFailure(Throwable cause);

    /**
     * Вызывается перед остановкой сервера.
     */
    void handleServerStopping();

    /**
     * Вызывается после остановки сервера.
     */
    void handleServerStopped();
}
