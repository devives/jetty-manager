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

import java.util.Objects;

@Contract(className = "org.eclipse.jetty.start.Main")
public interface Main extends Contract {

    /**
     * Аксессор к статическим методом класса.
     */
    final class Static {

        private final Class<?> class_;

        Static(Class<?> clazz) {
            class_ = Objects.requireNonNull(clazz, "clazz");
        }

        public void main(String[] args) {
            ClassLoaderUtils.callProcInContextClassLoader(class_.getClassLoader(), () -> {
                class_.getMethod("main", String[].class).invoke(null, (Object) args);
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
                return new Static(ExceptionUtils.passChecked(() -> classLoader.loadClass(Main.class.getAnnotation(Contract.class).className())));
            });
        }
    }

}
