package com.devives.jetty.injection;

public final class ContainerContext {

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

    /**
     * Устанавливает экземпляр обработчика событий жизненного цикла сервера Jetty для текущего потока.
     *
     * @param handler Экземпляр обработчика событий.
     */
    public static void setLifeCycleHandler(LifeCycleListener handler) {
        LISTENER_THREAD_LOCAL.set(handler);
    }

}
