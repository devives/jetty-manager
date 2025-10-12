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
