package com.devives.jetty.adapters;

public interface ServerAdapter {

    boolean isRunning();

    boolean isStarting();

    boolean isStarted();

    boolean isStopping();

    boolean isStopped();

    boolean isFailed();

    ClassLoader getClassLoader();

}
