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
package com.devives.jetty.container.injection;

import com.devives.jetty.container.context.ContainerContext;
import com.devives.jetty.container.context.ServerEventListener;
import org.eclipse.jetty.util.component.LifeCycle;

import java.util.Objects;

/**
 * Экземпляр класса создаётся через файл xml-конфигурации {@code jetty-wrapper-base\etc\jetty-container-inject.xml}.
 * <p>
 * Класс преобразовывает вызовы типизированных обработчиков событий {@link LifeCycle.Listener} в вызовы
 * не типизированных обработчиков {@link ServerEventListener}.
 */
public final class ServerEventListenerAdapter implements LifeCycle.Listener {

    private final ServerEventListener serverEventListener_;

    public ServerEventListenerAdapter() throws Exception {
        // Сохраняю ссылку на слушателя, т.к. она будет занулена после запуска сервера.
        serverEventListener_ = Objects.requireNonNull(
                ContainerContext.getServerEventListener(),
                "The 'com.devives.jetty.container.Container#getLifeCycleListener()` did not return value.");
    }

    @Override
    public void lifeCycleStarting(LifeCycle lifeCycle) {
        serverEventListener_.handleStarting(lifeCycle);
    }

    @Override
    public void lifeCycleStarted(LifeCycle lifeCycle) {
        serverEventListener_.handleStarted(lifeCycle);
    }

    @Override
    public void lifeCycleFailure(LifeCycle lifeCycle, Throwable cause) {
        serverEventListener_.handleFailure(lifeCycle, cause);
    }

    @Override
    public void lifeCycleStopping(LifeCycle lifeCycle) {
        serverEventListener_.handleStopping(lifeCycle);
    }

    @Override
    public void lifeCycleStopped(LifeCycle lifeCycle) {
        serverEventListener_.handleStopped(lifeCycle);
    }

}
