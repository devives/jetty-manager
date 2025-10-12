package com.devives.jetty.container;

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.reflection.ProxyBuilder;
import com.devives.jetty.container.contract.Contract;
import com.devives.jetty.container.contract.Server;
import com.devives.jetty.container.contract.ServerEventListener;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;


class ServerAdapter implements com.devives.jetty.container.Server {
    private final CompletableFuture<Void> startFuture_ = new CompletableFuture<>();
    private final CompletableFuture<Void> stopFuture_ = new CompletableFuture<>();
    private final ServerEventListener lifeCycleHandler_ = new ServerProxyListener();
    private volatile ClassLoader serverClassLoader_;
    private volatile Server serverProxy_;
    private volatile State state_ = State.STOPPED;

    CompletionStage<Void> getStartFuture() {
        return startFuture_;
    }

    CompletionStage<Void> getStopFuture() {
        return stopFuture_;
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

    void stop() {
        ExceptionUtils.passChecked(serverProxy_::stop);
    }

    /**
     * Возвращает слушатель событий экземпляра Jetty-сервера.
     *
     * @return слушатель событий.
     */
    ServerEventListener getServerEventListener() {
        return lifeCycleHandler_;
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
        Contract contract = clazz.getAnnotation(Contract.class);
        if (contract == null) {
            throw new ContainerException("Passed interface has not annotated by @Contract.");
        }
        String remoteClassName = clazz.getAnnotation(Contract.class).className();
        Class<?> remoteClass = serverClassLoader_.loadClass(remoteClassName);
        return serverProxy_.getBeans(remoteClass).stream()
                .map(o -> ProxyBuilder.forClasses(clazz).setTarget(o).build())
                .collect(Collectors.toList());
    }

    public final class ServerProxyListener implements ServerEventListener {

        @Override
        public void handleStarting(Object oServer) {
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
        public void handleStarted(Object oServer) {
            state_ = State.STARTED;
            startFuture_.complete(null);
        }

        @Override
        public void handleFailure(Object oServer, Throwable cause) {
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
        public void handleStopping(Object oServer) {
            state_ = State.STOPPING;
        }

        @Override
        public void handleStopped(Object oServer) {
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

