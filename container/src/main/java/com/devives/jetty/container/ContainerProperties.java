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

public class ContainerProperties {
    private Integer httpPort_;
    private Integer httpsPort_;
    private Integer stopPort_;
    private String stopKey_;
    private boolean exitVmOnStop_ = false;
    private Long stopTimeout_;

    /**
     * Метод для установки значения из строителя.
     *
     * @param httpPort номер порта.
     */
    void setHttpPort(int httpPort) {
        httpPort_ = httpPort;
    }

    /**
     * Метод для установки значения из строителя.
     *
     * @param httpsPort номер порта.
     */
    void setHttpsPort(int httpsPort) {
        httpsPort_ = httpsPort;
    }

    /**
     * Метод для установки значения из строителя.
     *
     * @param stopPort номер порта.
     */
    void setStopPort(int stopPort) {
        stopPort_ = stopPort;
    }

    /**
     * Метод для установки значения из строителя.
     *
     * @param stopKey номер порта.
     */
    void setStopKey(String stopKey) {
        stopKey_ = stopKey;
    }

    /**
     * Метод для установки значения из строителя.
     *
     * @param timeoutSeconds интервал ожидания, в секундах.
     */
    void setStopTimeout(Long timeoutSeconds) {
        stopTimeout_ = timeoutSeconds;
    }

    /**
     * Метод для установки значения из строителя.
     *
     * @param exitVmOnStop новое значение
     */
    void setExitVmOnStop(boolean exitVmOnStop) {
        exitVmOnStop_ = exitVmOnStop;
    }

    /**
     * Флаг указывает на необходимость вызова {@link System#exit(int)} после остановки Jetty-сервера.
     *
     * @return true, если вызывать, иначе false.
     */
    public boolean isExitVmOnStop() {
        return exitVmOnStop_;
    }

    /**
     * Возвращает номер HTTP-порта.
     * <p>
     * Наличие значение порта, не означает, что коннектор запущен.
     *
     * @return номер порта.
     */
    public Integer getHttpPort() {
        return httpPort_;
    }

    /**
     * Возвращает номер HTTPS-порта.
     * <p>
     * Наличие значение порта, не означает, что SSL-коннектор запущен.
     *
     * @return номер порта.
     */
    public Integer getHttpsPort() {
        return httpsPort_;
    }

    /**
     * @return номер порта остановки.
     */
    public Integer getStopPort() {
        return stopPort_ != null ? stopPort_ : -1;
    }

    /**
     * Возвращает значение ключа остановки Jetty-сервера.
     *
     * @return значение ключа.
     */
    public String getStopKey() {
        return stopKey_;
    }

    /**
     * @return таймаут, секунды.
     */
    public Long getStopTimeout() {
        return stopTimeout_;
    }

}
