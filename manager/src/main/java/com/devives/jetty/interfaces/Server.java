package com.devives.jetty.interfaces;

public interface Server {

    String CLASS = "org.eclipse.jetty.server.Server";

    long getStopTimeout();

    void setStopTimeout(long stopTimeout);

    void setStopAtShutdown(boolean stop);

    boolean isRunning();

    boolean isStarting();

    boolean isStarted();

    boolean isStopping();

    boolean isStopped();

    boolean isFailed();

    void stop();

}
