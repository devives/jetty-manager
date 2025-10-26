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

/**
 * Интерфейс сервера, запущенного в контейнере {@link Container}.
 * <p>
 * Предоставляет типизированный доступ к методам объекта Jetty-сервера.
 */
public interface Server {

    /**
     * @return true if the component is starting.
     * @see #isStarted()
     */
    boolean isStarting();

    /**
     * @return true if the component has been started.
     * @see #isStarting()
     */
    boolean isStarted();

    /**
     * @return true if the component is starting or has been started.
     */
    boolean isRunning();

    /**
     * @return true if the component is stopping.
     * @see #isStopped()
     */
    boolean isStopping();

    /**
     * @return true if the component has been stopped.
     * @see #isStopping()
     */
    boolean isStopped();

    /**
     * @return true if the component has failed to start or has failed to stop.
     */
    boolean isFailed();

    /**
     * Class loader in witch Jetty classes contains.
     *
     * @return class loader.
     */
    ClassLoader getClassLoader();


}
