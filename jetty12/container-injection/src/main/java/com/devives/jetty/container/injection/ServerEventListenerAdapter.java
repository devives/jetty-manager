package com.devives.jetty.container.injection;

import com.devives.jetty.container.context.ContainerContext;
import com.devives.jetty.container.context.ServerEventListener;
import org.eclipse.jetty.util.component.LifeCycle;

import java.util.Objects;

/**
 * Экземпляр класса создаётся через файл xml-конфигурации {@code jetty-wrapper-base\etc\jetty-container-inject.xml}
 */
public final class ServerEventListenerAdapter implements LifeCycle.Listener {

    private final ServerEventListener lifeCycleListener_;

    /**
     * Создаёт новый экземпляр.
     */
    public ServerEventListenerAdapter() {
        lifeCycleListener_ = Objects.requireNonNull(
                ContainerContext.getServerEventListener(),
                "The 'com.devives.jetty.container.context.ContainerContext#getServerEventListener()` did not return value.");
    }

    @Override
    public void lifeCycleStarting(LifeCycle lifeCycle) {
        lifeCycleListener_.handleStarting(lifeCycle);
    }

    @Override
    public void lifeCycleStarted(LifeCycle lifeCycle) {
        lifeCycleListener_.handleStarted(lifeCycle);
    }

    @Override
    public void lifeCycleFailure(LifeCycle lifeCycle, Throwable cause) {
        lifeCycleListener_.handleFailure(lifeCycle, cause);
    }

    @Override
    public void lifeCycleStopping(LifeCycle lifeCycle) {
        lifeCycleListener_.handleStopping(lifeCycle);
    }

    @Override
    public void lifeCycleStopped(LifeCycle lifeCycle) {
        lifeCycleListener_.handleStopped(lifeCycle);
    }

}
