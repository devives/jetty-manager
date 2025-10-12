package com.devives.jetty.contract;

import java.util.Collection;

public interface Server extends Contract, LifeCycle {

    String CLASS_NAME = "org.eclipse.jetty.server.Server";

    long getStopTimeout();

    void setStopTimeout(long stopTimeout);

    void setStopAtShutdown(boolean stop);

    <T> Collection<T> getBeans(Class<T> clazz);
}
