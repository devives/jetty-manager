package com.devives.jetty.container.context;

/**
 * Контекст контейнера Jetty-сервера.
 * <p>
 * Класс выполняет задачи связывания кода классов контейнера с кодом классов инжектированных в загрузчик классов Jetty (startJarLoader).
 * <ul>
 *     <li>Контейнер получает доступ к статическому методу {@link ContainerContext#setServerEventListener(ServerEventListener)}
 *     по имени, т.к. не имеет прямого доступа к классу.
 *     <li>Класс {@code com.devives.jetty.container.injection.ServerEventListenerAdapter}, инжектированный
 *     в загрузчик классов Jetty (startJarLoader), выполняет вызов {@link ContainerContext#getServerEventListener()}
 *     напрямую, в момент создания.
 * </ul>
 *
 */
public final class ContainerContext {

    private static final ThreadLocal<ServerEventListener> LISTENER_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * Возвращает экземпляр слушателя событий жизненного цикла сервера Jetty для текущего потока.
     * <p>
     * Переменная устанавливается на время выполнения метода {@code org.eclipse.jetty.start.Main#main()}.
     * <p>
     * Запрашивается из конструктора {@code com.devives.jetty.injection.ServerEventListener}.
     *
     * @return Экземпляр слушателя событий.
     */
    public static ServerEventListener getServerEventListener() {
        return LISTENER_THREAD_LOCAL.get();
    }

    /**
     * Устанавливает экземпляр обработчика событий жизненного цикла сервера Jetty для текущего потока.
     *
     * @param listener Экземпляр слушателя событий.
     */
    public static void setServerEventListener(ServerEventListener listener) {
        LISTENER_THREAD_LOCAL.set(listener);
    }

}
