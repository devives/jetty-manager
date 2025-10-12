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
