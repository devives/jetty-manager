package com.devives.jetty.interfaces;

public interface ShutdownMonitor {

    String CLASS = "org.eclipse.jetty.server.ShutdownMonitor";

    boolean isExitVm();

    String getKey();

    int getPort();
}
