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

import java.nio.file.Path;

/**
 * Перечисление указывает на версию используемых инъекций.
 */
public enum Version {
    /**
     * Автоматическое определение версии, из переданного в конструктор {@link ContainerBuilder#ContainerBuilder(ClassLoader, Path)}
     * домашнего каталога Jetty.
     */
    Auto(0),
    /**
     * Инъекции для Jetty 9.
     */
    Jetty9(9),
    /**
     * Инъекции для Jetty 12.
     */
    Jetty12(12);

    private final int version;

    Version(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }
}
