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
package com.devives.jetty.container.contract;

/**
 * Интерфейс слушателя событий жизненного цикла Jetty-сервера.
 * <p>
 * Содержит методы совпадающие по сигнатуре с {@code com.devives.jetty.container.context.ServerEventListener}.
 * <p>
 * Методы экземпляра слушателя событий вызываются из {@code com.devives.jetty.container.injection.ServerEventListenerAdapter},
 * инжектированного в загрузчик классов Jetty, через прокси класс, созданный
 * от интерфейса {@code com.devives.jetty.container.context.ServerEventListener}
 */
public interface ServerEventListener {

    /**
     * Вызывается перед стартом сервера.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     */
    void handleStarting(Object oServer);

    /**
     * Вызывается после старта сервера.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     */
    void handleStarted(Object oServer);

    /**
     * Вызывается при возникновении исключения на этапах старта или остановки, слушаемого Jetty-бина.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     * @param cause исключение. Будет выброшено в поток, после вызова всех обработчиков.
     */
    void handleFailure(Object oServer, Throwable cause);

    /**
     * Вызывается перед остановкой сервера.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     */
    void handleStopping(Object oServer);

    /**
     * Вызывается после остановки сервера.
     *
     * @param oServer Экземпляр {@code org.eclipse.jetty.server.Server}
     */
    void handleStopped(Object oServer);
}
