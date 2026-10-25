/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.devives.jetty.container;

import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.reflection.ProxyBuilder;
import com.devives.commons.lifecycle.LifeCycle;
import com.devives.commons.state.State;
import com.devives.commons.state.Stateful;
import com.devives.commons.state.SynchronizedStateHolderImpl;
import com.devives.jetty.container.contract.*;
import com.devives.jetty.container.contract.Server;

import java.io.Closeable;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static com.devives.commons.lifecycle.LifeCycleStates.*;


final class ServerAdapter extends Stateful<State> implements com.devives.jetty.container.Server {
    private final CompletableFuture<Void> startFuture_ = new CompletableFuture<>();
    private final CompletableFuture<Void> stopFuture_ = new CompletableFuture<>();
    private final ServerEventListener lifeCycleHandler_ = new ServerProxyListener();
    private final int version_;
    private volatile ClassLoader serverClassLoader_;
    private volatile Server serverProxy_;

    public ServerAdapter(int version) {
        super(new SynchronizedStateHolderImpl<>(STARTING));
        this.version_ = version;
    }

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

    /**
     * Возвращает слушатель событий экземпляра Jetty-сервера.
     * <p>
     * Этот экземпляр устанавливается в {@link ContainerContext.Statics#setServerEventListener(ServerEventListener)} на
     * время запуска Jetty-сервера.
     *
     * @return слушатель событий.
     */
    ServerEventListener getServerEventListener() {
        return lifeCycleHandler_;
    }

    public void stop() throws Exception {
        ExceptionUtils.passChecked(serverProxy_::stop);
    }

    public void addListener(LifeCycle.Listener listener) {

    }

    public void removeListener(LifeCycle.Listener listener) {

    }

    @Override
    public boolean isRunning() {
        final Server server = serverProxy_;
        return server != null
                ? server.isRunning()
                : getStateHolder().isExpected(STARTING, STARTED);
    }

    @Override
    public boolean isStarting() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isStarting()
                : getStateHolder().isExpected(STARTING);
    }

    @Override
    public boolean isStarted() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isStarted()
                : getStateHolder().isExpected(STARTED);
    }

    @Override
    public boolean isStopping() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isStopping()
                : getStateHolder().isExpected(STOPPING);
    }

    @Override
    public boolean isStopped() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isStopped()
                : getStateHolder().isExpected(STOPPED);
    }

    @Override
    public boolean isFailed() {
        final Server server = serverProxy_;
        return server != null
                ? serverProxy_.isFailed()
                :  getStateHolder().isExpected(FAILED);
    }

    @Override
    public ClassLoader getClassLoader() {
        return serverClassLoader_;
    }

    /**
     * Возвращает адаптер к экземпляру Jetty-бина.
     *
     * @param clazz класс адаптера, аннотированного {@link Contract}.
     * @param <T>   тип адаптера.
     * @return новый экземпляр адаптера.
     * @throws ContainerException     if passed interface has not annotated by {@link Contract}.
     * @throws ClassNotFoundException if Jetty class loader do not contain class named {@link Contract#className()}.
     */
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
                getStateHolder().set(STARTING);
            } catch (Exception e) {
                serverProxy_ = null;
                serverClassLoader_ = null;
                throw e;
            }
        }

        @Override
        public void handleStarted(Object oServer) {
            getStateHolder().set(STARTED);
            startFuture_.complete(null);
        }

        @Override
        public void handleFailure(Object oServer, Throwable cause) {
            State priorState = (State) getStateHolder().get();
            getStateHolder().set(FAILED);
            serverProxy_ = null;
            serverClassLoader_ = null;
            if (priorState == STARTING || priorState == STARTED) {
                startFuture_.completeExceptionally(cause);
            } else if (priorState == STOPPING || priorState == STOPPED) {
                stopFuture_.completeExceptionally(cause);
            }
        }

        @Override
        public void handleStopping(Object oServer) {
            getStateHolder().set(STOPPING);
        }

        @Override
        public void handleStopped(Object oServer) {
            getStateHolder().set(STOPPED);
            try {
                closeJettyClassLoaders();
            } finally {
                serverProxy_ = null;
                stopFuture_.complete(null);
            }
        }
    }

    private void closeJettyClassLoaders() {
        final ClassLoader classLoader = serverClassLoader_;
        try {
            ExceptionUtils.collectAndThrow(
                    () -> {
                        // Fix: Jetty does not close URLClassLoader after server stop. It's produce file leaks.
                        if (version_ >= 12) {
                            Environment.statics(classLoader).getAll().stream()
                                    .map(Environment::getClassLoader)
                                    .filter(Closeable.class::isInstance)
                                    .map(Closeable.class::cast)
                                    .forEach(c -> ExceptionUtils.passChecked(c::close));
                        }
                    },
                    () -> {
                        // Fix: Jetty does not close URLClassLoader after server stop. It's produce file leaks.
                        if (classLoader instanceof Closeable) {
                            ((Closeable) classLoader).close();
                        }
                    });
        } finally {
            serverClassLoader_ = null;
        }
    }

}

