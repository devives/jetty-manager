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

@Contract(className = "org.eclipse.jetty.server.ShutdownService")
public interface ShutdownService {

    /**
     * Флаг указывает, при остановке Jetty-сервера будет вызван {@code System.exit(0);}, что остановит JVM.
     * <p>
     * Значение устанавливается через параметр окружения {@code System.getProperty("STOP.EXIT", true)}.
     *
     * @return true, если JVM будет остановлен, иначе false.
     */
    boolean isExitVm();

    /**
     * Возвращает значение ключа остановки, которое необходимо передать в сокет остановки Jetty-сервера.
     * <p>
     * Значение устанавливается через параметр окружения {@code System.getProperty("STOP.KEY", randomStr())}.
     *
     * @return значение ключа остановки.
     */
    String getKey();

    /**
     * Возвращает реальный номер порта запущенного сокета.
     *
     * @return номер порта. Если сокет не запущен: "-1".
     * @since Jetty12.
     */
    int getLocalPort();

    /**
     * Возвращает указанное при запуске имя хоста.
     * <p>
     * Значение устанавливается через параметр окружения {@code System.getProperty("STOP.HOST", "127.0.0.1")}.
     *
     * @return номер порта.
     */
    int getHost();

    /**
     * Возвращает указанный при запуске номер порта.
     * <p>
     * Значение устанавливается через параметр окружения {@code System.getProperty("STOP.PORT")}.
     *
     * @return номер порта.
     */
    int getPort();
}
