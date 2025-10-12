package com.devives.jetty.container.contract;

/**
 * Интерфейс слушателя событий жизненного цикла Jetty-сервера.
 * <p>
 * Содержит методы совпадающие по сигнатуре с {@code com.devives.jetty.container.context.ServerEventListener}.
 * <p>
 * Методы экземпляра слушателя событий вызываются из {@code com.devives.jetty.container.injection.ServerEventListenerAdapter},
 * инжектированного в загрузчик классов Jetty, через прокси класс, созданный
 * от интерфейса {@code com.devives.jetty.container.context.ServerEventListener}
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
     *
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
