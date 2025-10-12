package com.devives.jetty.container.contract;

import com.devives.commons.lang.ClassLoaderUtils;
import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.reflection.ProxyBuilder;

import java.util.Objects;

/**
 * @deprecated Replaced with {@link ShutdownService} component, which is not a singleton.
 */
@Contract(className = "org.eclipse.jetty.server.ShutdownMonitor")
public interface ShutdownMonitor extends ShutdownService {

    /**
     * Аксессор к статическим методом класса.
     */
    final class Static {

        private final Class<?> class_;

        private Static(Class<?> clazz) {
            class_ = Objects.requireNonNull(clazz, "clazz");
        }

        public ShutdownMonitor getInstance() {
            return ClassLoaderUtils.callFuncInContextClassLoader(class_.getClassLoader(), () -> {
                Object o = class_.getMethod("getInstance").invoke(null);
                return ProxyBuilder.forClasses(ShutdownMonitor.class).setTarget(o).build();
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
                return new Static(ExceptionUtils.passChecked(() -> classLoader.loadClass(ShutdownMonitor.class.getAnnotation(Contract.class).className())));
            });
        }
    }

}
