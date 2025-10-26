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
package com.devives.jetty.container.context;

/**
 * Контекст контейнера Jetty-сервера.
 * <p>
 * Класс выполняет задачи связывания кода классов контейнера с кодом классов инжектированных в загрузчик классов Jetty (startJarLoader).
 * <ul>
 *     <li>Контейнер получает доступ к статическому методу {@link ContainerContext#setServerEventListener(ServerEventListener)}
 *     по имени, т.к. не имеет прямого доступа к классу.
 *     <li>Класс {@code com.devives.jetty.container.injection.ServerEventListenerAdapter}, инжектированный
 *     в загрузчик классов Jetty (startJarLoader), выполняет вызов {@link ContainerContext#getServerEventListener()}
 *     напрямую, в момент создания.
 * </ul>
 *
 */
public final class ContainerContext {

    private static final ThreadLocal<ServerEventListener> LISTENER_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * Возвращает экземпляр слушателя событий жизненного цикла сервера Jetty для текущего потока.
     * <p>
     * Переменная устанавливается на время выполнения метода {@code org.eclipse.jetty.start.Main#main()}.
     * <p>
     * Запрашивается из конструктора {@code com.devives.jetty.injection.ServerEventListener}.
     *
     * @return Экземпляр слушателя событий.
     */
    public static ServerEventListener getServerEventListener() {
        return LISTENER_THREAD_LOCAL.get();
    }

    /**
     * Устанавливает экземпляр обработчика событий жизненного цикла сервера Jetty для текущего потока.
     *
     * @param listener Экземпляр слушателя событий.
     */
    public static void setServerEventListener(ServerEventListener listener) {
        LISTENER_THREAD_LOCAL.set(listener);
    }

}
