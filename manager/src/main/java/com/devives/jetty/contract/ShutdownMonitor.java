package com.devives.jetty.contract;

import com.devives.commons.lang.ClassLoaderUtils;
import com.devives.commons.lang.ExceptionUtils;
import com.devives.commons.lang.reflection.ProxyBuilder;

import java.util.Objects;

/**
 * @deprecated Replaced with {@link ShutdownService} component, which is not a singleton.
 */
@Deprecated(since = "12.1.0", forRemoval = true)
public interface ShutdownMonitor extends ShutdownService, Contract {

    String CLASS_NAME = "org.eclipse.jetty.server.ShutdownMonitor";

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

        public static Static at(ClassLoader classLoader) {
            return new Static(ExceptionUtils.passChecked(() -> classLoader.loadClass(CLASS_NAME)));
        }
    }

}
