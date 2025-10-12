package com.devives.jetty;

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.reflection.ProxyBuilder;
import com.devives.jetty.contract.Contract;
import com.devives.jetty.contract.Server;
import com.devives.jetty.injection.LifeCycleListener;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;


class ServerAdapterImpl implements ServerAdapter {
    private final CompletableFuture<Void> startFuture_ = new CompletableFuture<>();
    private final CompletableFuture<Void> stopFuture_ = new CompletableFuture<>();
    private final LifeCycleListener lifeCycleHandler_ = new LifeCycleListenerImpl();
    private volatile ClassLoader serverClassLoader_;
    private volatile Server serverProxy_;
    private volatile State state_ = State.STOPPED;

    CompletionStage<Void> getStartFuture() {
        return startFuture_;
    }

    CompletionStage<Void> getStopFuture() {
        return stopFuture_;
    }

    void stop() {
        ExceptionUtils.passChecked(serverProxy_::stop);
    }

    LifeCycleListener lifeCycleHandler() {
        return lifeCycleHandler_;
    }

    long getStopTimeout() {
        return serverProxy_.getStopTimeout();
    }

    void setStopTimeout(long stopTimeout) {
        serverProxy_.setStopTimeout(stopTimeout);
    }

    void setStopAtShutdown(boolean stop) {
        serverProxy_.setStopAtShutdown(stop);
    }

    Server getServerProxy() {
        return serverProxy_;
    }

    @Override
    public boolean isRunning() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isRunning()
                : Arrays.binarySearch(new State[]{State.STARTED, State.STARTING}, state_) >= 0;
    }

    @Override
    public boolean isStarting() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isStarting()
                : state_ == State.STARTING;
    }

    @Override
    public boolean isStarted() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isStarted()
                : state_ == State.STARTED;
    }

    @Override
    public boolean isStopping() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isStopping()
                : state_ == State.STARTING;
    }

    @Override
    public boolean isStopped() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isStopped()
                : state_ == State.STOPPED;
    }

    @Override
    public boolean isFailed() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isFailed()
                : state_ == State.FAILED;
    }

    @Override
    public ClassLoader getClassLoader() {
        return serverClassLoader_;
    }

    public <T> Collection<T> getBeans(Class<T> clazz) throws ClassNotFoundException {
        if (!Contract.class.isAssignableFrom(clazz)) {
            throw new ContainerException("The clazz must be a descendant of \"com.devives.jetty.contract.Contract\".");
        }

        String remoteClassName = null;
        try {
            remoteClassName = (String) clazz.getDeclaredField("CLASS_NAME").get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ContainerException("Passed interface must have \"CLASS_NAME\" field.", e);
        }

        Class<?> remoteClass = serverClassLoader_.loadClass(remoteClassName);
        return serverProxy_.getBeans(remoteClass).stream()
                .map(o -> ProxyBuilder.forClasses(clazz).setTarget(o).build())
                .collect(Collectors.toList());
    }

    public class LifeCycleListenerImpl implements LifeCycleListener {

        @Override
        public void handleServerStarting(Object oServer) {
            try {
                serverProxy_ = ProxyBuilder.forClasses(Server.class).setTarget(oServer).build();
                serverClassLoader_ = oServer.getClass().getClassLoader();
                state_ = State.STARTING;
            } catch (Exception e) {
                serverProxy_ = null;
                serverClassLoader_ = null;
                throw e;
            }
        }

        @Override
        public void handleServerStarted() {
            state_ = State.STARTED;
            startFuture_.complete(null);
        }

        @Override
        public void handleServerFailure(Throwable cause) {
            State priorState = state_;
            state_ = State.FAILED;
            serverProxy_ = null;
            serverClassLoader_ = null;
            if (priorState == State.STARTING || priorState == State.STARTED) {
                startFuture_.completeExceptionally(cause);
            } else if (priorState == State.STOPPING || priorState == State.STOPPED) {
                stopFuture_.completeExceptionally(cause);
            }
        }

        @Override
        public void handleServerStopping() {
            state_ = State.STOPPING;
        }

        @Override
        public void handleServerStopped() {
            state_ = State.STOPPED;
            serverProxy_ = null;
            serverClassLoader_ = null;
            stopFuture_.complete(null);
        }
    }

    private enum State {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED,
        FAILED
    }

}

