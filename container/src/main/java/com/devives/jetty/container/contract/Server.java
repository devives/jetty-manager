package com.devives.jetty.container.contract;

import java.util.Collection;

@Contract(className = "org.eclipse.jetty.server.Server")
public interface Server extends Contract, LifeCycle {

    long getStopTimeout();

    void setStopTimeout(long stopTimeout);

    void setStopAtShutdown(boolean stop);

    <T> Collection<T> getBeans(Class<T> clazz);
}
