package com.devives.jetty.injection;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.LifeCycle;

import java.util.Objects;

/**
 * Экземпляр класса создаётся через файл xml-конфигурации {@code jetty-wrapper-base\etc\jetty-container-inject.xml}
 */
public final class ServerEventListener implements LifeCycle.Listener {

    private final Server server_;
    private final LifeCycleListener lifeCycleListener_;

    public ServerEventListener(Server server) throws Exception {
        server_ = server;
        lifeCycleListener_ = Objects.requireNonNull(
                com.devives.jetty.Container.getLifeCycleListener(),
                "The 'com.devives.jetty.Container#getLifeCycleListener()` did not return value.");
//        Class<?> jettyRunnableClass = Class.forName("com.devives.jetty.Container");
//        lifeCycleListener_ = (LifeCycleListener) Objects.requireNonNull(jettyRunnableClass.getMethod("getLifeCycleListener").invoke(null),
//                "'com.devives.jetty.Container#getLifeCycleListener()` did not return value.");
    }

    @Override
    public void lifeCycleStarting(LifeCycle event) {
        lifeCycleListener_.handleServerStarting(server_);
    }

    @Override
    public void lifeCycleStarted(LifeCycle event) {
        lifeCycleListener_.handleServerStarted();
    }

    @Override
    public void lifeCycleFailure(LifeCycle event, Throwable cause) {
        lifeCycleListener_.handleServerFailure(cause);
    }

    @Override
    public void lifeCycleStopping(LifeCycle event) {
        lifeCycleListener_.handleServerStopping();
    }

    @Override
    public void lifeCycleStopped(LifeCycle event) {
        lifeCycleListener_.handleServerStopped();
    }

}
