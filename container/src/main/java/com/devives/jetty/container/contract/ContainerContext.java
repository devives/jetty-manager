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
         */
        public void setServerEventListener(ServerEventListener handler) {
            ClassLoaderUtils.callProcInContextClassLoader(class_.getClassLoader(), () -> {
                Class<?> classLifeCycleListener = class_.getClassLoader().loadClass("com.devives.jetty.container.context.ServerEventListener");
                Method method = class_.getMethod("setServerEventListener", classLifeCycleListener);
                Object oHandler = handler != null
                        ? ProxyBuilder.forClasses(classLifeCycleListener).setTarget(handler).build()
                        : null;
                method.invoke(null, oHandler);
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
