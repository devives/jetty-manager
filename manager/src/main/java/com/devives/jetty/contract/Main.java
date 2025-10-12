package com.devives.jetty.contract;

import com.devives.commons.lang.ClassLoaderUtils;
import com.devives.commons.lang.ExceptionUtils;

import java.util.Objects;

public interface Main extends Contract {

    String CLASS_NAME = "org.eclipse.jetty.start.Main";

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

        public static Static at(ClassLoader classLoader) {
            return new Static(ExceptionUtils.passChecked(() -> classLoader.loadClass(CLASS_NAME)));
        }
    }

}
