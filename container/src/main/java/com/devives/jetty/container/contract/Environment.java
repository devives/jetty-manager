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

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 */
@Contract(className = "org.eclipse.jetty.util.component.Environment")
public interface Environment {

    /**
     * @return The case-insensitive name of the environment.
     */
    String getName();

    /**
     * @return The {@link ClassLoader} for the environment or if none set, then the {@link ClassLoader} that
     * loaded the environment implementation.
     */
    ClassLoader getClassLoader();


    /**
     * Аксессор к статическим методом класса.
     */
    final class Static {
        private final Class<?> class_;

        private Static(Class<?> clazz) {
            class_ = Objects.requireNonNull(clazz, "clazz");
        }

        public Collection<Environment> getAll() {
            return ClassLoaderUtils.callFuncInContextClassLoader(class_.getClassLoader(), () -> {
                Collection<?> collection = (Collection<?>) class_.getMethod("getAll").invoke(null);
                return collection.stream()
                        .map(o -> ProxyBuilder.forClasses(Environment.class).setTarget(o).build())
                        .collect(Collectors.toList());
            });
        }

        public Environment get(String name) {
            return ClassLoaderUtils.callFuncInContextClassLoader(class_.getClassLoader(), () -> {
                Object object = class_.getMethod("get", String.class).invoke(null, name);
                return ProxyBuilder.forClasses(Environment.class).setTarget(object).build();
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
                return new Static(ExceptionUtils.passChecked(() -> classLoader.loadClass(Environment.class.getAnnotation(Contract.class).className())));
            });
        }
    }

}
