package com.devives.jetty.container.injection;

import com.devives.jetty.container.context.ContainerContext;
import com.devives.jetty.container.context.ServerEventListener;
import org.eclipse.jetty.util.component.LifeCycle;

import java.util.Objects;

/**
 * Экземпляр класса создаётся через файл xml-конфигурации {@code jetty-wrapper-base\etc\jetty-container-inject.xml}.
 * <p>
 * Класс преобразовывает вызовы типизированных обработчиков событий {@link LifeCycle.Listener} в вызовы
 * не типизированных обработчиков {@link ServerEventListener}.
 */
public final class ServerEventListenerAdapter implements LifeCycle.Listener {

    private final ServerEventListener serverEventListener_;

    public ServerEventListenerAdapter() throws Exception {
        // Сохраняю ссылку на слушателя, т.к. она будет занулена после запуска сервера.
        serverEventListener_ = Objects.requireNonNull(
                ContainerContext.getServerEventListener(),
                "The 'com.devives.jetty.container.Container#getLifeCycleListener()` did not return value.");
    }

    @Override
    public void lifeCycleStarting(LifeCycle lifeCycle) {
        serverEventListener_.handleStarting(lifeCycle);
    }

    @Override
    public void lifeCycleStarted(LifeCycle lifeCycle) {
        serverEventListener_.handleStarted(lifeCycle);
    }

    @Override
    public void lifeCycleFailure(LifeCycle lifeCycle, Throwable cause) {
        serverEventListener_.handleFailure(lifeCycle, cause);
    }

    @Override
    public void lifeCycleStopping(LifeCycle lifeCycle) {
        serverEventListener_.handleStopping(lifeCycle);
    }

    @Override
    public void lifeCycleStopped(LifeCycle lifeCycle) {
        serverEventListener_.handleStopped(lifeCycle);
    }

}
