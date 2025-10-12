package com.devives.jetty.container;

/**
 * Интерфейс сервера, запущенного в контейнере {@link Container}.
 * <p>
 * Предоставляет типизированный доступ к методам объекта Jetty-сервера.
 */
public interface Server {

    /**
     * @return true if the component is starting.
     * @see #isStarted()
     */
    boolean isStarting();

    /**
     * @return true if the component has been started.
     * @see #isStarting()
     */
    boolean isStarted();

    /**
     * @return true if the component is starting or has been started.
     */
    boolean isRunning();

    /**
     * @return true if the component is stopping.
     * @see #isStopped()
     */
    boolean isStopping();

    /**
     * @return true if the component has been stopped.
     * @see #isStopping()
     */
    boolean isStopped();

    /**
     * @return true if the component has failed to start or has failed to stop.
     */
    boolean isFailed();

    /**
     * Class loader in witch Jetty classes contains.
     *
     * @return class loader.
     */
    ClassLoader getClassLoader();


}
