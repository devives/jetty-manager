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

import com.devives.commons.lang.ClassLoaderUtils;
import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.reflection.ProxyBuilder;

import java.lang.reflect.Method;
import java.util.Objects;

@Contract(className = "com.devives.jetty.container.context.ContainerContext")
public interface ContainerContext {

    /**
     * Аксессор к статическим методом класса.
     */
    final class Static {

        private final Class<?> class_;

        private Static(Class<?> clazz) {
            class_ = Objects.requireNonNull(clazz, "clazz");
        }
        /**
         * Устанавливает экземпляр слушателя событий жизненного цикла сервера Jetty для текущего потока.
         * <p>
         * Переменная устанавливается на время выполнения метода {@code org.eclipse.jetty.start.Main#main()}.
         * <p>
         * Запрашивается из конструктора {@code com.devives.jetty.container.injection.ServerEventListener}.
         *
         * @param listener слушатель событий
         */
        public void setServerEventListener(ServerEventListener listener) {
            ClassLoaderUtils.callProcInContextClassLoader(class_.getClassLoader(), () -> {
                Class<?> classLifeCycleListener = class_.getClassLoader().loadClass("com.devives.jetty.container.context.ServerEventListener");
                Method method = class_.getMethod("setServerEventListener", classLifeCycleListener);
                Object oListener = listener != null
                        ? ProxyBuilder.forClasses(classLifeCycleListener).setTarget(listener).build()
                        : null;
                method.invoke(null, oListener);
            });
        }

        /**
         * Создаёт аксессор к статически методам класса.
         *
         * @param classLoader загрузчик классов.
         * @return Аксессор.
         */
        public static Static at(ClassLoader classLoader) {
            return ClassLoaderUtils.callFuncInContextClassLoader(classLoader, () -> {
                return new Static(ExceptionUtils.passChecked(() -> classLoader.loadClass(ContainerContext.class.getAnnotation(Contract.class).className())));
            });
        }
    }
}
