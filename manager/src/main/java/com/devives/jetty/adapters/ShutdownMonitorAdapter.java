package com.devives.jetty.adapters;

import com.devives.commons.lang.ClassLoaderUtils;
import com.devives.commons.lang.reflection.ProxyBuilder;
import com.devives.jetty.interfaces.ShutdownMonitor;

import java.lang.reflect.Method;

public interface ShutdownMonitorAdapter extends ShutdownMonitor {

    static ShutdownMonitorAdapter getShutdownMonitor(ServerAdapter serverAdapter) {
        Object oShutdownMonitor = ClassLoaderUtils.callFuncInContextClassLoader(serverAdapter.getClassLoader(), () -> {
            Class<?> classMain = serverAdapter.getClassLoader().loadClass(ShutdownMonitor.CLASS);
            Method mainMethod = classMain.getMethod("getInstance");
            return mainMethod.invoke(null);
        });
        return ProxyBuilder.forClasses(ShutdownMonitorAdapter.class).setTarget(oShutdownMonitor).build();
    }

}
